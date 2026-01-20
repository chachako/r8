// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleOnAbstractInterfaceMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testInterfaceMethod() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-if class " + I.class.getTypeName() + " {",
            "  void m();",
            "}",
            "-keep class " + Unused.class.getTypeName())
        .compile()
        // TODO(b/475856398): Should be present.
        .inspect(inspector -> assertThat(inspector.clazz(Unused.class), isAbsent()));
  }

  @Test
  public void testImplementationMethod() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-if class * implements " + I.class.getTypeName() + " {",
            "  void m();",
            "}",
            "-keep class " + Unused.class.getTypeName())
        .compile()
        .inspect(inspector -> assertThat(inspector.clazz(Unused.class), isPresent()));
  }

  static class Main {

    public static void main(String[] args) {
      I i = new A();
      i.m();
    }
  }

  interface I {

    void m();
  }

  static class A implements I {

    @Override
    public void m() {
      System.out.println("A.m()");
    }
  }

  static class Unused {}
}
