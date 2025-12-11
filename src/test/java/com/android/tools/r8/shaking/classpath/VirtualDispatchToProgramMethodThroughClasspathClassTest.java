// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.classpath;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VirtualDispatchToProgramMethodThroughClasspathClassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, C.class)
        .addClasspathClasses(B.class)
        .addKeepClassAndMembersRules(Main.class)
        // Since B extends A.
        .addKeepClassRules(A.class)
        .setMinApi(parameters)
        .compile()
        .addRunClasspathClasses(B.class)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/418131194): Should be "A", "B", "C".
        .assertSuccessWithOutputLines("A", "A", "A");
  }

  @Test
  public void testR8Partial() throws Exception {
    testForR8Partial(parameters)
        .addR8IncludedClasses(Main.class, A.class, C.class)
        .addR8ExcludedClasses(B.class)
        .addKeepClassAndMembersRules(Main.class)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "C");
  }

  static class Main {

    public static void main(String[] args) {
      getA().m();
      getB().m();
      getC().m();
    }

    // Kept so that R8 does not know anything about the dynamic type.
    static A getA() {
      return new A();
    }

    // Kept so that R8 does not know anything about the dynamic type.
    static A getB() {
      return new B();
    }

    // Kept so that R8 does not know anything about the dynamic type.
    static A getC() {
      return new C();
    }
  }

  static class A {

    public void m() {
      System.out.println("A");
    }
  }

  // Added on the classpath.
  static class B extends A {

    @Override
    public void m() {
      System.out.println("B");
    }
  }

  static class C extends B {

    @Override
    public void m() {
      System.out.println("C");
    }
  }
}
