// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexItemFactory.StringBuildingMethods;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeInstructionListIterator;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StringConcat;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts StringConcat -> StringBuilder chains.
 *
 * <p>For CF output, converts to InvokeCustom.
 *
 * <p>TODO(246658291): Maybe introduce a CfStringConcat via desugaring to avoid the CfInvokeDynamic
 * special-casing in UnrepresentableInDexInstructionRemover and CfToCfInterfaceMethodRewriter.
 */
public class StringConcatRemover extends CodeRewriterPass {

  public StringConcatRemover(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "StringConcatRemover";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveStringConcat();
  }

  @Override
  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    boolean changed = false;
    IRCodeInstructionListIterator iterator = code.instructionListIterator();
    AffectedValues affectedValues = new AffectedValues();
    boolean canUseInvokeCustom =
        appView
            .options()
            .canUseInvokeCustomWithIndyStringConcat(
                code.context().getDefinition().getClassFileVersion());
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      StringConcat stringConcat = current.asStringConcat();
      if (stringConcat == null) {
        continue;
      }
      changed = true;

      DexType[] argTypes = stringConcat.getArgTypes();
      List<Value> argValues = stringConcat.inValues();

      // StringConcatCreator should not create zero-arg instructions.
      assert argTypes.length != 0;

      if (argTypes.length == 1) {
        Value argValue = argValues.get(0);
        TypeElement argTypeElement = argValue.getType();
        if (argTypeElement.isReferenceType() && argTypeElement.isDefinitelyNull()) {
          if (stringConcat.hasOutValue()) {
            // One null argument -> "null".
            iterator.replaceCurrentInstruction(
                new ConstString(stringConcat.outValue(), dexItemFactory.nullString));
          } else {
            iterator.removeOrReplaceByDebugLocalRead();
          }
        } else if (argTypeElement.isClassType(dexItemFactory.stringType)
            && argTypeElement.isDefinitelyNotNull()) {
          // One string literal argument -> noop.
          if (stringConcat.outValue() != null) {
            stringConcat.outValue().replaceUsers(argValues.get(0));
          }
          iterator.removeOrReplaceByDebugLocalRead();
        } else {
          // The value type does not distinguish boolean / char from int.
          DexType argType = argTypes[0];
          // One argument, String.valueOf().
          DexMethod valueOfMethod = dexItemFactory.stringMembers.getValueOfForDexType(argType);
          replaceWithInvoke(
              code,
              iterator,
              stringConcat,
              new InvokeStatic(valueOfMethod, null, argValues),
              affectedValues);
        }
        continue;
      } else if (canUseConcatMethod(argValues)) {
        // Two non-null strings: String.concat()
        replaceWithInvoke(
            code,
            iterator,
            stringConcat,
            new InvokeVirtual(dexItemFactory.stringMembers.concat, null, argValues),
            affectedValues);
        continue;
      }

      if (canUseInvokeCustom) {
        replaceWithInvokeCustom(code, iterator, stringConcat, affectedValues);
      } else {
        replaceWithDirectStringBuilderChain(code, iterator, affectedValues);
      }
    }
    affectedValues.widening(appView, code);
    return CodeRewriterResult.hasChanged(changed);
  }

  private void replaceWithInvokeCustom(
      IRCode code,
      IRCodeInstructionListIterator iterator,
      StringConcat stringConcat,
      AffectedValues affectedValues) {
    List<Value> inValues = stringConcat.inValues();
    List<Value> dynamicValues = new ArrayList<>();
    List<DexType> dynamicTypes = new ArrayList<>();
    DexType[] argTypes = stringConcat.getArgTypes();

    StringBuilder recipe = new StringBuilder();
    boolean hasConstant = false;
    for (int i = 0; i < inValues.size(); i++) {
      Value value = inValues.get(i);
      Value aliasedValue = value.getAliasedValue();
      if (aliasedValue.isConstString()) {
        hasConstant = true;
        recipe.append(aliasedValue.getDefinition().asConstString().getValue().toString());
      } else {
        recipe.append('\u0001');
        dynamicValues.add(value);
        dynamicTypes.add(argTypes[i]);
      }
    }

    List<DexValue> bootstrapArgs = new ArrayList<>();
    DexMethod bootstrapMethod;
    if (hasConstant) {
      bootstrapArgs.add(
          new DexValue.DexValueString(dexItemFactory.createString(recipe.toString())));
      bootstrapMethod = dexItemFactory.stringConcatFactoryMembers.makeConcatWithConstants;
    } else {
      bootstrapMethod = dexItemFactory.stringConcatFactoryMembers.makeConcat;
    }

    DexMethodHandle bootstrapMethodHandle =
        dexItemFactory.createMethodHandle(MethodHandleType.INVOKE_STATIC, bootstrapMethod, false);

    DexProto proto =
        dexItemFactory.createProto(
            dexItemFactory.stringType, dynamicTypes.toArray(DexType.EMPTY_ARRAY));
    DexCallSite callSite =
        dexItemFactory.createCallSite(
            dexItemFactory.concatMethodName, proto, bootstrapMethodHandle, bootstrapArgs);

    InvokeCustom invoke = new InvokeCustom(callSite, null, dynamicValues);
    replaceWithInvoke(code, iterator, stringConcat, invoke, affectedValues);
  }

  private boolean canUseConcatMethod(List<Value> argValues) {
    if (argValues.size() != 2) {
      return false;
    }
    Value argValue0 = argValues.get(0);
    TypeElement argTypeElement0 = argValue0.getType();
    Value argValue1 = argValues.get(1);
    TypeElement argTypeElement1 = argValue1.getType();
    return argTypeElement0.isClassType(dexItemFactory.stringType)
        && argTypeElement1.isClassType(dexItemFactory.stringType)
        && argTypeElement0.isDefinitelyNotNull()
        && argTypeElement1.isDefinitelyNotNull();
  }

  private void replaceWithDirectStringBuilderChain(
      IRCode code, IRCodeInstructionListIterator iterator, AffectedValues affectedValues) {
    // TODO(b/246658291): Move the .append() calls to directly after their input values (instead of
    // all at the end) in order to minimize the number of required registers.
    StringConcat stringConcat = iterator.previous().asStringConcat();
    assert stringConcat != null : iterator.peekNext();
    List<Value> inValues = stringConcat.inValues();
    DexType[] argTypes = stringConcat.getArgTypes();
    List<Instruction> newInstructions = new ArrayList<>(2 + argTypes.length);

    Value stringBuilderValue =
        code.createValue(
            TypeElement.fromDexType(
                dexItemFactory.stringBuilderType, definitelyNotNull(), appView));
    NewInstance newInstance = new NewInstance(dexItemFactory.stringBuilderType, stringBuilderValue);
    newInstance.setPosition(stringConcat.getPosition());
    newInstructions.add(newInstance);

    StringBuildingMethods stringBuilderMethods = dexItemFactory.stringBuilderMethods;
    Value firstArg = inValues.get(0);
    InvokeDirect initInstruction;
    int firstAppendIndex;
    if (firstArg.getType().isStringType(dexItemFactory) && firstArg.isNeverNull()) {
      // Use StringBuilder.<init>(String) when the first value is a non-null string.
      initInstruction =
          new InvokeDirect(
              stringBuilderMethods.stringConstructor,
              null,
              List.of(stringBuilderValue, inValues.get(0)));
      firstAppendIndex = 1;
    } else {
      initInstruction =
          new InvokeDirect(
              stringBuilderMethods.defaultConstructor, null, List.of(stringBuilderValue));
      firstAppendIndex = 0;
    }
    initInstruction.setPosition(stringConcat.getPosition());
    newInstructions.add(initInstruction);

    for (int i = firstAppendIndex; i < inValues.size(); ++i) {
      Value arg = inValues.get(i);
      DexMethod appendMethod;
      if (arg.getType().isStringType(dexItemFactory)) {
        appendMethod = null;
        if (arg.isConstString()) {
          DexString value = arg.definition.asConstString().getValue();
          // Use null / true / false / int when possible, since they are smaller than const-string.
          if (value.isIdenticalTo(dexItemFactory.nullString)) {
            appendMethod = stringBuilderMethods.appendString;
            ConstNumber constIns = code.createConstNull();
            constIns.setPosition(arg.definition.getPosition());
            newInstructions.add(constIns);
            arg = constIns.outValue();
          } else if (value.isIdenticalTo(dexItemFactory.trueString)) {
            appendMethod = stringBuilderMethods.appendBoolean;
            ConstNumber constIns = code.createBooleanConstant(true);
            constIns.setPosition(arg.definition.getPosition());
            newInstructions.add(constIns);
            arg = constIns.outValue();
          } else if (value.isIdenticalTo(dexItemFactory.falseString)) {
            appendMethod = stringBuilderMethods.appendBoolean;
            ConstNumber constIns = code.createBooleanConstant(false);
            constIns.setPosition(arg.definition.getPosition());
            newInstructions.add(constIns);
            arg = constIns.outValue();
          } else if (value.length() < 10) { // Fits in 32-bit int.
            int firstNonDigit = value.getNumberSuffixStartIndex();
            if (firstNonDigit == 0 || (firstNonDigit == 1 && value.getFirstByteAsChar() == '-')) {
              // Encode as a number to save space.
              appendMethod = stringBuilderMethods.appendInt;
              ConstNumber constIns = code.createIntConstant(Integer.valueOf(value.toASCIIString()));
              constIns.setPosition(arg.definition.getPosition());
              newInstructions.add(constIns);
              arg = constIns.outValue();
            } else if (value.length() == 1) {
              appendMethod = stringBuilderMethods.appendChar;
              ConstNumber constIns = code.createIntConstant(value.toString().charAt(0));
              constIns.setPosition(arg.definition.getPosition());
              newInstructions.add(constIns);
              arg = constIns.outValue();
            }
          }
        }
        if (appendMethod == null) {
          appendMethod = stringBuilderMethods.appendString;
        }
      } else {
        DexType argType = argTypes[i];
        appendMethod = stringBuilderMethods.getAppendMethodForType(argType);
      }
      InvokeVirtual invokeVirtual =
          new InvokeVirtual(appendMethod, null, List.of(stringBuilderValue, arg));
      invokeVirtual.setPosition(stringConcat.getPosition());
      newInstructions.add(invokeVirtual);
    }

    iterator.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(newInstructions, options);
    iterator.next();

    InvokeVirtual toStringInvoke =
        new InvokeVirtual(stringBuilderMethods.toString, null, List.of(stringBuilderValue));
    replaceWithInvoke(code, iterator, stringConcat, toStringInvoke, affectedValues);
  }

  private void replaceWithInvoke(
      IRCode code,
      IRCodeInstructionListIterator iterator,
      StringConcat stringConcat,
      Invoke invoke,
      AffectedValues affectedValues) {
    // Invokes are always nullable, so cannot use the non-null StringConcat out value.
    if (stringConcat.hasUsedOutValue()) {
      invoke.setOutValue(
          code.createValue(TypeElement.stringClassType(appView), stringConcat.getLocalInfo()));
    }
    iterator.replaceCurrentInstruction(invoke, affectedValues);
  }
}
