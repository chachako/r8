// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.code.FieldGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.LongInterval;
import com.android.tools.r8.utils.ObjectUtils;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;

public class ProguardMemberRuleValue {

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

  ProguardMemberRuleValue(boolean value) {
    this.type = Type.BOOLEAN;
    this.booleanValue = value;
    this.longInterval = null;
    this.fieldHolder = null;
    this.fieldName = null;
    this.nullability = null;
    this.stringValue = null;
  }

  ProguardMemberRuleValue(DexString stringValue) {
    this.type = Type.STRING;
    this.booleanValue = false;
    this.longInterval = null;
    this.fieldHolder = null;
    this.fieldName = null;
    this.nullability = Nullability.definitelyNotNull();
    this.stringValue = stringValue;
  }

  @SuppressWarnings("InconsistentOverloads")
  ProguardMemberRuleValue(DexType fieldHolder, DexString fieldName, Nullability nullability) {
    assert !nullability.isDefinitelyNull();
    this.type = Type.FIELD;
    this.booleanValue = false;
    this.longInterval = null;
    this.fieldHolder = fieldHolder;
    this.fieldName = fieldName;
    this.nullability = nullability;
    this.stringValue = null;
  }

  ProguardMemberRuleValue(Nullability nullability) {
    assert nullability.isDefinitelyNull() || nullability.isDefinitelyNotNull();
    this.type = Type.NULLABILITY;
    this.booleanValue = false;
    this.longInterval = null;
    this.fieldHolder = null;
    this.fieldName = null;
    this.nullability = nullability;
    this.stringValue = null;
  }

  ProguardMemberRuleValue(LongInterval value) {
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

  public ProguardMemberRuleValue resolveFieldValue(AppView<?> appView, DexType valueType) {
    AbstractValue value = toAbstractValue(appView, valueType);
    if (valueType.isPrimitiveType()) {
      if (!value.isSingleNumberValue()) {
        return this;
      }
      SingleNumberValue singleNumberValue = value.asSingleNumberValue();
      if (valueType.isBooleanType()) {
        if (singleNumberValue.isSingleBoolean()) {
          return new ProguardMemberRuleValue(singleNumberValue.isTrue());
        }
      } else if (valueType.isByteType()
          || valueType.isCharType()
          || valueType.isIntType()
          || valueType.isShortType()) {
        return new ProguardMemberRuleValue(new LongInterval(singleNumberValue.getIntValue()));
      } else if (valueType.isLongType()) {
        return new ProguardMemberRuleValue(new LongInterval(singleNumberValue.getLongValue()));
      } else {
        assert valueType.isDoubleType() || valueType.isFloatType();
      }
    } else {
      assert valueType.isReferenceType();
      if (value.isNull()) {
        return new ProguardMemberRuleValue(Nullability.definitelyNull());
      } else if (value.isSingleStringValue()) {
        return new ProguardMemberRuleValue(value.asSingleStringValue().getDexString());
      }
    }
    return this;
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
            if (field.isFinal() && field.hasExplicitStaticValue()) {
              AbstractValue abstractValue =
                  field.getStaticValue().toAbstractValue(abstractValueFactory);
              if (abstractValue.isSingleValue()) {
                return abstractValue;
              }
            }
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

  public boolean test(AppView<?> appView, Value value) {
    switch (type) {
      case BOOLEAN:
        return value.isConstNumber(booleanValue ? 1 : 0);
      case FIELD:
        {
          Value root = value.getAliasedValue();
          // If the value is defined by a field-get instruction, then check if the field instruction
          // matches the field in the condition.
          if (root.isDefinedByInstructionSatisfying(Instruction::isFieldGet)) {
            FieldGet fieldGet = value.getDefinition().asFieldGet();
            DexField field = fieldGet.getField();
            if (field.getName().isIdenticalTo(getFieldName())) {
              if (field.getHolderType().isIdenticalTo(getFieldHolder())) {
                return true;
              }
              if (appView.hasClassHierarchy()) {
                DexEncodedField resolvedField =
                    appView.appInfoWithClassHierarchy().resolveField(field).getResolvedField();
                return resolvedField != null
                    && resolvedField.getHolderType().isIdenticalTo(getFieldHolder());
              }
            }
            return false;
          }

          // Otherwise lookup the field in the condition and check if it has a constant value.
          // If so, check if the value matches the constant value.
          if (appView.hasClassHierarchy()
              && root.isDefinedByInstructionSatisfying(Instruction::isConstInstruction)) {
            DexClass fieldHolder = appView.definitionFor(getFieldHolder());
            if (fieldHolder != null) {
              List<DexEncodedField> fields =
                  Lists.newArrayList(
                      fieldHolder.fields(f -> f.getName().isIdenticalTo(getFieldName())));
              if (fields.size() == 1) {
                DexEncodedField field = fields.get(0);
                AbstractValue fieldValue = field.getOptimizationInfo().getAbstractValue();
                if (fieldValue.isSingleValue()) {
                  if (fieldValue.isNull()) {
                    return value.getType().isReferenceType()
                        && value.getType().nullability().isDefinitelyNull();
                  } else if (fieldValue.isSingleConstClassValue()) {
                    return value.isConstClass(fieldValue.asSingleConstClassValue().getType());
                  } else if (fieldValue.isSingleNumberValue()) {
                    return value.isConstNumber(fieldValue.asSingleNumberValue().getValue());
                  } else if (fieldValue.isSingleStringValue()) {
                    return value.isConstString(fieldValue.asSingleStringValue().getDexString());
                  }
                }
              }
            }
          }
          return false;
        }
      case NULLABILITY:
        {
          assert getNullability().isDefinitelyNotNull() || getNullability().isDefinitelyNull();
          TypeElement type = value.getType();
          return type.isReferenceType() && type.nullability() == getNullability();
        }
      case STRING:
        return value.isConstString(stringValue);
      case VALUE_RANGE:
        {
          if (value.isConstNumber()) {
            long rawValue = value.getConstInstruction().asConstNumber().getRawValue();
            return longInterval.containsValue(rawValue);
          }
          return false;
        }
      default:
        throw new Unreachable("Unexpected type: " + type);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ProguardMemberRuleValue)) {
      return false;
    }
    ProguardMemberRuleValue other = (ProguardMemberRuleValue) obj;
    return type == other.type
        && booleanValue == other.booleanValue
        && Objects.equals(longInterval, other.longInterval)
        && ObjectUtils.identical(fieldHolder, other.fieldHolder)
        && ObjectUtils.identical(fieldName, other.fieldName)
        && nullability == other.nullability
        && ObjectUtils.identical(stringValue, other.stringValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        type, booleanValue, longInterval, fieldHolder, fieldName, nullability, stringValue);
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
