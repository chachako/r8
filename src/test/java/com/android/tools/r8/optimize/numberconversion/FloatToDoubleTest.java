// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.numberconversion;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ir.code.NumericType;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class FloatToDoubleTest extends NumberConversionTestBase {

  @Parameterized.Parameter(0)
  public float input;

  @Parameterized.Parameter(1)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        new Float[] {
          128.12F,
          65408.61623F,
          -65408.61623F,
          32768.574F,
          -32768.574F,
          42F,
          -32F,
          0F,
          -0F,
          (float) Integer.MAX_VALUE,
          (float) Integer.MIN_VALUE,
          Float.MAX_VALUE,
          Float.MIN_VALUE,
          -Float.MAX_VALUE,
          -Float.MIN_VALUE,
          Float.NaN,
          Float.intBitsToFloat(0xFFFFFFFF), // Non-canonical NaN
          Float.intBitsToFloat(0xFFFFF00F), // Non-canonical NaN
          Float.intBitsToFloat(0x7F900000), // Non-canonical NaN
          Float.intBitsToFloat(0x7FBFFFFF), // Non-canonical NaN
          Float.POSITIVE_INFINITY,
          Float.NEGATIVE_INFINITY
        },
        TestParameters.builder().withNoneRuntime().build());
  }

  @Test
  public void testBitwise() throws Exception {
    // Check that input is bitwise preserved.
    testConversion(
        mv -> {
          mv.visitLdcInsn(input);
          mv.visitInsn(Opcodes.F2D);
        },
        NumericType.DOUBLE,
        Double.doubleToRawLongBits((double) input));
  }

  @Test
  public void testCompare() throws Exception {
    // DCMPG returns 1 if any of the inputs are NaN.
    assumeFalse(Double.isNaN(input));
    // Comparison is used to verify the internal state of constants rather than just the output
    // instructions. This avoids the situation where the input is not truncated during
    // optimization, but is still truncated in the final instructions because of typed extraction.
    testConversion(
        mv -> {
          mv.visitLdcInsn(input);
          mv.visitInsn(Opcodes.F2D);
          mv.visitLdcInsn((double) input);
          mv.visitInsn(Opcodes.DCMPG);
        },
        NumericType.INT,
        0);
  }
}
