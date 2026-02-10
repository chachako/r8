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
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Models string concatenation, mainly from invoke-dynamic instructions.
 *
 * <p>Removed before finalization by StringConcatRemover.
 */
public class StringConcat extends Instruction {

  // Types are kept because Values do not distinguish boolean & char from int.
  // The types are normalized (e.g. Object for all reference types) to optimize outlining.
  private DexType[] argTypes;
  // One entry for each arg type. Null entries mean use an inValue.
  // It's invalid for two constants to be adjacent (they should have been merged in this case).
  private List<DexString> argConstants;

  private StringConcat(
      Value outValue, List<Value> inValues, DexType[] argTypes, List<DexString> argConstants) {
    super(outValue, inValues);
    this.argTypes = argTypes;
    this.argConstants = argConstants;
    assertValidState();
  }

  public static StringConcat create(
      Value outValue,
      List<Value> inValues,
      DexType[] argTypes,
      List<DexString> argConstants,
      AppView<?> appView) {
    return new StringConcat(
        outValue, inValues, normalizeArgTypes(argTypes, appView.dexItemFactory()), argConstants);
  }

  public static StringConcat createNormalized(
      AppView<?> appView,
      Value outValue,
      DexType[] argTypes,
      List<DexString> argConstants,
      List<Value> inValues) {
    assert Arrays.equals(argTypes, normalizeArgTypes(argTypes, appView.dexItemFactory()));
    return new StringConcat(outValue, inValues, argTypes, argConstants);
  }

  private static DexType[] normalizeArgTypes(DexType[] argTypes, DexItemFactory dexItemFactory) {
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
    return syntheticArgTypes;
  }

  private void assertValidState() {
    assert outValue == null || outValue.getType().isDefinitelyNotNull();
    assert argTypes.length > 0;
    assert argTypes.length == argConstants.size();
    assert argConstants.stream().filter(Objects::nonNull).count() + inValues.size()
        == argTypes.length;
    assert !hasAdjacentConstants();
  }

  private boolean hasAdjacentConstants() {
    DexString prev = null;
    for (DexString str : argConstants) {
      assert str == null || prev == null : "Adjacent Constants: " + str + ", " + prev;
      prev = str;
    }
    return false;
  }

  /** Sets argTypes, argConstants, and inValues. Does not update any value users. */
  public void setArguments(
      DexType[] newArgTypes, List<DexString> newArgConstants, List<Value> newInValues) {
    inValues.clear();
    inValues.addAll(newInValues);
    argTypes = newArgTypes;
    argConstants = newArgConstants;
    assertValidState();
  }

  public DexType[] getArgTypes() {
    return argTypes;
  }

  public List<DexString> getArgConstants() {
    return argConstants;
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
    builder.addStringConcat(argTypes, argConstants, inValues);
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
