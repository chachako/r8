// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaVmNameSystemPropertyPropagationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Dalvik");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        // TODO(b/450537181): Consider making this default in R8.
        .addKeepRules(
            "-assumenosideeffects class java.lang.System {",
            "  java.lang.String getProperty(java.lang.String = \"java.vm.name\")"
                + " return \"Dalvik\";",
            "}")
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
              assertTrue(mainMethod.streamInstructions().anyMatch(i -> i.isConstString("Dalvik")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Dalvik");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(System.getProperty("java.vm.name"));
    }
  }
}
