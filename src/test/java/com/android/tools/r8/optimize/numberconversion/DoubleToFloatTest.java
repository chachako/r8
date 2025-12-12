// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.numberconversion;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.utils.LongUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class DoubleToFloatTest extends NumberConversionTestBase {

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
          Double.MAX_VALUE,
          Double.MIN_VALUE,
          -Double.MAX_VALUE,
          -Double.MIN_VALUE,
          Double.NaN,
          Double.longBitsToDouble(0xFFFFFFFFFFFFFFFFL), // Non-canonical NaN
          Double.longBitsToDouble(0xFFFFF0000000000FL), // Non-canonical NaN
          Double.longBitsToDouble(0x7F90000000000000L), // Non-canonical NaN
          Double.longBitsToDouble(0x7FBFFFFFFFFFFFFFL), // Non-canonical NaN
          Double.POSITIVE_INFINITY,
          Double.NEGATIVE_INFINITY
        },
        TestParameters.builder().withNoneRuntime().build());
  }

  @Test
  public void testBitwise() throws Exception {
    // Check that input is bitwise preserved.
    testConversion(
        mv -> {
          mv.visitLdcInsn(input);
          mv.visitInsn(Opcodes.D2F);
        },
        NumericType.FLOAT,
        LongUtils.encodeFloat((float) input));
  }

  @Test
  public void testCompare() throws Exception {
    // FCMPG returns 1 if any of the inputs are NaN.
    assumeFalse(Double.isNaN(input));
    // Comparison is used to verify the internal state of constants rather than just the output
    // instructions. This avoids the situation where the input is not truncated during
    // optimization, but is still truncated in the final instructions because of typed extraction.
    testConversion(
        mv -> {
          mv.visitLdcInsn(input);
          mv.visitInsn(Opcodes.D2F);
          mv.visitLdcInsn((float) input);
          mv.visitInsn(Opcodes.FCMPG);
        },
        NumericType.INT,
        0);
  }
}
