// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.CfCodeDiagnostics;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstructorInliningInClassFilesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testDefault() throws Exception {
    assumeTrue(parameters.canInitNewInstanceUsingSuperclassConstructor());
    testForD8(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getProgramClassFileData())
        .release()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    allOf(
                        diagnosticType(CfCodeDiagnostics.class),
                        diagnosticMessage(
                            containsString(
                                "Constructor mismatch, expected constructor from "
                                    + B.class.getTypeName())))))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testIgnoreWarning() throws Exception {
    assumeTrue(parameters.canInitNewInstanceUsingSuperclassConstructor());
    testForD8(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addOptionsModification(
            options -> {
              assertFalse(options.getTestingOptions().allowConstructorMismatchInClassFiles);
              options.getTestingOptions().allowConstructorMismatchInClassFiles = true;
            })
        .release()
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private static List<byte[]> getProgramClassFileData() {
    return ImmutableList.of(
        transformer(A.class).removeMethodsWithName("<init>").transform(),
        transformer(B.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(A.class), descriptor(Object.class))
            .transform());
  }

  static class A {

    A() {}
  }

  static class B extends A {

    B() {
      super(); // Changed from calling A.<init> to Object.<init>
      System.out.println("Hello, world!");
    }
  }

  static class Main {

    public static void main(String[] args) {
      new B();
    }
  }
}
