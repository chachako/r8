// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexSgetByte extends DexSgetOrSput implements CfOrDexStaticFieldRead {

  public static final int OPCODE = 0x64;
  public static final String NAME = "SgetByte";
  public static final String SMALI_NAME = "sget-byte";

  DexSgetByte(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getFieldMap());
  }

  public DexSgetByte(int AA, DexField BBBB) {
    super(AA, BBBB);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public void registerUse(UseRegistry<?> registry) {
    registry.registerStaticFieldReadInstruction(this);
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addStaticGet(AA, getField());
  }

  @Override
  public DexSgetByte withField(DexField field) {
    DexSgetByte instruction = new DexSgetByte(AA, field);
    instruction.setOffset(getOffset());
    return instruction;
  }
}
