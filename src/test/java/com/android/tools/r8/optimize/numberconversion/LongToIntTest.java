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
public class LongToIntTest extends NumberConversionTestBase {

  @Parameterized.Parameter(0)
  public long input;

  @Parameterized.Parameter(1)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        new Long[] {
          128L,
          65408L,
          -65408L,
          32768L,
          -32768L,
          42L,
          -32L,
          0L,
          (long) Integer.MAX_VALUE,
          (long) Integer.MIN_VALUE,
          Long.MAX_VALUE,
          Long.MIN_VALUE
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
          mv.visitInsn(Opcodes.L2I);
          mv.visitLdcInsn((int) input);
          mv.visitInsn(Opcodes.ISUB);
        },
        NumericType.INT,
        0);
  }
}
