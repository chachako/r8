// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.WithAllCfRuntimes;
import com.android.tools.r8.WithAllRuntimesAndApiLevels;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;

@Execution(ExecutionMode.SAME_THREAD)
public class B420228751AltTest extends TestBase {

  @ParameterizedTest
  @WithAllCfRuntimes
  public void testJvm(TestParameters parameters) throws Exception {
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "I", "J");
  }

  @ParameterizedTest
  @WithAllRuntimesAndApiLevels
  public void testR8(TestParameters parameters) throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(I.class, J.class)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "I", "J");
  }

  static class Main {

    public static void main(String[] strArr) {
      System.out.println(new A().foo());
      System.out.println(new B().m());
      System.out.println(new C().m());
    }
  }

  interface I {

    default String m() {
      return "I";
    }
  }

  interface J extends I {

    default String m() {
      return "J";
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class A {

    @NeverInline
    @NeverPropagateValue
    @NoMethodStaticizing
    String foo() {
      return "A";
    }
  }

  static class B extends A implements I {}

  static class C implements J {}
}
