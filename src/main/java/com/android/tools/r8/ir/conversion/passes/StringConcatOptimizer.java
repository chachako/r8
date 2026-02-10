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

    List<DexString> newArgConstants = new ArrayList<>();
    List<DexType> newArgTypes = new ArrayList<>();
    List<Value> newInValues = new ArrayList<>();

    int inValueIdx = 0;
    for (int argIdx = 0; argIdx < argTypes.length; ++argIdx) {
      DexString curString = argConstants.get(argIdx);
      if (curString != null) {
        newArgConstants.add(curString);
        newArgTypes.add(argTypes[argIdx]);
        continue;
      }

      Value value = inValues.get(inValueIdx++);
      Value aliasedValue = value.getAliasedValue();
      boolean safeToMerge =
          aliasedValue.isDefinedByInstructionSatisfying(Instruction::isStringConcat)
              && value.hasSingleUniqueUserAndNoOtherUsers()
              && aliasedValue.hasSingleUniqueUserAndNoOtherUsers();
      StringConcat otherStringConcat = null;
      if (safeToMerge) {
        otherStringConcat = aliasedValue.getDefinition().asStringConcat();
        // TODO(467374229): We could also allow this if we verify that the order of the toString()
        // calls does not change when merged.
        safeToMerge = !otherStringConcat.mightCallToStringWithSideEffects(appView);
      }
      if (!safeToMerge) {
        newArgConstants.add(null);
        newInValues.add(value);
        newArgTypes.add(argTypes[argIdx]);
        continue;
      }
      value.clearUsers();

      Collections.addAll(newArgTypes, otherStringConcat.getArgTypes());
      newArgConstants.addAll(otherStringConcat.getArgConstants());
      newInValues.addAll(otherStringConcat.inValues());
      for (Value otherValue : otherStringConcat.inValues()) {
        otherValue.removeUser(otherStringConcat);
        otherValue.addUser(stringConcat);
      }
      otherStringConcat.removeOrReplaceByDebugLocalRead();
    }

    stringConcat.setArguments(
        newArgTypes.toArray(DexType.EMPTY_ARRAY), newArgConstants, newInValues);
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

    List<DexString> newArgConstants = new ArrayList<>();
    List<DexType> newArgTypes = new ArrayList<>();
    List<Value> newInValues = new ArrayList<>();

    DexString prevString = null;
    Value prevValue = null;
    int inValueIdx = 0;
    // Loop an extra time to not have to duplicate the "insert previous value" logic after the loop.
    for (int argIdx = 0; argIdx <= argTypes.length; ++argIdx) {
      DexString curString = null;
      Value curValue = null;
      if (argIdx < argTypes.length) {
        curString = argConstants.get(argIdx);
        if (curString == null) {
          curValue = inValues.get(inValueIdx++);
          Value aliasedValue = curValue.getAliasedValue();
          DexType argType = argTypes[argIdx];

          // We re-add the user at the end.
          curValue.removeUser(stringConcat);
          if (!aliasedValue.isPhi()) {
            Instruction instruction = aliasedValue.getDefinition();
            // Translate foo.toString() -> foo.
            // Check for non-null since null.toString() throws, but "" + null == "null".
            if (isToStringInvoke(instruction)) {
              Value receiver = instruction.getFirstOperand();
              // TODO(467374229): We could also allow this if we verify that the order of the
              //     toString() call does not change.
              if (receiver.isNeverNull()
                  && !StringConcat.toStringMayHaveSideEffects(dexItemFactory, receiver.getType())) {
                Value newValue = instruction.getFirstOperand();
                if (!curValue.hasAnyUsers()) {
                  // Remove the alias instruction if present.
                  if (aliasedValue != curValue
                      && aliasedValue.hasSingleUniqueUserAndNoOtherUsers()) {
                    curValue.definition.remove();
                  }

                  // Remove the toString() instruction.
                  instruction.remove();
                }
                argType = dexItemFactory.objectType;
                curValue = newValue;
                aliasedValue = curValue.getAliasedValue();
                instruction = aliasedValue.getDefinition();
              }
            }
            // Translate {String,Integer,etc}.valueOf(thing) -> thing.
            DexMethod matchedMethod = detectValueOf(instruction);
            if (matchedMethod != null) {
              Value newValue = instruction.getFirstOperand();
              if (!curValue.hasAnyUsers()) {
                // Remove the alias instruction if present.
                if (aliasedValue != curValue && aliasedValue.hasSingleUniqueUserAndNoOtherUsers()) {
                  curValue.definition.remove();
                  instruction.remove();
                }
              }
              argType = matchedMethod.getProto().getParameter(0);
              curValue = newValue;
              aliasedValue = curValue.getAliasedValue();
            }

            if (aliasedValue.isConstString() || aliasedValue.isConstNumber()) {
              curString = convertConstToString(aliasedValue, argType);
            }
          }
        }
      } // argIdx < numArgs
      if (argIdx > 0) {
        if (prevString != null && curString != null) {
          // Merge adjacent strings.
          curString = curString.prepend(prevString, appView.dexItemFactory());
        } else if (prevString != null) {
          newArgTypes.add(appView.dexItemFactory().objectType);
          newArgConstants.add(prevString);
        } else {
          assert prevValue != null;
          // There was a pending value to add.
          newArgTypes.add(argTypes[argIdx - 1]);
          newArgConstants.add(null);
          newInValues.add(prevValue);
        }
      }
      prevString = curString;
      prevValue = curValue;
    }

    if (newInValues.isEmpty()) {
      DexString value;
      if (!newArgConstants.isEmpty()) {
        assert newArgConstants.size() == 1;
        value = newArgConstants.get(0);
      } else {
        value = dexItemFactory.emptyString;
      }
      ConstString constString =
          ConstString.builder().setOutValue(stringConcat.outValue()).setValue(value).build();
      iterator.replaceCurrentInstruction(constString);
    } else {
      for (Value v : newInValues) {
        v.addUser(stringConcat);
      }
      stringConcat.setArguments(
          newArgTypes.toArray(DexType.EMPTY_ARRAY), newArgConstants, newInValues);
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
    stringConcat.setArguments(newArgTypes, newArgConstants, newInValues);
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
}
