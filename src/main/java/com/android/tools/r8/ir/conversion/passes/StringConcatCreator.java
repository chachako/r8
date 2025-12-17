// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeInstructionListIterator;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.StringConcat;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.ArrayList;
import java.util.List;

/** Converts InvokeCustom -> StringConcat. */
public class StringConcatCreator extends CodeRewriterPass<AppInfo> {

  public StringConcatCreator(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "StringConcatCreator";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveInvokeCustom();
  }

  @Override
  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    DexMethod makeConcat = dexItemFactory.stringConcatFactoryMembers.makeConcat;
    DexMethod makeConcatWithConstants =
        dexItemFactory.stringConcatFactoryMembers.makeConcatWithConstants;
    AffectedValues affectedValues = new AffectedValues();
    IRCodeInstructionListIterator iterator = code.instructionListIterator();
    boolean changed = false;
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      InvokeCustom invokeCustom = current.asInvokeCustom();
      if (invokeCustom == null) {
        continue;
      }
      DexCallSite callSite = invokeCustom.getCallSite();
      DexMethod method = callSite.bootstrapMethod.asMethod();

      Instruction newInstruction;
      if (method.isIdenticalTo(makeConcat)) {
        newInstruction = buildStringConcat(code, invokeCustom, callSite);
      } else if (method.isIdenticalTo(makeConcatWithConstants)) {
        newInstruction = buildStringConcatWithConstants(iterator, invokeCustom, callSite);
      } else {
        continue;
      }
      if (newInstruction == null && invokeCustom.hasOutValue()) {
        // String concat with no args.
        newInstruction =
            new ConstString(createStringConcatOutValue(code), dexItemFactory.emptyString);
      }
      changed = true;
      if (newInstruction == null) {
        // String concat with no args and no outValue. Should not happen.
        iterator.removeOrReplaceByDebugLocalRead();
      } else {
        iterator.replaceCurrentInstruction(newInstruction, affectedValues);
      }
    }
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    return CodeRewriterResult.hasChanged(changed);
  }

  private Value createStringConcatOutValue(IRCode code) {
    Value outValue =
        code.createValue(TypeElement.stringClassType(appView, Nullability.definitelyNotNull()));
    return outValue;
  }

  private StringConcat buildStringConcat(
      IRCode code, InvokeCustom invokeCustom, DexCallSite callSite) {
    DexType[] argTypes = callSite.methodProto.parameters.values;
    if (argTypes.length == 0) {
      // Can only happen with custom bytecode generators.
      return null;
    }
    List<Value> argValues = invokeCustom.inValues();
    Value outValue = createStringConcatOutValue(code);
    return StringConcat.create(appView, outValue, argTypes, argValues);
  }

  private StringConcat buildStringConcatWithConstants(
      IRCodeInstructionListIterator iterator, InvokeCustom invokeCustom, DexCallSite callSite) {
    DexType[] argValueTypes = callSite.methodProto.parameters.values;
    List<Value> inValues = invokeCustom.inValues();
    List<Value> argValues = new ArrayList<>(argValueTypes.length);

    List<DexType> argTypes = new ArrayList<>();
    List<DexValue> bootstrapArgs = callSite.bootstrapArgs;
    String recipe = bootstrapArgs.get(0).asDexValueString().getValue().toString();
    int argTypesIdx = 0;
    int inValuesIdx = 0;
    int bootstrapArgsIdx = 1;

    StringBuilder acc = new StringBuilder();
    for (int i = 0; i < recipe.length(); i++) {
      char c = recipe.charAt(i);
      if (c == '\u0001') {
        if (acc.length() > 0) {
          argValues.add(
              StringConcat.addConstStringBeforeCurrent(
                  appView, iterator, dexItemFactory.createString(acc.toString())));
          argTypes.add(dexItemFactory.objectType);
          acc.setLength(0);
        }
        argTypes.add(argValueTypes[argTypesIdx++]);
        argValues.add(inValues.get(inValuesIdx++));

      } else if (c == '\u0002') {
        DexValue constValue = bootstrapArgs.get(bootstrapArgsIdx++);
        if (constValue.isDexValueString()) {
          acc.append(constValue.asDexValueString().getValue().toString());
        } else if (constValue.isDexValueInt()) {
          acc.append(constValue.asDexValueInt().getValue());
        } else if (constValue.isDexValueLong()) {
          acc.append(constValue.asDexValueLong().getValue());
        } else if (constValue.isDexValueFloat()) {
          acc.append(constValue.asDexValueFloat().getValue());
        } else if (constValue.isDexValueDouble()) {
          acc.append(constValue.asDexValueDouble().getValue());
        } else {
          throw new Unreachable("Unsupported constant type: " + constValue);
        }
      } else {
        acc.append(c);
      }
    }
    if (acc.length() > 0) {
      argValues.add(
          StringConcat.addConstStringBeforeCurrent(
              appView, iterator, dexItemFactory.createString(acc.toString())));
      argTypes.add(dexItemFactory.objectType);
    }
    assert inValuesIdx == inValues.size();

    // Can only happen with custom bytecode generators.
    if (argValues.isEmpty()) {
      return null;
    }

    Value outValue = createStringConcatOutValue(iterator.getCode());
    return StringConcat.create(appView, outValue, argTypes.toArray(DexType.EMPTY_ARRAY), argValues);
  }
}
