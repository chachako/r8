// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumevalues;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeValuesWithConditionSatisfiedAfterInliningTest extends TestBase {

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
            "  java.lang.String greeting(* = \"a\") return \"Hello\";",
            "  java.lang.String greeting(* = \"b\") return \", world!\";",
            "}")
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.print(greeting(a()));
      System.out.println(greeting(b()));
    }

    @NeverPropagateValue
    static String a() {
      return "a";
    }

    @NeverInline
    static String b() {
      return "b";
    }

    static String greeting(String unused) {
      return null;
    }
  }
}
