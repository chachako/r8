// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumevalues;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeValuesWhenArgumentIsTrueTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumevalues class * {",
            "  static int m(boolean = false) return 0;",
            "  static int m(boolean = true) return 1;",
            "}")
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "1", "-3");
  }

  static class Main {

    static int f;

    public static void main(String[] args) {
      f = -1;
      System.out.println(m(false)); // Should return 0.
      f = -2;
      System.out.println(m(true)); // Should return 1.
      f = -3;
      System.out.println(m(args.length == 0)); // Should return -3.
    }

    static int m(boolean b) {
      return f;
    }
  }
}
