// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.assume;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

public class AssumeInfo {

  private static final AssumeInfo EMPTY =
      new AssumeInfo(DynamicType.unknown(), AbstractValue.unknown(), false, Collections.emptySet());

  private final DynamicType assumeType;
  private final AbstractValue assumeValue;
  private final boolean isSideEffectFree;
  private final Set<ProguardConfigurationRule> origins;

  private AssumeInfo(
      DynamicType assumeType,
      AbstractValue assumeValue,
      boolean isSideEffectFree,
      Set<ProguardConfigurationRule> origins) {
    this.assumeType = assumeType;
    this.assumeValue = assumeValue;
    this.isSideEffectFree = isSideEffectFree;
    this.origins = origins;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static AssumeInfo create(
      DynamicType assumeType,
      AbstractValue assumeValue,
      boolean isSideEffectFree,
      Set<ProguardConfigurationRule> origins) {
    return assumeType.isUnknown() && assumeValue.isUnknown() && !isSideEffectFree
        ? empty()
        : new AssumeInfo(assumeType, assumeValue, isSideEffectFree, origins);
  }

  public static AssumeInfo empty() {
    return EMPTY;
  }

  public Nullability getAssumeNullability() {
    return getAssumeType().getNullability();
  }

  public DynamicType getAssumeType() {
    return assumeType;
  }

  public AbstractValue getAssumeValue() {
    return assumeValue;
  }

  public Set<ProguardConfigurationRule> getOrigins() {
    return origins;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isEmpty() {
    if (this == empty()) {
      return true;
    }
    assert !assumeType.isUnknown() || !assumeValue.isUnknown() || isSideEffectFree;
    return false;
  }

  public boolean isSideEffectFree() {
    return isSideEffectFree;
  }

  public AssumeInfo meet(AssumeInfo other) {
    DynamicType meetType = internalMeetType(assumeType, other.assumeType);
    AbstractValue meetValue = internalMeetValue(assumeValue, other.assumeValue);
    boolean meetIsSideEffectFree =
        internalMeetIsSideEffectFree(isSideEffectFree, other.isSideEffectFree);
    return AssumeInfo.create(
        meetType, meetValue, meetIsSideEffectFree, Sets.union(origins, other.origins));
  }

  private static DynamicType internalMeetType(DynamicType type, DynamicType other) {
    if (type.equals(other)) {
      return type;
    }
    if (type.isUnknown()) {
      return other;
    }
    if (other.isUnknown()) {
      return type;
    }
    return DynamicType.unknown();
  }

  private static AbstractValue internalMeetValue(AbstractValue value, AbstractValue other) {
    if (value.equals(other)) {
      return value;
    }
    if (value.isUnknown()) {
      return other;
    }
    if (other.isUnknown()) {
      return value;
    }
    return AbstractValue.bottom();
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean internalMeetIsSideEffectFree(
      boolean isSideEffectFree, boolean otherIsSideEffectFree) {
    return isSideEffectFree || otherIsSideEffectFree;
  }

  @SuppressWarnings("ReferenceEquality")
  public AssumeInfo rewrittenWithLens(AppView<?> appView, GraphLens graphLens) {
    // Verify that there is no need to rewrite the assumed type.
    assert assumeType.isNotNullType() || assumeType.isUnknown();
    // If the assumed value is a static field, then rewrite it.
    if (assumeValue.isSingleFieldValue()) {
      DexField field = assumeValue.asSingleFieldValue().getField();
      DexField rewrittenField = graphLens.getRenamedFieldSignature(field);
      if (rewrittenField != field) {
        SingleFieldValue rewrittenAssumeValue =
            appView.abstractValueFactory().createSingleStatelessFieldValue(rewrittenField);
        return create(assumeType, rewrittenAssumeValue, isSideEffectFree, origins);
      }
    }
    return this;
  }

  public AssumeInfo withoutPrunedItems(PrunedItems prunedItems) {
    // Verify that there is no need to prune the assumed type.
    assert assumeType.isNotNullType() || assumeType.isUnknown();
    // If the assumed value is a static field, and the static field is removed, then prune the
    // assumed value.
    if (assumeValue.isSingleFieldValue()
        && prunedItems.isRemoved(assumeValue.asSingleFieldValue().getField())) {
      return create(assumeType, AbstractValue.unknown(), isSideEffectFree, origins);
    }
    return this;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    AssumeInfo assumeInfo = (AssumeInfo) other;
    return assumeValue.equals(assumeInfo.assumeValue)
        && assumeType.equals(assumeInfo.assumeType)
        && isSideEffectFree == assumeInfo.isSideEffectFree;
  }

  @Override
  public int hashCode() {
    return Objects.hash(assumeValue, assumeType, isSideEffectFree);
  }

  public static class Builder {

    private DynamicType assumeType = DynamicType.unknown();
    private AbstractValue assumeValue = AbstractValue.unknown();
    private boolean isSideEffectFree = false;
    private Set<ProguardConfigurationRule> origins = Collections.emptySet();

    public Builder addOrigin(ProguardConfigurationRule origin) {
      if (origins.isEmpty()) {
        origins = Sets.newIdentityHashSet();
      }
      origins.add(origin);
      return this;
    }

    public Builder meet(Builder builder) {
      return meetAssumeType(builder.assumeType)
          .meetAssumeValue(builder.assumeValue)
          .meetIsSideEffectFree(builder.isSideEffectFree);
    }

    public Builder meetAssumeType(DynamicType assumeType) {
      this.assumeType = internalMeetType(this.assumeType, assumeType);
      return this;
    }

    public Builder meetAssumeValue(AbstractValue assumeValue) {
      this.assumeValue = internalMeetValue(this.assumeValue, assumeValue);
      return this;
    }

    public Builder meetIsSideEffectFree(boolean isSideEffectFree) {
      this.isSideEffectFree = internalMeetIsSideEffectFree(this.isSideEffectFree, isSideEffectFree);
      return this;
    }

    public Builder setIsSideEffectFree() {
      this.isSideEffectFree = true;
      return this;
    }

    public AssumeInfo build() {
      return create(assumeType, assumeValue, isSideEffectFree, origins);
    }

    public boolean isEqualTo(Builder builder) {
      return assumeValue.equals(builder.assumeValue)
          && assumeType.equals(builder.assumeType)
          && isSideEffectFree == builder.isSideEffectFree;
    }
  }
}
