// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.constantdynamic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HierarchyConstantDynamicTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("true", "true", "true");
  private static final Class<?> MAIN_CLASS = A.class;

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    assumeTrue(parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11));
    assumeTrue(parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));

    testForJvm()
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), A.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());

    testForD8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_CLASS)
        // TODO(b/198142613): There should not be a warnings on class references which are
        //  desugared away.
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            b -> b.addDontWarn("java.lang.invoke.MethodHandles$Lookup"))
        // TODO(b/198142625): Support CONSTANT_Dynamic output for class files.
        .applyIf(
            parameters.isCfRuntime(),
            r -> {
              assertThrows(
                  CompilationFailedException.class,
                  () ->
                      r.compileWithExpectedDiagnostics(
                          diagnostics -> {
                            diagnostics.assertOnlyErrors();
                            diagnostics.assertErrorsMatch(
                                diagnosticMessage(
                                    containsString(
                                        "Unsupported dynamic constant (not desugaring)")));
                          }));
            },
            r ->
                r.run(parameters.getRuntime(), MAIN_CLASS)
                    .assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  private Collection<byte[]> getTransformedClasses() throws IOException {
    return ImmutableList.of(
        transformer(A.class)
            .setVersion(CfVersion.V11)
            .transformConstStringToConstantDynamic(
                "condy1", A.class, "myConstant", false, "constantName", Object.class)
            .transform(),
        transformer(B.class)
            .setVersion(CfVersion.V11)
            .transformConstStringToConstantDynamic(
                "condy1", B.class, "myConstant", false, "constantName", Object.class)
            .transform());
  }

  // The name of the bootstrap method is the same in both A and B.
  public static class A {

    public static Object f() {
      return "condy1"; // Will be transformed to Constant_DYNAMIC.
    }

    public static void main(String[] args) {
      System.out.println(f() != null);
      System.out.println(B.f() != null);
      System.out.println(f() != B.f());
    }

    private static Object myConstant(MethodHandles.Lookup lookup, String name, Class<?> type) {
      return new Object();
    }
  }

  public static class B extends A {

    public static Object f() {
      return "condy1"; // Will be transformed to Constant_DYNAMIC.
    }

    private static Object myConstant(MethodHandles.Lookup lookup, String name, Class<?> type) {
      return new Object();
    }
  }
}
