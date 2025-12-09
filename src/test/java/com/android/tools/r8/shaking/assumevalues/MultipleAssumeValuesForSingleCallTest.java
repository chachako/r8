// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumevalues;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringDiagnostic;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MultipleAssumeValuesForSingleCallTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    assertFailsCompilation(
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addKeepRules(
                    "-assumevalues class * { * greeting(* = @NonNull) return \"Hello, world!\"; }",
                    "-assumevalues class * { * greeting(* = \"foo\") return \"Goodbye, world!\"; }")
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics
                            .assertOnlyErrors()
                            .assertErrorsMatch(
                                allOf(
                                    diagnosticType(StringDiagnostic.class),
                                    diagnosticMessage(
                                        containsString(
                                            "Call to "
                                                + descriptor(Main.class)
                                                + "->greeting(Ljava/lang/Object;)Ljava/lang/String;"
                                                + " in "
                                                + descriptor(Main.class)
                                                + "->main("
                                                + "[Ljava/lang/String;)V matches different assume "
                                                + "rules"))))));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(greeting("foo"));
    }

    static String greeting(Object unused) {
      return null;
    }
  }
}
