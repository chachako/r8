// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static com.android.tools.r8.ir.desugar.LambdaClass.LAMBDA_INSTANCE_FIELD_NAME;
import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isStatic;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingletonStatelessLambdaTest extends TestBase {

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
        .addOptionsModification(
            options -> options.desugarSpecificOptions().createSingletonsForStatelessLambdas = true)
        .collectSyntheticItems()
        .release()
        .compile()
        .inspectWithSyntheticItems(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addOptionsModification(
            options -> options.desugarSpecificOptions().createSingletonsForStatelessLambdas = true)
        .addKeepClassAndMembersRules(Main.class)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private void inspect(CodeInspector inspector, SyntheticItemsTestUtils syntheticItems) {
    ClassSubject lambdaClass = inspector.clazz(syntheticItems.syntheticLambdaClass(Main.class, 0));
    assertThat(lambdaClass, isPresent());
    assertThat(
        lambdaClass.uniqueFieldWithOriginalName(LAMBDA_INSTANCE_FIELD_NAME),
        allOf(isPresent(), isStatic(), isFinal()));
  }

  static class Main {

    public static void main(String[] args) {
      if (get() == get()) {
        run(get());
      }
    }

    static Runnable get() {
      return () -> System.out.println("Hello, world!");
    }

    static void run(Runnable runnable) {
      if (get() == get()) {
        runnable.run();
      }
    }
  }
}
