// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumevalues;

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
public class AssumeValuesReturnConstantStringTest extends TestBase {

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
            "-assumevalues class * {",
            "  java.lang.String greeting() return \"Hello, world!\";",
            "  java.lang.String newline() return \"\\n\";",
            "}")
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(StringUtils.unixLines("Hello, world!"));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.print(greeting());
      System.out.print(newline());
    }

    static String greeting() {
      return null;
    }

    static String newline() {
      return null;
    }
  }
}
