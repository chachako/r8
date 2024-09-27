// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PartialCompilationBasicTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  // Test with min API level 24.
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntime(DexVm.Version.V7_0_0)
        .withApiLevel(AndroidApiLevel.N)
        .build();
  }

  @Test
  public void runTestClassAIsCompiledWithD8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(A.class, B.class, Main.class)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.r8PartialCompilationOptions.enabled = true;
              // Run R8 on all classes except class A.
              options.r8PartialCompilationOptions.isR8 =
                  name -> !name.equals(A.class.getTypeName());
            })
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class, getClass().getTypeName())
        .assertSuccessWithOutputLines("Instantiated", "Not instantiated");
  }

  @Test
  public void runTestClassBIsCompiledWithD8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(A.class, B.class, Main.class)
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.r8PartialCompilationOptions.enabled = true;
              // Run R8 on all classes except class A.
              options.r8PartialCompilationOptions.isR8 =
                  name -> !name.equals(B.class.getTypeName());
            })
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), isAbsent());
              assertThat(inspector.clazz(B.class), isPresent());
            })
        .run(parameters.getRuntime(), Main.class, getClass().getTypeName())
        .assertSuccessWithOutputLines("Not instantiated", "Instantiated");
  }

  public static class A {}

  public static class B {}

  public static class Main {

    public static void main(String[] args) throws Exception {
      // Instantiate class A.
      try {
        Class.forName(new String(args[0] + "$" + new String(new byte[] {65})));
        System.out.println("Instantiated");
      } catch (ClassNotFoundException e) {
        System.out.println("Not instantiated");
      }
      // Instantiate class B.
      try {
        Class.forName(new String(args[0] + "$" + new String(new byte[] {66})));
        System.out.println("Instantiated");
      } catch (ClassNotFoundException e) {
        System.out.println("Not instantiated");
      }
    }
  }
}
