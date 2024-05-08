// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package switchpatternmatching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumSwitchUsingEnumSwitchBootstrapMethod extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public static String EXPECTED_OUTPUT =
      StringUtils.lines(
          "null",
          "Spades or Piques",
          "Hearts or C\u0153ur",
          "Diamonds or Carreaux",
          "Clubs or Trefles",
          "Trumps or Atouts",
          "The Fool or L'Excuse");

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    CodeInspector inspector = new CodeInspector(ToolHelper.getClassFileForTestClass(Main.class));
    // javac generated an invokedynamic using bootstrap method
    // java.lang.runtime.SwitchBootstraps.enumSwitch.
    assertEquals(
        1,
        inspector
            .clazz(Main.class)
            .uniqueMethodWithOriginalName("enumSwitch")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeDynamic)
            .map(
                instruction ->
                    instruction
                        .asCfInstruction()
                        .getInstruction()
                        .asInvokeDynamic()
                        .getCallSite()
                        .getBootstrapMethod()
                        .member
                        .asDexMethod())
            .filter(
                method ->
                    method
                        .getHolderType()
                        .toString()
                        .contains("java.lang.runtime.SwitchBootstraps"))
            .filter(method -> method.toString().contains("enumSwitch"))
            .count());

    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.getCfRuntime().isNewerThanOrEqual(CfVm.JDK21),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue("For Cf we should compile with Jdk 21 library", parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public enum Tarot {
    SPADE,
    HEART,
    DIAMOND,
    CLUB,
    TRUMP,
    EXCUSE
  }

  public static class Main {

    public static void main(String[] args) {
      enumSwitch(null);
      enumSwitch(Tarot.SPADE);
      enumSwitch(Tarot.HEART);
      enumSwitch(Tarot.DIAMOND);
      enumSwitch(Tarot.CLUB);
      enumSwitch(Tarot.TRUMP);
      enumSwitch(Tarot.EXCUSE);
    }

    static void enumSwitch(Tarot t1) {
      switch (t1) {
        case null -> System.out.println("null");
        case SPADE -> System.out.println("Spades or Piques");
        case HEART -> System.out.println("Hearts or C\u0153ur");
        case Tarot t when t == Tarot.DIAMOND -> System.out.println("Diamonds or Carreaux");
        case Tarot t when t == Tarot.CLUB -> System.out.println("Clubs or Trefles");
        case Tarot t when t == Tarot.TRUMP -> System.out.println("Trumps or Atouts");
        case Tarot t -> System.out.println("The Fool or L'Excuse");
      }
    }
  }
}
