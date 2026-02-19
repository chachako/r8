// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepRuleNegatedNameTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepRules("-keep class " + Main.class.getTypeName() + " {", "  void !foo();", "}")
        .setMinApi(AndroidApiLevel.N)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());
              assertThat(mainClass.uniqueMethodWithOriginalName("foo"), isAbsent());
              assertThat(mainClass.uniqueMethodWithOriginalName("bar"), isPresent());
              assertThat(mainClass.uniqueMethodWithOriginalName("baz"), isPresent());
            });
  }

  @Test
  public void testIfRule() throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepRules(
            // Keep foo.
            "-keep class " + Main.class.getTypeName() + " {",
            "  void foo();",
            "}",
            // If there exists a method not ending in "ar", then keep baz().
            "-if class * {",
            "  void !*ar();",
            "}",
            "-keep class <1> {",
            "  void baz();",
            "}")
        .setMinApi(AndroidApiLevel.N)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());
              assertThat(mainClass.uniqueMethodWithOriginalName("foo"), isPresent());
              assertThat(mainClass.uniqueMethodWithOriginalName("bar"), isAbsent());
              assertThat(mainClass.uniqueMethodWithOriginalName("baz"), isPresent());
            });
  }

  @Test
  public void testIfRuleWithBackReference() throws Exception {
    assertFailsCompilation(
        () ->
            testForR8(Backend.DEX)
                .addInnerClasses(getClass())
                .addKeepRules(
                    // Keep foo.
                    "-keep class " + Main.class.getTypeName() + " {",
                    "  void foo();",
                    "}",
                    // If there exists a method not ending in "ar", then keep what's by the
                    // wildcard?!
                    "-if class * {",
                    "  void !*ar();",
                    "}",
                    "-keep class <1> {",
                    "  void <2>();",
                    "}")
                .setMinApi(AndroidApiLevel.N)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics
                            .assertOnlyErrors()
                            .assertErrorsMatch(
                                allOf(
                                    diagnosticType(StringDiagnostic.class),
                                    diagnosticMessage(
                                        equalTo(
                                            "Wildcard <2> is invalid (cannot reference negated"
                                                + " matches)."))))));
  }

  static class Main {

    public static void foo() {}

    public static void bar() {}

    public static void baz() {}
  }
}
