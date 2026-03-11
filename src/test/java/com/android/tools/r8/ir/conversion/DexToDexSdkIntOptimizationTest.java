// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexToDexSdkIntOptimizationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().build();
  }

  @Test
  public void test() throws Exception {
    Path unoptimizedCompileResult =
        testForD8()
            .addProgramClassFileData(getProgramClassFileData())
            .release()
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .inspect(inspector -> inspect(inspector, false))
            .writeToZip();

    // Compiling with D8 should normally not perform dex-to-dex optimizations.
    testForD8()
        .addProgramFiles(unoptimizedCompileResult)
        .release()
        .setMinApi(AndroidApiLevel.N)
        .compile()
        .inspect(inspector -> inspect(inspector, false));

    // Dex-dto-dex optimizations should optimize the code.
    testForD8()
        .addProgramFiles(unoptimizedCompileResult)
        .addOptionsModification(
            options -> {
              assertFalse(options.enableDexToDexCodeOptimizations);
              options.enableDexToDexCodeOptimizations = true;
            })
        .release()
        .setMinApi(AndroidApiLevel.N)
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  private void inspect(CodeInspector inspector, boolean optimized) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    MethodSubject mainMethod = mainClass.mainMethod();
    MethodSubject aboveMethod = mainClass.uniqueMethodWithFinalName("above");
    MethodSubject belowMethod = mainClass.uniqueMethodWithFinalName("below");
    assertThat(mainMethod, invokesMethod(aboveMethod));
    assertThat(mainMethod, notIf(invokesMethod(belowMethod), optimized));
  }

  private static Collection<byte[]> getProgramClassFileData() throws NoSuchFieldException {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(VERSION.class), "Landroid/os/Build$VERSION;")
            .transform(),
        transformer(VERSION.class)
            .setClassDescriptor("Landroid/os/Build$VERSION;")
            .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT"), AccessFlags::setFinal)
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      if (VERSION.SDK_INT >= 24) {
        above();
      } else {
        below();
      }
    }

    private static void above() {
      System.out.println("Above");
    }

    private static void below() {
      System.out.println("Below");
    }
  }

  public static class /*android.os.Build$*/ VERSION {

    public static /*final*/ int SDK_INT = -1;
  }
}
