// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ToCharArrayTest extends TestBase {

  public static class Main {
    public static void main(String[] args) {
      char[] chars = "ABC".toCharArray();
      escape(chars);
      char[] emptyChars = "".toCharArray();
      escape(emptyChars);
    }

    @NeverInline
    public static void escape(char[] chars) {
      System.out.println("Length: " + chars.length);
      for (char c : chars) {
        System.out.print(c);
      }
      System.out.println();
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public ToCharArrayTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Length: 3", "ABC", "Length: 0", "")
        .inspect(
            inspector -> {
              if (parameters.isCfRuntime()) {
                return;
              }
              ClassSubject mainClass = inspector.clazz(Main.class);
              MethodSubject mainMethod = mainClass.mainMethod();
              assertEquals(
                  0,
                  mainMethod
                      .streamInstructions()
                      .filter(
                          i -> i.isInvoke() && i.getMethod().name.toString().equals("toCharArray"))
                      .count());
              assertEquals(2, mainMethod.streamInstructions().filter(i -> i.isNewArray()).count());
              assertEquals(
                  1, mainMethod.streamInstructions().filter(i -> i.isFilledNewArrayData()).count());
            });
  }
}
