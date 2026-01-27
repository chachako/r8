// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.apimodel.ApiModelMockClassTest.TestClass;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineMethodAndStubClassTest extends TestBase {

  private static class ApiLevelTestConfiguration {
    private final AndroidApiLevel libraryClassLevel;
    private final AndroidApiLevel libraryMethodLevel;

    private ApiLevelTestConfiguration(
        AndroidApiLevel libraryClassLevel, AndroidApiLevel libraryMethodLevel) {
      assert libraryClassLevel.getMinor() == 0; // Test can only handle minor of 0 here.
      this.libraryClassLevel = libraryClassLevel;
      this.libraryMethodLevel = libraryMethodLevel;
    }

    @Override
    public String toString() {
      return "ApiLevelTestConfiguration{"
          + "libraryClassLevel="
          + libraryClassLevel
          + ", libraryMethodLevel="
          + libraryMethodLevel
          + '}';
    }
  }

  @Parameter public TestParameters parameters;

  @Parameter(1)
  public ApiLevelTestConfiguration apiLevels;

  @Parameters(name = "{0} {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        ImmutableList.of(
            new ApiLevelTestConfiguration(AndroidApiLevel.M, AndroidApiLevel.Q),
            new ApiLevelTestConfiguration(AndroidApiLevel.BAKLAVA, AndroidApiLevel.BAKLAVA_1)));
  }

  public Method apiMethod() throws Exception {
    return LibraryClass.class.getDeclaredMethod("foo");
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class, TestClass.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        // TODO(b/213552119): Remove when enabled by default.
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableGlobalSyntheticCheck)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        // We only model the class and not the default initializer, otherwise we outline the new
        // instance call and remove the last reference in non-outlined code.
        .apply(setMockApiLevelForClass(LibraryClass.class, apiLevels.libraryClassLevel))
        .apply(setMockApiLevelForMethod(apiMethod(), apiLevels.libraryMethodLevel));
  }

  public boolean addToBootClasspath() {
    return parameters.isDexRuntime()
        && parameters
            .getRuntime()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(apiLevels.libraryClassLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(
            parameters.getRuntime(),
            Main.class,
            Integer.toString(apiLevels.libraryClassLevel.getLevel()))
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(
            parameters.getRuntime(),
            Main.class,
            Integer.toString(apiLevels.libraryClassLevel.getLevel()))
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(
            parameters.getRuntime(),
            Main.class,
            Integer.toString(apiLevels.libraryClassLevel.getLevel()))
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) throws Exception {
    assertThat(inspector.clazz(LibraryClass.class), isAbsent());
    verifyThat(inspector, parameters, apiMethod())
        .isOutlinedFromUntil(
            Main.class.getDeclaredMethod("main", String[].class), apiLevels.libraryMethodLevel);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevels.libraryClassLevel)) {
      runResult.assertSuccessWithOutputLines("LibraryClass::foo");
    } else {
      runResult.assertSuccessWithOutputLines("Hello World");
    }
  }

  // Only present from api level libraryClassLevel.
  public static class LibraryClass {

    // Only present from api level libraryMethodLevel.
    public static void foo() {
      System.out.println("LibraryClass::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= Integer.parseInt(args[0])) {
        new LibraryClass().foo();
      } else {
        System.out.println("Hello World");
      }
    }
  }
}
