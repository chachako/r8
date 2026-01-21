// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.List;

/**
 * Models string concatenation, mainly from invoke-dynamic instructions.
 *
 * <p>Removed before finalization by StringConcatRemover.
 */
public class StringConcat extends Instruction {

  // Types are kept because Values do not distinguish boolean & char from int.
  // The types are normalized (e.g. Object for all reference types) to optimize outlining.
  private DexTypeList argTypes;

  private StringConcat(Value outValue, DexTypeList argTypes, List<Value> inValues) {
    super(outValue, inValues);
    assert outValue == null || outValue.getType().isDefinitelyNotNull();
    this.argTypes = argTypes;
    assert argTypes.size() == inValues.size();
    assert argTypes.size() > 0;
  }

  public static StringConcat create(
      AppView<?> appView, Value outValue, DexType[] argTypes, List<Value> inValues) {
    return new StringConcat(
        outValue, normalizeArgTypes(argTypes, appView.dexItemFactory()), inValues);
  }

  public static StringConcat createNormalized(
      AppView<?> appView, Value outValue, DexTypeList argTypes, List<Value> inValues) {
    assert argTypes.equals(normalizeArgTypes(argTypes.getBacking(), appView.dexItemFactory()));
    return new StringConcat(outValue, argTypes, inValues);
  }

  private static DexTypeList normalizeArgTypes(DexType[] argTypes, DexItemFactory dexItemFactory) {
    DexType[] syntheticArgTypes = new DexType[argTypes.length];
    for (int i = 0; i < argTypes.length; ++i) {
      DexType argType = argTypes[i];
      switch (argType.toShorty()) {
        case 'L':
        case '[':
          argType = dexItemFactory.objectType;
          break;
        case 'B':
        case 'S':
        case 'I':
          argType = dexItemFactory.intType;
          break;
        default:
          break;
      }
      syntheticArgTypes[i] = argType;
    }
    return new DexTypeList(syntheticArgTypes);
  }

  public static Value addConstStringBeforeCurrent(
      AppView<?> appView, IRCodeInstructionListIterator iterator, DexString stringData) {
    Instruction curInstruction = iterator.previous();
    ConstString constString =
        ConstString.builder()
            .setFreshOutValue(appView, iterator.getCode())
            .setPosition(curInstruction)
            .setValue(stringData)
            .build();
    iterator.addPossiblyThrowingInstructionToPossiblyThrowingBlock(constString, appView.options());
    iterator.nextUntil(curInstruction);
    return constString.outValue();
  }

  /** Sets argTypes and inValues. Does not update any value users. */
  public void setArguments(DexTypeList newArgTypes, List<Value> newInValues) {
    assert newArgTypes.size() == newInValues.size();
    inValues.clear();
    inValues.addAll(newInValues);
    argTypes = newArgTypes;
  }

  public DexTypeList getArgTypeList() {
    return argTypes;
  }

  public DexType[] getArgTypes() {
    return argTypes.getBacking();
  }

  @Override
  public int opcode() {
    return Opcodes.STRING_CONCAT;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean isStringConcat() {
    return true;
  }

  @Override
  public StringConcat asStringConcat() {
    return this;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isStringConcat();
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.stringClassType(appView, Nullability.definitelyNotNull());
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public Inliner.ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return ConstraintWithTarget.ALWAYS;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return true;
  }

  @Override
  public boolean instructionInstanceCanThrow(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    return true;
  }

  public static boolean toStringMayHaveSideEffects(
      DexItemFactory dexItemFactory, TypeElement type) {
    // TODO(467374229): Inspect the actual toString() for side effects.
    return type.isClassType()
        && !type.isStringType(dexItemFactory)
        && dexItemFactory.getPrimitiveFromBoxed(type.toDexType(dexItemFactory)) == null;
  }

  public boolean mightCallToStringWithSideEffects(AppView<?> appView) {
    // TODO(agrieve): check if the toString method of each argType may have side effects.
    for (Value value : inValues) {
      if (toStringMayHaveSideEffects(appView.dexItemFactory(), value.getType())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean instructionMayHaveSideEffects(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    return mightCallToStringWithSideEffects(appView);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    // If an toString() methods are called, there could be side effects.
    return mightCallToStringWithSideEffects(appView);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addStringConcat(argTypes, inValues);
  }

  @Override
  public void insertLoadAndStores(LoadStoreHelper helper) {
    throw new Unreachable("StringConcat Should have been lowered.");
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable("StringConcat Should have been lowered.");
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable("StringConcat Should have been lowered.");
  }

  @Override
  public int maxInValueRegister() {
    throw new Unreachable("StringConcat Should have been lowered.");
  }

  @Override
  public int maxOutValueRegister() {
    throw new Unreachable("StringConcat Should have been lowered.");
  }
}
