// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeInstructionListIterator;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.StringConcat;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.ValueUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Optimize StringConcat instructions:
 *
 * <ul>
 *   <li>Combine adjacent constants
 *   <li>Unbox boxed args
 *   <li>Remove explicit .toString() on arguments
 *   <li>Remove side-effect-free arguments when out value is unused
 *   <li>Merge nested ones into parent ones
 * </ul>
 */
public class StringConcatOptimizer extends CodeRewriterPass<AppInfo> {

  public StringConcatOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "StringConcatOptimizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveStringConcat();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    IRCodeInstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      StringConcat stringConcat = current.asStringConcat();
      if (stringConcat != null) {
        if (mergeStringConcats(stringConcat)) {
          hasChanged = true;
        }
        if (!stringConcat.hasUsedOutValue()) {
          // TODO: Might be worth an option to assume all toString() overloads are side-effect free.
          // TODO: Maybe should replace with directly toString() calls rather than concatenating
          // (concatenation is likely smaller size once outlined).
          removeAllSideEffectFreeInValues(stringConcat, dexItemFactory);
          hasChanged = true;
        } else if (simplifyArgs(stringConcat, iterator)) {
          hasChanged = true;
        }
      }
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  private boolean hasMergableStringConcats(StringConcat stringConcat) {
    for (Value value : stringConcat.inValues()) {
      if (value.getAliasedValue().isDefinedByInstructionSatisfying(Instruction::isStringConcat)
          && value.hasSingleUniqueUserAndNoOtherUsers()) {
        return true;
      }
    }
    return false;
  }

  /**
   * If any inValue is from a StringConcat, merges it into this instance.
   *
   * @return Whether any instructions were merged.
   */
  public boolean mergeStringConcats(StringConcat stringConcat) {
    if (!hasMergableStringConcats(stringConcat)) {
      return false;
    }

    DexType[] argTypes = stringConcat.getArgTypes();
    List<DexString> argConstants = stringConcat.getArgConstants();
    List<Value> inValues = stringConcat.inValues();

    StringConcatBuilder builder = new StringConcatBuilder();

    int inValueIdx = 0;
    for (int argIdx = 0; argIdx < argTypes.length; ++argIdx) {
      DexString curString = argConstants.get(argIdx);
      if (curString != null) {
        builder.addConstant(curString);
        continue;
      }

      Value value = inValues.get(inValueIdx++);
      boolean safeToMerge =
          value.isDefinedByInstructionSatisfying(Instruction::isStringConcat)
              && value.hasSingleUniqueUserAndNoOtherUsers();

      StringConcat otherStringConcat = null;
      if (safeToMerge) {
        // TODO(467374229): We could also allow this if we verify that the order of the toString()
        // calls does not change when merged.
        otherStringConcat = value.getDefinition().asStringConcat();
        safeToMerge = !otherStringConcat.mightCallToStringWithSideEffects(appView);
      }

      if (!safeToMerge) {
        builder.addValue(value, argTypes[argIdx]);
        continue;
      }
      value.clearUsers();
      DexType[] otherTypes = otherStringConcat.getArgTypes();
      List<DexString> otherConstants = otherStringConcat.getArgConstants();
      List<Value> otherInValues = otherStringConcat.inValues();

      int otherInValueIdx = 0;
      for (int i = 0; i < otherTypes.length; i++) {
        DexString otherStr = otherConstants.get(i);
        if (otherStr != null) {
          builder.addConstant(otherStr);
        } else {
          Value otherValue = otherInValues.get(otherInValueIdx++);
          otherValue.removeUser(otherStringConcat);
          otherValue.addUser(stringConcat);
          builder.addValue(otherValue, otherTypes[i]);
        }
      }
      otherStringConcat.removeOrReplaceByDebugLocalRead();
    }

    builder.apply(stringConcat);
    return true;
  }

  private boolean canSimplifyArgs(StringConcat stringConcat) {
    DexString valueOfMethodName = dexItemFactory.valueOfMethodName;
    for (Value value : stringConcat.inValues()) {
      value = value.getAliasedValue();
      if (value.isPhi()) {
        continue;
      }
      Instruction definition = value.getDefinition();
      if (definition.isConstString() || definition.isConstNumber()) {
        return true;
      }
      if (definition.isInvokeStatic()
          && definition
              .asInvokeStatic()
              .getInvokedMethod()
              .getName()
              .isIdenticalTo(valueOfMethodName)) {
        return true;
      }
      if (isToStringInvoke(definition) && value.hasSingleUniqueUserAndNoOtherUsers()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Converts numeric constants to strings and merges adjacent constants.
   *
   * @return Whether anything was changed.
   */
  public boolean simplifyArgs(StringConcat stringConcat, IRCodeInstructionListIterator iterator) {
    if (!canSimplifyArgs(stringConcat)) {
      return false;
    }

    DexType[] argTypes = stringConcat.getArgTypes();
    List<DexString> argConstants = stringConcat.getArgConstants();
    List<Value> inValues = stringConcat.inValues();

    StringConcatBuilder builder = new StringConcatBuilder();

    int inValueIdx = 0;
    for (int argIdx = 0; argIdx < argTypes.length; ++argIdx) {
      DexString curString = argConstants.get(argIdx);
      if (curString != null) {
        builder.addConstant(curString);
        continue;
      }

      Value value = inValues.get(inValueIdx++);
      Value aliasedValue = value.getAliasedValue();
      DexType argType = argTypes[argIdx];

      // We re-add the user at the end.
      value.removeUser(stringConcat);

      if (!aliasedValue.isPhi()) {
        Instruction instruction = aliasedValue.getDefinition();

        // Translate foo.toString() -> foo.
        // Check for non-null since null.toString() throws, but "" + null == "null".
        if (isToStringInvoke(instruction)) {
          Value receiver = instruction.getFirstOperand();
          // TODO(467374229): We could also allow this if we verify that the order of the
          //     toString() call does not change.
          Value aliasedReceiver = receiver.getAliasedValue();
          if (!aliasedReceiver.isPhi()
              && receiver.isNeverNull()
              && !StringConcat.toStringMayHaveSideEffects(dexItemFactory, receiver.getType())) {
            ValueUtils.removeAliasChain(value, aliasedValue);
            if (!aliasedValue.hasAnyUsers()) {
              // Remove the toString() instruction.
              instruction.removeOrReplaceByDebugLocalRead();
            }
            argType = dexItemFactory.objectType;
            value = receiver;
            aliasedValue = aliasedReceiver;
            instruction = aliasedReceiver.getDefinition();
          }
        }

        // Simplify {String,Integer,etc}.valueOf(thing) -> thing.
        DexMethod matchedMethod = detectValueOf(instruction);
        if (matchedMethod != null) {
          ValueUtils.removeAliasChain(value, aliasedValue);
          Value newValue = instruction.getFirstOperand();
          if (!aliasedValue.hasAnyUsers()) {
            instruction.removeOrReplaceByDebugLocalRead();
          }
          argType = matchedMethod.getProto().getParameter(0);
          value = newValue;
          aliasedValue = value.getAliasedValue();
        }

        // Convert constants to strings.
        if (aliasedValue.isConstString() || aliasedValue.isConstNumber()) {
          builder.addConstant(convertConstToString(aliasedValue, argType));
          continue;
        }
      }

      // If it wasn't converted to a constant, add as a Value.
      builder.addValue(value, argType);
    }

    if (builder.isSingleConstant()) {
      // Everything was simplified to a single constant or empty string.
      DexString value = builder.getSingleConstant();
      ConstString constString =
          ConstString.builder().setOutValue(stringConcat.outValue()).setValue(value).build();
      iterator.replaceCurrentInstruction(constString);
    } else {
      builder.apply(stringConcat);
      for (Value v : stringConcat.inValues()) {
        v.addUser(stringConcat);
      }
    }
    return true;
  }

  private DexMethod detectValueOf(Instruction instruction) {
    InvokeStatic invokeStatic = instruction.asInvokeStatic();
    if (invokeStatic == null) {
      return null;
    }
    DexMethod invokedMethod = invokeStatic.getInvokedMethod();
    if (!invokedMethod.getName().isIdenticalTo(dexItemFactory.valueOfMethodName)) {
      return null;
    }
    DexType argType = instruction.getFirstOperand().getType().toDexType(dexItemFactory);
    DexMethod stringValueOf = dexItemFactory.stringMembers.getValueOfForDexType(argType);
    if (invokedMethod.isIdenticalTo(stringValueOf)) {
      return invokedMethod;
    }
    DexMethod boxedValueOf = dexItemFactory.getBoxPrimitiveMethod(argType);
    if (boxedValueOf != null && invokedMethod.isIdenticalTo(boxedValueOf)) {
      return invokedMethod;
    }
    return null;
  }

  private boolean isToStringInvoke(Instruction instruction) {
    InvokeVirtual invokeVirtual = instruction.asInvokeVirtual();
    if (invokeVirtual != null) {
      DexMethod invokedMethod = invokeVirtual.getInvokedMethod();
      return invokedMethod.match(dexItemFactory.objectMembers.toString);
    }
    return false;
  }

  /** Used when StringConcat has no outValue. */
  private void removeAllSideEffectFreeInValues(
      StringConcat stringConcat, DexItemFactory dexItemFactory) {
    List<Value> inValues = stringConcat.inValues();
    List<Value> newInValues = new ArrayList<>(inValues.size());
    for (Value value : inValues) {
      if (StringConcat.toStringMayHaveSideEffects(dexItemFactory, value.getType())) {
        newInValues.add(value);
      } else {
        value.removeUser(stringConcat);
      }
    }
    if (newInValues.isEmpty()) {
      stringConcat.inValues().clear();
      stringConcat.removeOrReplaceByDebugLocalRead();
      return;
    }
    DexType[] newArgTypes = new DexType[newInValues.size()];
    Arrays.fill(newArgTypes, dexItemFactory.objectType);
    List<DexString> newArgConstants = Collections.nCopies(newArgTypes.length, null);
    stringConcat.setArguments(newArgTypes, newArgConstants, newInValues, appView);
  }

  private DexString convertConstToString(Value value, DexType argType) {
    Instruction def = value.getDefinition();
    ConstNumber asNumber = def.asConstNumber();
    switch (argType.descriptor.content[0]) {
      case '[':
      case 'L':
        if (asNumber != null) {
          return dexItemFactory.nullString;
        }
        return def.asConstString().getValue();
      case 'Z':
        return dexItemFactory.createString(String.valueOf(asNumber.getBooleanValue()));
      case 'C':
        return dexItemFactory.createString(String.valueOf((char) asNumber.getIntValue()));
      case 'F':
        return dexItemFactory.createString(String.valueOf(asNumber.getFloatValue()));
      case 'J':
        return dexItemFactory.createString(String.valueOf(asNumber.getLongValue()));
      case 'D':
        return dexItemFactory.createString(String.valueOf(asNumber.getDoubleValue()));
      case 'B':
      case 'S':
      case 'I':
        return dexItemFactory.createString(String.valueOf(asNumber.getIntValue()));
      default:
        throw new Unreachable();
    }
  }

  /** Takes care of merging adjacent constants. */
  private class StringConcatBuilder {
    private final List<DexString> newArgConstants = new ArrayList<>();
    private final List<DexType> newArgTypes = new ArrayList<>();
    private final List<Value> newInValues = new ArrayList<>();
    private DexString pendingString = null;

    void addConstant(DexString constant) {
      if (pendingString == null) {
        pendingString = constant;
      } else {
        pendingString = constant.prepend(pendingString, dexItemFactory);
      }
    }

    void addValue(Value value, DexType type) {
      flushPending();
      newArgConstants.add(null);
      newArgTypes.add(type);
      newInValues.add(value);
    }

    private void flushPending() {
      if (pendingString != null) {
        newArgConstants.add(pendingString);
        newArgTypes.add(dexItemFactory.objectType);
        pendingString = null;
      }
    }

    boolean isSingleConstant() {
      return newInValues.isEmpty();
    }

    DexString getSingleConstant() {
      // If we have a pending string that hasn't been flushed to lists yet.
      if (newArgConstants.isEmpty()) {
        return pendingString != null ? pendingString : dexItemFactory.emptyString;
      }
      // If it was flushed, return the single constant.
      assert newArgConstants.size() == 1;
      return newArgConstants.get(0);
    }

    void apply(StringConcat stringConcat) {
      flushPending();
      stringConcat.setArguments(
          newArgTypes.toArray(DexType.EMPTY_ARRAY), newArgConstants, newInValues, appView);
    }
  }
}
