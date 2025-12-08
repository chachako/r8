// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.numberconversion;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ir.code.NumericType;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class IntToLongTest extends NumberConversionTestBase {

  @Parameterized.Parameter(0)
  public int input;

  @Parameterized.Parameter(1)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        new Integer[] {128, 65408, -65408, 42, -32, 0, Integer.MAX_VALUE, Integer.MIN_VALUE},
        TestParameters.builder().withNoneRuntime().build());
  }

  @Test
  public void test() throws Exception {
    // Subtraction is used to verify the internal state of constants rather than just the output
    // instructions. This avoids the situation where the input is not truncated during optimization,
    // but is still truncated in the final instructions because of typed extraction.
    testConversion(
        mv -> {
          mv.visitLdcInsn(input);
          mv.visitInsn(Opcodes.I2L);
          mv.visitLdcInsn((long) input);
          mv.visitInsn(Opcodes.LSUB);
        },
        NumericType.LONG,
        0);
  }
}
