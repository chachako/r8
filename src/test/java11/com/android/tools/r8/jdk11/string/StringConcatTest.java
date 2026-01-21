// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk11.string;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.Jdk9TestUtils;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringConcatTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "true",
          "1",
          "c",
          "3",
          "5",
          "7",
          "9.0",
          "11.0",
          "e",
          "concat",
          "first true and true",
          "1 true 2 false",
          "try true and false",
          "3 true 4 true",
          "ONE true TWO true",
          "ONE 1 TWO 0",
          "a 1 b 2 c 3 d 4 e CONST_STR f CONST_STR",
          "false123456.07.08hi",
          "SIDE EFFECT",
          "10",
          "hi3",
          "truetruetruefalsetrue0truenull",
          "true-3truedtruee");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withCfRuntime(CfVm.JDK17)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8CfNoDesugar() throws Exception {
    parameters.assumeJvmTestParameters();

    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setNoMinApi()
        .disableDesugaring()
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addOptionsModification(opt -> opt.enableStringConcatInstruction = true)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addOptionsModification(opt -> opt.enableStringConcatInstruction = true)
        .setMinApi(parameters)
        .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addDontObfuscate()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(inspector -> inspectOutput(inspector, true));
  }

  private long countStringBuilderOrInvokeDynamic(MethodSubject method) {
    return method
        .streamInstructions()
        .filter(
            ins ->
                CodeMatchers.isInvokeWithTarget(typeName(StringBuilder.class), "<init>").test(ins)
                    || ins.isInvokeDynamic())
        .count();
  }

  private void inspectOutput(CodeInspector inspector, boolean isR8) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    MethodSubject method = mainClass.uniqueMethodWithOriginalName("shouldConvertToValueOf");
    assertTrue(method.isPresent());
    assertEquals(0, countStringBuilderOrInvokeDynamic(method));

    // D8 does not simplify the String.valueOf(phiString).
    int expectedValueOfCount = isR8 ? 8 : 9;
    assertEquals(
        expectedValueOfCount,
        method
            .streamInstructions()
            .filter(ins -> ins.isInvokeStatic() && ins.getMethod().getName().isEqualTo("valueOf"))
            .count());

    method = mainClass.uniqueMethodWithOriginalName("shouldConvertToConcat");
    assertTrue(method.isPresent());
    assertEquals(0, countStringBuilderOrInvokeDynamic(method));
    assertEquals(
        1,
        method
            .streamInstructions()
            .filter(ins -> ins.isInvokeVirtual() && ins.getMethod().getName().isEqualTo("concat"))
            .count());

    method = mainClass.uniqueMethodWithOriginalName("shouldConvertToConcat");
    assertTrue(method.isPresent());
    assertEquals(0, countStringBuilderOrInvokeDynamic(method));
    assertEquals(
        1,
        method
            .streamInstructions()
            .filter(ins -> ins.isInvokeVirtual() && ins.getMethod().getName().isEqualTo("concat"))
            .count());

    method = mainClass.uniqueMethodWithOriginalName("doesRemoveExplicitToString");
    assertTrue(method.isPresent());
    assertEquals(
        0,
        method
            .streamInstructions()
            .filter(ins -> ins.isInvokeVirtual() && ins.getMethod().getName().isEqualTo("toString"))
            .count());
    assertEquals(
        0,
        method
            .streamInstructions()
            .filter(ins -> ins.isInvokeStatic() && ins.getMethod().getName().isEqualTo("valueOf"))
            .count());

    method = mainClass.uniqueMethodWithOriginalName("numericAndBooleanStrings");
    assertTrue(method.isPresent());
    assertEquals(0, method.streamInstructions().filter(ins -> ins.isConstString()).count());
  }

  static class Main {

    private static final String CONST_STR = "CONST_STR";
    private static int sideEffectCounter = 0;

    static class ToStringThatCounts {
      @Override
      public String toString() {
        return "" + sideEffectCounter++;
      }
    }

    static class ToStringThatPrints {
      @Override
      public String toString() {
        System.out.println("SIDE EFFECT");
        return "";
      }
    }

    public static void main(String[] strArr) {
      shouldConvertToValueOf();
      shouldConvertToConcat();
      firstSharedConcat();
      secondSharedConcatWithTryCatch();
      mergeStringsSharedConcat();
      mergeStringsWithSideEffects();
      mergeConstants();
      System.out.println(allTheParams(false, (byte) 1, '2', (short) 3, 4, 5, 6, 7, 8, "hi"));
      noOutValues();
      doesNotRemoveExplicitToString();
      doesRemoveExplicitToString();
      numericAndBooleanStrings();
    }

    @NeverInline
    public static void shouldConvertToValueOf() {
      boolean alwaysTrue = System.currentTimeMillis() > 0;
      System.out.println("" + alwaysTrue);
      System.out.println("" + (alwaysTrue ? (byte) 1 : (byte) 2));
      System.out.println("" + (alwaysTrue ? 'c' : 'd'));
      System.out.println("" + (alwaysTrue ? (short) 3 : (short) 4));
      System.out.println("" + (alwaysTrue ? 5 : 6));
      System.out.println("" + (alwaysTrue ? (long) 7 : (long) 8));
      System.out.println("" + (alwaysTrue ? 9f : 10f));
      System.out.println("" + (alwaysTrue ? 11.0 : 12.0));
      System.out.println("" + (alwaysTrue ? "e" : "f"));
    }

    @NeverInline
    public static void shouldConvertToConcat() {
      boolean alwaysTrue = System.currentTimeMillis() > 0;
      System.out.println("con" + (alwaysTrue ? "cat" : "dog"));
    }

    @NeverInline
    public static void firstSharedConcat() {
      boolean alwaysTrue = System.currentTimeMillis() > 0;
      System.out.println("first " + alwaysTrue + " and " + alwaysTrue);
      System.out.println("1 " + alwaysTrue + " 2 " + !alwaysTrue);
    }

    @NeverInline
    public static void secondSharedConcatWithTryCatch() {
      boolean alwaysTrue = System.currentTimeMillis() > 0;
      try {
        System.out.println("try " + alwaysTrue + " and " + !alwaysTrue);
        System.out.println("3 " + alwaysTrue + " 4 " + alwaysTrue);
      } catch (Exception e) {
      }
    }

    @NeverInline
    public static void mergeStringsSharedConcat() {
      boolean alwaysTrue = System.currentTimeMillis() > 0;
      String partOne = "ONE " + alwaysTrue;
      String partTwo = " TWO " + alwaysTrue;
      System.out.println(partOne + partTwo);
    }

    @NeverInline
    public static void mergeStringsWithSideEffects() {
      sideEffectCounter = 0;
      String partTwo = " TWO " + new ToStringThatCounts();
      String partOne = "ONE " + new ToStringThatCounts();
      System.out.println(partOne + partTwo);
    }

    @NeverInline
    public static void mergeConstants() {
      boolean alwaysTrue = System.currentTimeMillis() > 0;
      try {
        String partOne = "a " + '1';
        String partTwo = " b " + (short) 2;
        String partThree = " c " + (byte) 3;
        String partFour = " d " + (long) 4;
        String constStr = CONST_STR;
        // Test same inValue used twice
        String noop = (alwaysTrue ? "" : "X");
        String partFive = " e " + constStr + noop + " f " + constStr;
        String step1 = partOne + partTwo + partThree;
        String step2 = noop + step1 + partFour + noop + partFive;
        System.out.println(step2);
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }

    private static void noop(String value) {
      // Tests removing StringConcat with no out value.
    }

    @NeverInline
    public static void noOutValues() {
      boolean alwaysTrue = System.currentTimeMillis() > 0;
      try {
        // Values should be removed.
        noop(1 + "a" + 3);
        // Should not be removed due to side effects.
        noop("asdf" + (alwaysTrue ? new ToStringThatPrints() : null));
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }

    @NeverInline
    public static String allTheParams(
        boolean a, byte b, char c, short d, int e, long f, float g, double h, Object i, String j) {
      return "" + a + b + c + d + e + f + g + h + i + j;
    }

    @NeverInline
    public static void doesNotRemoveExplicitToString() {
      sideEffectCounter = 0;
      String partOne = new ToStringThatCounts().toString();
      String partTwo = new ToStringThatCounts().toString();
      // The explicit .toString() calls should not be optimized since the toString() side effects
      // would run in the wrong order.
      System.out.println(partTwo + partOne);
    }

    @NeverInline
    public static void doesRemoveExplicitToString() {
      String partOne = "hi".toString();
      String partTwo = Integer.valueOf(3).toString();
      System.out.println(partOne + partTwo);
    }

    @NeverInline
    public static void numericAndBooleanStrings() {
      boolean alwaysTrue = System.currentTimeMillis() > 0;
      System.out.println(
          alwaysTrue + "" + true + alwaysTrue + "false" + alwaysTrue + 0 + alwaysTrue + "null");
      System.out.println(alwaysTrue + "-3" + alwaysTrue + 'd' + alwaysTrue + "e");
    }
  }
}
