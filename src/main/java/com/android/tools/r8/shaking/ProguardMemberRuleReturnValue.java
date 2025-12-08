// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.LongInterval;
import com.android.tools.r8.utils.ObjectUtils;
import java.util.Objects;

public class ProguardMemberRuleReturnValue {

  private enum Type {
    BOOLEAN,
    FIELD,
    NULLABILITY,
    STRING,
    VALUE_RANGE
  }

  private final Type type;
  private final boolean booleanValue;
  private final LongInterval longInterval;
  private final DexType fieldHolder;
  private final DexString fieldName;
  private final Nullability nullability;
  private final DexString stringValue;

  ProguardMemberRuleReturnValue(boolean value) {
    this.type = Type.BOOLEAN;
    this.booleanValue = value;
    this.longInterval = null;
    this.fieldHolder = null;
    this.fieldName = null;
    this.nullability = null;
    this.stringValue = null;
  }

  ProguardMemberRuleReturnValue(DexString stringValue) {
    this.type = Type.STRING;
    this.booleanValue = false;
    this.longInterval = null;
    this.fieldHolder = null;
    this.fieldName = null;
    this.nullability = Nullability.definitelyNotNull();
    this.stringValue = stringValue;
  }

  @SuppressWarnings("InconsistentOverloads")
  ProguardMemberRuleReturnValue(DexType fieldHolder, DexString fieldName, Nullability nullability) {
    assert !nullability.isDefinitelyNull();
    this.type = Type.FIELD;
    this.booleanValue = false;
    this.longInterval = null;
    this.fieldHolder = fieldHolder;
    this.fieldName = fieldName;
    this.nullability = nullability;
    this.stringValue = null;
  }

  ProguardMemberRuleReturnValue(Nullability nullability) {
    assert nullability.isDefinitelyNull() || nullability.isDefinitelyNotNull();
    this.type = Type.NULLABILITY;
    this.booleanValue = false;
    this.longInterval = null;
    this.fieldHolder = null;
    this.fieldName = null;
    this.nullability = nullability;
    this.stringValue = null;
  }

  ProguardMemberRuleReturnValue(LongInterval value) {
    this.type = Type.VALUE_RANGE;
    this.booleanValue = false;
    this.longInterval = value;
    this.fieldHolder = null;
    this.fieldName = null;
    this.nullability = getNullabilityForValueRange(value);
    this.stringValue = null;
  }

  private static Nullability getNullabilityForValueRange(LongInterval value) {
    if (value.isSingleValue(0)) {
      return Nullability.definitelyNull();
    } else if (!value.containsValue(0)) {
      return Nullability.definitelyNotNull();
    } else {
      return Nullability.maybeNull();
    }
  }

  public boolean isBoolean() {
    return type == Type.BOOLEAN;
  }

  public boolean isField() {
    return type == Type.FIELD;
  }

  public boolean isNullability() {
    return type == Type.NULLABILITY;
  }

  public boolean isString() {
    return type == Type.STRING;
  }

  public boolean isValueRange() {
    return type == Type.VALUE_RANGE;
  }

  public boolean getBoolean() {
    assert isBoolean();
    return booleanValue;
  }

  public DexType getFieldHolder() {
    assert isField();
    return fieldHolder;
  }

  public DexString getFieldName() {
    assert isField();
    return fieldName;
  }

  private boolean hasNullability() {
    return isField() || isNullability() || isString() || isValueRange();
  }

  public Nullability getNullability() {
    assert hasNullability();
    return nullability;
  }

  public DexString getString() {
    assert isString();
    return stringValue;
  }

  public LongInterval getValueRange() {
    assert isValueRange();
    return longInterval;
  }

  public AbstractValue toAbstractValue(AppView<?> appView, DexType valueType) {
    AbstractValueFactory abstractValueFactory = appView.abstractValueFactory();
    switch (type) {
      case BOOLEAN:
        return abstractValueFactory.createSingleNumberValue(
            BooleanUtils.intValue(booleanValue), TypeElement.getBoolean());

      case FIELD:
        DexClass holder = appView.definitionFor(fieldHolder);
        if (holder != null) {
          DexEncodedField field = holder.lookupUniqueStaticFieldWithName(fieldName);
          if (field != null) {
            return abstractValueFactory.createSingleStatelessFieldValue(field.getReference());
          }
        }
        return AbstractValue.unknown();

      case NULLABILITY:
        return nullability.isDefinitelyNull()
            ? abstractValueFactory.createUncheckedNullValue()
            : AbstractValue.unknown();

      case STRING:
        return abstractValueFactory.createSingleStringValue(stringValue);

      case VALUE_RANGE:
        if (valueType.isReferenceType()) {
          assert false;
          return AbstractValue.unknown();
        }
        return longInterval.isSingleValue()
            ? abstractValueFactory.createSingleNumberValue(
                longInterval.getSingleValue(), TypeElement.getLong())
            : abstractValueFactory.createNumberFromIntervalValue(
                longInterval.getMin(), longInterval.getMax());

      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }

  public DynamicType toDynamicType(AppView<?> appView, DexType valueType) {
    return valueType.isReferenceType() && hasNullability() && getNullability().isDefinitelyNotNull()
        ? DynamicType.definitelyNotNull()
        : DynamicType.unknown();
  }

  // TODO(b/409103321): Implement all cases.
  public boolean test(Value value) {
    switch (type) {
      case BOOLEAN:
        return value.isConstNumber(booleanValue ? 1 : 0);
      case FIELD:
        throw new Unimplemented("Unimplemented type: " + type);
      case NULLABILITY:
        throw new Unimplemented("Unimplemented type: " + type);
      case STRING:
        return value.isConstString(stringValue);
      case VALUE_RANGE:
        throw new Unimplemented("Unimplemented type: " + type);
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ProguardMemberRuleReturnValue)) {
      return false;
    }
    ProguardMemberRuleReturnValue other = (ProguardMemberRuleReturnValue) obj;
    return type == other.type
        && booleanValue == other.booleanValue
        && Objects.equals(longInterval, other.longInterval)
        && ObjectUtils.identical(fieldHolder, other.fieldHolder)
        && nullability == other.nullability
        && ObjectUtils.identical(stringValue, other.stringValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, booleanValue, longInterval, fieldHolder, nullability, stringValue);
  }

  @Override
  public String toString() {
    return " return " + getValueString();
  }

  public String getValueString() {
    switch (type) {
      case BOOLEAN:
        return Boolean.toString(booleanValue);
      case FIELD:
        StringBuilder result = new StringBuilder();
        if (nullability.isDefinitelyNotNull()) {
          result.append("@NonNull ");
        }
        result.append(fieldHolder.getTypeName()).append('.').append(fieldName);
        return result.toString();
      case NULLABILITY:
        return nullability.isDefinitelyNull() ? "null" : "@NonNull";
      case STRING:
        return stringValue.toString();
      case VALUE_RANGE:
        if (longInterval.isSingleValue()) {
          return Long.toString(longInterval.getMin());
        } else {
          return longInterval.getMin() + ".." + longInterval.getMax();
        }
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }
}
