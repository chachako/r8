// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.whyareyoukeeping;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WhyAreYouKeepingIgnoreAllowShrinkingRulesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            // This rule should not be the reason for keeping Main.m since it allows shrinking.
            "-keep,allowshrinking class * { <methods>; }",
            "-whyareyoukeeping class " + Main.class.getTypeName() + " { static void m(); }")
        .collectStdout()
        .compile()
        .assertStdoutThatMatches(
            containsString(
                StringUtils.lines(
                    "void com.android.tools.r8.shaking.whyareyoukeeping.WhyAreYouKeepingIgnoreAllowShrinkingRulesTest$Main.m()",
                    "|- is invoked from:",
                    "|  void"
                        + " com.android.tools.r8.shaking.whyareyoukeeping.WhyAreYouKeepingIgnoreAllowShrinkingRulesTest$Main.main(java.lang.String[])",
                    "|- is referenced in keep rule:",
                    "|  -keep class"
                        + " com.android.tools.r8.shaking.whyareyoukeeping.WhyAreYouKeepingIgnoreAllowShrinkingRulesTest$Main"
                        + " { public static void main(java.lang.String[]); }")));
  }

  static class Main {

    public static void main(String[] args) {
      m();
    }

    static void m() {
      System.out.println("m");
    }
  }
}
