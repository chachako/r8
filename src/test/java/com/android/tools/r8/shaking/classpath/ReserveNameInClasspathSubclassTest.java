// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.classpath;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReserveNameInClasspathSubclassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "A", "A");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addClasspathClasses(B.class)
        .addKeepClassAndMembersRules(Main.class)
        // Since B extends A.
        .addKeepClassAndDefaultConstructor(A.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertEquals("b", aClassSubject.uniqueMethodWithOriginalName("b").getFinalName());
            })
        .addRunClasspathClasses(B.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "A", "A");
  }

  @Test
  public void testPartial() throws Exception {
    parameters.assumeDexRuntime();
    testForR8Partial(parameters)
        .addR8IncludedClasses(Main.class, A.class)
        .addR8ExcludedClasses(B.class)
        .addKeepClassAndMembersRules(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertEquals("b", aClassSubject.uniqueMethodWithOriginalName("b").getFinalName());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "A", "A");
  }

  static class Main {

    public static void main(String[] args) {
      new A().b();
      new B().b();
      (System.currentTimeMillis() > 0 ? new B() : new A()).b();
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    @NoMethodStaticizing
    public void b() {
      System.out.println("A");
    }
  }

  // Added on the classpath.
  static class B extends A {

    public void a() {
      System.out.println("B");
    }
  }
}
