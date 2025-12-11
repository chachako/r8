// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfNumberConversion;
import com.android.tools.r8.dex.code.DexDoubleToFloat;
import com.android.tools.r8.dex.code.DexDoubleToInt;
import com.android.tools.r8.dex.code.DexDoubleToLong;
import com.android.tools.r8.dex.code.DexFloatToDouble;
import com.android.tools.r8.dex.code.DexFloatToInt;
import com.android.tools.r8.dex.code.DexFloatToLong;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexIntToByte;
import com.android.tools.r8.dex.code.DexIntToChar;
import com.android.tools.r8.dex.code.DexIntToDouble;
import com.android.tools.r8.dex.code.DexIntToFloat;
import com.android.tools.r8.dex.code.DexIntToLong;
import com.android.tools.r8.dex.code.DexIntToShort;
import com.android.tools.r8.dex.code.DexLongToDouble;
import com.android.tools.r8.dex.code.DexLongToFloat;
import com.android.tools.r8.dex.code.DexLongToInt;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.LongUtils;
import java.util.Set;

public class NumberConversion extends Unop {

  private final NumberConversionType type;

  public NumberConversionType getType() {
    return this.type;
  }

  public NumberConversion(NumberConversionType type, Value dest, Value source) {
    super(dest, source);
    this.type = type;
  }

  @Override
  public int opcode() {
    return Opcodes.NUMBER_CONVERSION;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int dest = builder.allocatedRegister(dest(), getNumber());
    int src = builder.allocatedRegister(source(), getNumber());
    switch (type) {
      case INT_TO_BYTE:
        instruction = new DexIntToByte(dest, src);
        break;
      case INT_TO_CHAR:
        instruction = new DexIntToChar(dest, src);
        break;
      case INT_TO_SHORT:
        instruction = new DexIntToShort(dest, src);
        break;
      case INT_TO_LONG:
        instruction = new DexIntToLong(dest, src);
        break;
      case INT_TO_FLOAT:
        instruction = new DexIntToFloat(dest, src);
        break;
      case INT_TO_DOUBLE:
        instruction = new DexIntToDouble(dest, src);
        break;
      case LONG_TO_INT:
        instruction = new DexLongToInt(dest, src);
        break;
      case LONG_TO_FLOAT:
        instruction = new DexLongToFloat(dest, src);
        break;
      case LONG_TO_DOUBLE:
        instruction = new DexLongToDouble(dest, src);
        break;
      case FLOAT_TO_INT:
        instruction = new DexFloatToInt(dest, src);
        break;
      case FLOAT_TO_LONG:
        instruction = new DexFloatToLong(dest, src);
        break;
      case FLOAT_TO_DOUBLE:
        instruction = new DexFloatToDouble(dest, src);
        break;
      case DOUBLE_TO_INT:
        instruction = new DexDoubleToInt(dest, src);
        break;
      case DOUBLE_TO_LONG:
        instruction = new DexDoubleToLong(dest, src);
        break;
      case DOUBLE_TO_FLOAT:
        instruction = new DexDoubleToFloat(dest, src);
        break;
      default:
        throw new Unreachable(type + " is not caught by exhaustive switch");
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isNumberConversion()) {
      return false;
    }
    NumberConversion o = other.asNumberConversion();
    return o.type == type;
  }

  @Override
  public boolean isNumberConversion() {
    return true;
  }

  @Override
  public NumberConversion asNumberConversion() {
    return this;
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return PrimitiveTypeElement.fromNumericType(type.getTo());
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfNumberConversion(type), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addNumberConversion(type, source());
  }

  @Override
  public boolean outTypeKnownToBeBoolean(Set<Phi> seen) {
    return type.getTo() == NumericType.BYTE && source().knownToBeBoolean(seen);
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    AbstractValue val = abstractValueSupplier.getAbstractValue(getFirstOperand(), appView, context);
    if (!val.isSingleNumberValue()) {
      return super.getAbstractValue(appView, context, abstractValueSupplier);
    }
    SingleNumberValue num = val.asSingleNumberValue();
    AbstractValueFactory valueFactory = appView.abstractValueFactory();
    PrimitiveTypeElement typeElement = PrimitiveTypeElement.fromNumericType(type.getTo());

    switch (type) {
      case INT_TO_BYTE:
        long rawIntToByteValue = (byte) num.getIntValue();
        return valueFactory.createSingleNumberValue(rawIntToByteValue, typeElement);
      case INT_TO_CHAR:
        long rawIntToCharValue = (char) num.getIntValue();
        return valueFactory.createSingleNumberValue(rawIntToCharValue, typeElement);
      case INT_TO_SHORT:
        long rawIntToShortValue = (short) num.getIntValue();
        return valueFactory.createSingleNumberValue(rawIntToShortValue, typeElement);
      case INT_TO_LONG:
        long rawIntToLongValue = (long) num.getIntValue();
        return valueFactory.createSingleNumberValue(rawIntToLongValue, typeElement);
      case INT_TO_FLOAT:
        long rawIntToFloatValue = LongUtils.encodeFloat((float) num.getIntValue());
        return valueFactory.createSingleNumberValue(rawIntToFloatValue, typeElement);
      case INT_TO_DOUBLE:
        long rawIntToDoubleValue = Double.doubleToLongBits((double) num.getIntValue());
        return valueFactory.createSingleNumberValue(rawIntToDoubleValue, typeElement);
      case LONG_TO_INT:
        long rawLongToIntValue = (int) num.getLongValue();
        return valueFactory.createSingleNumberValue(rawLongToIntValue, typeElement);
      case LONG_TO_FLOAT:
        long rawLongToFloatValue = LongUtils.encodeFloat((float) num.getLongValue());
        return valueFactory.createSingleNumberValue(rawLongToFloatValue, typeElement);
      case LONG_TO_DOUBLE:
        long rawLongToDoubleValue = Double.doubleToLongBits((double) num.getLongValue());
        return valueFactory.createSingleNumberValue(rawLongToDoubleValue, typeElement);
      case FLOAT_TO_INT:
        long rawFloatToIntValue = (int) num.getFloatValue();
        return valueFactory.createSingleNumberValue(rawFloatToIntValue, typeElement);
      case FLOAT_TO_LONG:
        long rawFloatToLongValue = (long) num.getFloatValue();
        return valueFactory.createSingleNumberValue(rawFloatToLongValue, typeElement);
      case DOUBLE_TO_INT:
        long rawDoubleToIntValue = (int) num.getDoubleValue();
        return valueFactory.createSingleNumberValue(rawDoubleToIntValue, typeElement);
      case DOUBLE_TO_LONG:
        long rawDoubleToLongValue = (long) num.getDoubleValue();
        return valueFactory.createSingleNumberValue(rawDoubleToLongValue, typeElement);
      default:
        return super.getAbstractValue(appView, context, abstractValueSupplier);
    }
  }
}
