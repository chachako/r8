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
public class DoubleToLongTest extends NumberConversionTestBase {

  @Parameterized.Parameter(0)
  public double input;

  @Parameterized.Parameter(1)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        new Double[] {
          128.12D,
          65408.61623D,
          -65408.61623D,
          32768.574D,
          -32768.574D,
          42D,
          -32D,
          0D,
          -0D,
          (double) Integer.MAX_VALUE,
          (double) Integer.MIN_VALUE,
          (double) Float.MAX_VALUE,
          (double) Float.MIN_VALUE,
          (double) -Float.MAX_VALUE,
          (double) -Float.MIN_VALUE,
          (double) Float.NaN,
          (double) Float.intBitsToFloat(0xFFFFFFFF),
          (double) Float.POSITIVE_INFINITY,
          (double) Float.NEGATIVE_INFINITY,
          Double.MAX_VALUE,
          Double.MIN_VALUE,
          -Double.MAX_VALUE,
          -Double.MIN_VALUE,
          Double.NaN,
          Double.longBitsToDouble(0xFFFFFFFFFFFFFFFFL),
          Double.POSITIVE_INFINITY,
          Double.NEGATIVE_INFINITY
        },
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
          mv.visitInsn(Opcodes.D2L);
          mv.visitLdcInsn((long) input);
          mv.visitInsn(Opcodes.LSUB);
        },
        NumericType.LONG,
        0);
  }
}
