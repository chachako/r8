// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.classpath;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ResolveToProgramMethodThroughClasspathTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Main.class, I.class, A.class)
        .addClasspathClasses(B.class)
        .addKeepMainRule(Main.class)
        // Keep A and I since B inherits from them.
        .addKeepClassAndDefaultConstructor(A.class)
        .addKeepClassRules(I.class)
        .enableInliningAnnotations()
        .compile()
        .applyIf(
            parameters.isCfRuntime(),
            cr -> cr.addRunClasspathClasses(B.class),
            cr ->
                // Desugar B and add to classpath.
                cr.addRunClasspathFiles(
                    testForD8()
                        .addProgramClasses(B.class)
                        .addClasspathClasses(I.class, A.class)
                        .release()
                        .setMinApi(parameters)
                        .compile()
                        .writeToZip()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "classMethod",
            parameters.canUseDefaultAndStaticInterfaceMethods()
                ? "interfaceMethod"
                : "AbstractMethodError",
            "staticClassMethod");
  }

  @Test
  public void testR8Partial() throws Exception {
    testForR8Partial(parameters)
        .addR8IncludedClasses(Main.class, I.class, A.class)
        .addR8ExcludedClasses(B.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("classMethod", "interfaceMethod", "staticClassMethod");
  }

  static class Main {

    public static void main(String[] args) {
      B b = new B();
      b.classMethod();
      try {
        b.interfaceMethod();
      } catch (AbstractMethodError e) {
        System.out.println("AbstractMethodError");
      }
      B.staticClassMethod();
    }
  }

  interface I {

    @NeverInline
    default void interfaceMethod() {
      System.out.println("interfaceMethod");
    }
  }

  static class A {

    @NeverInline
    void classMethod() {
      System.out.println("classMethod");
    }

    @NeverInline
    static void staticClassMethod() {
      System.out.println("staticClassMethod");
    }
  }

  // Added on the classpath.
  static class B extends A implements I {}
}
