// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.numberconversion;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConversionTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "INT_TO_BYTE",
          "42",
          "0",
          "-13",
          "-1",
          "0",
          "127",
          "-128",
          "-128",
          "INT_TO_CHAR",
          "42",
          "0",
          "65523",
          "65535",
          "0",
          "65535",
          "0",
          "0",
          "INT_TO_SHORT",
          "42",
          "0",
          "-13",
          "-1",
          "0",
          "32767",
          "-32768",
          "-32768",
          "INT_TO_LONG",
          "42",
          "0",
          "-13",
          "2147483647",
          "-2147483648",
          "INT_TO_FLOAT",
          "1109917696",
          "0",
          "-1051721728",
          "1325400064",
          "-822083584",
          "INT_TO_DOUBLE",
          "4631107791820423168",
          "0",
          "-4599864069405540352",
          "4746794007244308480",
          "-4476578029606273024",
          "LONG_TO_INT",
          "42",
          "0",
          "-13",
          "-1",
          "0",
          "2147483647",
          "-2147483648",
          "-2147483648",
          "LONG_TO_FLOAT",
          "1109917696",
          "0",
          "-1051721728",
          "1593835520",
          "-553648128",
          "1325400064",
          "1325400064",
          "-822083584",
          "LONG_TO_DOUBLE",
          "4631107791820423168",
          "0",
          "-4599864069405540352",
          "4890909195324358656",
          "-4332462841530417152",
          "4746794007244308480",
          "4746794007248502784",
          "-4476578029606273024",
          "FLOAT_TO_INT",
          "42",
          "42",
          "0",
          "0",
          "-13",
          "-13",
          "2147483647",
          "2147483647",
          "0",
          "0",
          "-2147483648",
          "-2147483648",
          "0",
          "0",
          "2147483647",
          "2147483647",
          "-2147483648",
          "FLOAT_TO_LONG",
          "42",
          "42",
          "0",
          "0",
          "-13",
          "-13",
          "9223372036854775807",
          "9223372036854775807",
          "0",
          "0",
          "-9223372036854775808",
          "-9223372036854775808",
          "0",
          "0",
          "9223372036854775807",
          "9223372036854775807",
          "-9223372036854775808",
          "-9223372036854775808",
          "FLOAT_TO_DOUBLE",
          "4631107791820423168",
          "4631171123797557248",
          "0",
          "-9223372036854775808",
          "true",
          "-4599864069405540352",
          "-4599610742033874944",
          "9218868437227405312",
          "5183643170566569984",
          "3936146074321813504",
          "-5287225962532962304",
          "-4039728866288205824",
          "-4503599627370496",
          "9221120237041090560",
          "-536870912",
          "true",
          "DOUBLE_TO_INT",
          "42",
          "42",
          "0",
          "0",
          "-13",
          "-13",
          "2147483647",
          "2147483647",
          "0",
          "0",
          "-2147483648",
          "-2147483648",
          "0",
          "0",
          "2147483647",
          "2147483647",
          "-2147483648",
          "DOUBLE_TO_LONG",
          "42",
          "42",
          "0",
          "0",
          "-13",
          "-13",
          "9223372036854775807",
          "9223372036854775807",
          "0",
          "0",
          "-9223372036854775808",
          "-9223372036854775808",
          "0",
          "0",
          "9223372036854775807",
          "9223372036854775807",
          "-9223372036854775808",
          "-9223372036854775808",
          "DOUBLE_TO_FLOAT",
          "1109917696",
          "1110035661",
          "0",
          "-2147483648",
          "true",
          "-1051721728",
          "-1051249869",
          "2139095040",
          "2139095040",
          "0",
          "-2147483648",
          "-8388608",
          "-8388608",
          "2143289344",
          "-1",
          "true");

  @Parameterized.Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .enableMemberValuePropagationAnnotations()
        .enableInliningAnnotations()
        .addKeepMainRule(TestClass.class)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(TestClass.class)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static class TestClass {
    public static void main(String[] args) {
      System.out.println("INT_TO_BYTE");
      System.out.println((byte) 42);
      System.out.println((byte) 0);
      System.out.println((byte) -13);
      System.out.println((byte) Integer.MAX_VALUE);
      System.out.println((byte) Integer.MIN_VALUE);
      System.out.println((byte) 127);
      System.out.println((byte) (127 + 1));
      System.out.println((byte) -128);

      System.out.println("INT_TO_CHAR");
      System.out.println(charBits((char) 42));
      System.out.println(charBits((char) 0));
      System.out.println(charBits((char) -13));
      System.out.println(charBits((char) Integer.MAX_VALUE));
      System.out.println(charBits((char) Integer.MIN_VALUE));
      System.out.println(charBits((char) 0xFFFF));
      System.out.println(charBits((char) (0xFFFF + 1)));
      System.out.println(charBits((char) 0x0000));

      System.out.println("INT_TO_SHORT");
      System.out.println((short) 42);
      System.out.println((short) 0);
      System.out.println((short) -13);
      System.out.println((short) Integer.MAX_VALUE);
      System.out.println((short) Integer.MIN_VALUE);
      System.out.println((short) 32767);
      System.out.println((short) (32767 + 1));
      System.out.println((short) -32768);

      System.out.println("INT_TO_LONG");
      System.out.println((long) 42);
      System.out.println((long) 0);
      System.out.println((long) -13);
      System.out.println((long) Integer.MAX_VALUE);
      System.out.println((long) Integer.MIN_VALUE);

      System.out.println("INT_TO_FLOAT");
      System.out.println(Float.floatToRawIntBits((float) 42));
      System.out.println(Float.floatToRawIntBits((float) 0));
      System.out.println(Float.floatToRawIntBits((float) -13));
      System.out.println(Float.floatToRawIntBits((float) Integer.MAX_VALUE));
      System.out.println(Float.floatToRawIntBits((float) Integer.MIN_VALUE));

      System.out.println("INT_TO_DOUBLE");
      System.out.println(Double.doubleToRawLongBits((double) 42));
      System.out.println(Double.doubleToRawLongBits((double) 0));
      System.out.println(Double.doubleToRawLongBits((double) -13));
      System.out.println(Double.doubleToRawLongBits((double) Integer.MAX_VALUE));
      System.out.println(Double.doubleToRawLongBits((double) Integer.MIN_VALUE));

      System.out.println("LONG_TO_INT");
      System.out.println((int) 42L);
      System.out.println((int) 0L);
      System.out.println((int) -13L);
      System.out.println((int) Long.MAX_VALUE);
      System.out.println((int) Long.MIN_VALUE);
      System.out.println((int) 2147483647L);
      System.out.println((int) (2147483647L + 1));
      System.out.println((int) -2147483648L);

      System.out.println("LONG_TO_FLOAT");
      System.out.println(Float.floatToRawIntBits((float) 42L));
      System.out.println(Float.floatToRawIntBits((float) 0L));
      System.out.println(Float.floatToRawIntBits((float) -13L));
      System.out.println(Float.floatToRawIntBits((float) Long.MAX_VALUE));
      System.out.println(Float.floatToRawIntBits((float) Long.MIN_VALUE));
      System.out.println(Float.floatToRawIntBits((float) 2147483647L));
      System.out.println(Float.floatToRawIntBits((float) (2147483647L + 1)));
      System.out.println(Float.floatToRawIntBits((float) -2147483648L));

      System.out.println("LONG_TO_DOUBLE");
      System.out.println(Double.doubleToRawLongBits((double) 42L));
      System.out.println(Double.doubleToRawLongBits((double) 0L));
      System.out.println(Double.doubleToRawLongBits((double) -13L));
      System.out.println(Double.doubleToRawLongBits((double) Long.MAX_VALUE));
      System.out.println(Double.doubleToRawLongBits((double) Long.MIN_VALUE));
      System.out.println(Double.doubleToRawLongBits((double) 2147483647L));
      System.out.println(Double.doubleToRawLongBits((double) (2147483647L + 1)));
      System.out.println(Double.doubleToRawLongBits((double) -2147483648L));

      System.out.println("FLOAT_TO_INT");
      System.out.println((int) 42F);
      System.out.println((int) 42.45F);
      System.out.println((int) +0F);
      System.out.println((int) -0F);
      System.out.println((int) -13F);
      System.out.println((int) -13.45F);
      System.out.println((int) Float.POSITIVE_INFINITY);
      System.out.println((int) Float.MAX_VALUE);
      System.out.println((int) Float.MIN_VALUE);
      System.out.println((int) -Float.MIN_VALUE);
      System.out.println((int) -Float.MAX_VALUE);
      System.out.println((int) Float.NEGATIVE_INFINITY);
      System.out.println((int) Float.NaN);
      System.out.println((int) Float.intBitsToFloat(0xFFFFFFFF));
      System.out.println((int) 2147483647F);
      System.out.println((int) (2147483647F + 100));
      System.out.println((int) -2147483648F);

      System.out.println("FLOAT_TO_LONG");
      System.out.println((long) 42F);
      System.out.println((long) 42.45F);
      System.out.println((long) +0F);
      System.out.println((long) -0F);
      System.out.println((long) -13F);
      System.out.println((long) -13.45F);
      System.out.println((long) Float.POSITIVE_INFINITY);
      System.out.println((long) Float.MAX_VALUE);
      System.out.println((long) Float.MIN_VALUE);
      System.out.println((long) -Float.MIN_VALUE);
      System.out.println((long) -Float.MAX_VALUE);
      System.out.println((long) Float.NEGATIVE_INFINITY);
      System.out.println((long) Float.NaN);
      System.out.println((long) Float.intBitsToFloat(0xFFFFFFFF));
      System.out.println((long) 9223372036854775807F);
      System.out.println((long) (9223372036854775807F + 100));
      System.out.println((long) -9223372036854775808F);
      System.out.println((long) (-9223372036854775808F - 100));

      System.out.println("FLOAT_TO_DOUBLE");
      System.out.println(Double.doubleToRawLongBits((double) 42F));
      System.out.println(Double.doubleToRawLongBits((double) 42.45F));
      System.out.println(Double.doubleToRawLongBits((double) +0F));
      System.out.println(Double.doubleToRawLongBits((double) -0F));
      System.out.println(
          Double.doubleToRawLongBits((double) +0F) != Double.doubleToRawLongBits((double) -0F));
      System.out.println(Double.doubleToRawLongBits((double) -13F));
      System.out.println(Double.doubleToRawLongBits((double) -13.45F));
      System.out.println(Double.doubleToRawLongBits((double) Float.POSITIVE_INFINITY));
      System.out.println(Double.doubleToRawLongBits((double) Float.MAX_VALUE));
      System.out.println(Double.doubleToRawLongBits((double) Float.MIN_VALUE));
      System.out.println(Double.doubleToRawLongBits((double) -Float.MIN_VALUE));
      System.out.println(Double.doubleToRawLongBits((double) -Float.MAX_VALUE));
      System.out.println(Double.doubleToRawLongBits((double) Float.NEGATIVE_INFINITY));
      System.out.println(Double.doubleToRawLongBits((double) Float.NaN));
      System.out.println(Double.doubleToRawLongBits((double) Float.intBitsToFloat(0xFFFFFFFF)));
      System.out.println(
          Double.doubleToRawLongBits((double) Float.NaN)
              != Double.doubleToRawLongBits((double) Float.intBitsToFloat(0xFFFFFFFF)));

      System.out.println("DOUBLE_TO_INT");
      System.out.println((int) 42D);
      System.out.println((int) 42.45D);
      System.out.println((int) +0D);
      System.out.println((int) -0D);
      System.out.println((int) -13D);
      System.out.println((int) -13.45D);
      System.out.println((int) Double.POSITIVE_INFINITY);
      System.out.println((int) Double.MAX_VALUE);
      System.out.println((int) Double.MIN_VALUE);
      System.out.println((int) -Double.MIN_VALUE);
      System.out.println((int) -Double.MAX_VALUE);
      System.out.println((int) Double.NEGATIVE_INFINITY);
      System.out.println((int) Double.NaN);
      System.out.println((int) Double.longBitsToDouble(0xFFFFFFFFFFFFFFFFL));
      System.out.println((int) 2147483647F);
      System.out.println((int) (2147483647F + 100));
      System.out.println((int) -2147483648F);

      System.out.println("DOUBLE_TO_LONG");
      System.out.println((long) 42D);
      System.out.println((long) 42.45D);
      System.out.println((long) +0D);
      System.out.println((long) -0D);
      System.out.println((long) -13D);
      System.out.println((long) -13.45D);
      System.out.println((long) Double.POSITIVE_INFINITY);
      System.out.println((long) Double.MAX_VALUE);
      System.out.println((long) Double.MIN_VALUE);
      System.out.println((long) -Double.MIN_VALUE);
      System.out.println((long) -Double.MAX_VALUE);
      System.out.println((long) Double.NEGATIVE_INFINITY);
      System.out.println((long) Double.NaN);
      System.out.println((long) Double.longBitsToDouble(0xFFFFFFFFFFFFFFFFL));
      System.out.println((long) 9223372036854775807D);
      System.out.println((long) (9223372036854775807D + 100));
      System.out.println((long) -9223372036854775808D);
      System.out.println((long) (-9223372036854775808D - 100));

      System.out.println("DOUBLE_TO_FLOAT");
      System.out.println(Float.floatToRawIntBits((float) 42D));
      System.out.println(Float.floatToRawIntBits((float) 42.45D));
      System.out.println(Float.floatToRawIntBits((float) +0D));
      System.out.println(Float.floatToRawIntBits((float) -0D));
      System.out.println(Float.floatToIntBits((float) +0D) != Float.floatToIntBits((float) -0D));
      System.out.println(Float.floatToRawIntBits((float) -13D));
      System.out.println(Float.floatToRawIntBits((float) -13.45D));
      System.out.println(Float.floatToRawIntBits((float) Double.POSITIVE_INFINITY));
      System.out.println(Float.floatToRawIntBits((float) Double.MAX_VALUE));
      System.out.println(Float.floatToRawIntBits((float) Double.MIN_VALUE));
      System.out.println(Float.floatToRawIntBits((float) -Double.MIN_VALUE));
      System.out.println(Float.floatToRawIntBits((float) -Double.MAX_VALUE));
      System.out.println(Float.floatToRawIntBits((float) Double.NEGATIVE_INFINITY));
      System.out.println(Float.floatToRawIntBits((float) Double.NaN));
      System.out.println(
          Float.floatToRawIntBits((float) Double.longBitsToDouble(0xFFFFFFFFFFFFFFFFL)));
      System.out.println(
          Float.floatToRawIntBits((float) Double.NaN)
              != Float.floatToRawIntBits((float) Double.longBitsToDouble(0xFFFFFFFFFFFFFFFFL)));
    }

    @NeverInline
    @NeverPropagateValue
    private static int charBits(char c) {
      return (int) c;
    }
  }
}
