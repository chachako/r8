// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/479862365. */
@RunWith(Parameterized.class)
public class RepackageClassInitializersSameHierarchyDifferentPackagesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClasses(A.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClass = inspector.clazz(A.class.getTypeName());
              assertThat(aClass, isPresent());
              assertEquals("", aClass.getDexProgramClass().getType().getPackageName());

              ClassSubject bClass = inspector.clazz("pkg.B");
              assertThat(bClass, isPresent());
              // TODO(b/479862365): Should have been repackaged.
              assertEquals("pkg", bClass.getDexProgramClass().getType().getPackageName());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private static List<byte[]> getProgramClassFileData() {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(descriptor(B.class), "Lpkg/B;")
            .transform(),
        transformer(B.class).setClassDescriptor("Lpkg/B;").transform());
  }

  public static class Main {

    public static void main(String[] args) {
      A.hello();
      B.world();
    }
  }

  @NoVerticalClassMerging
  public static class A {

    static {
      System.out.print("Hello");
    }

    @NeverInline
    public static void hello() {
      System.out.print("");
    }
  }

  public static class /*pkg.*/ B extends A {

    static {
      System.out.println(", world!");
    }

    @NeverInline
    public static void world() {
      System.out.print("");
    }
  }
}
