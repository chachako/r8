// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keep;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.keep.KeepMembersAnnotatedByLibraryTest.OtherClass;
import com.android.tools.r8.shaking.keep.KeepMembersAnnotatedByLibraryTest.TestClass;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepMembersAnnotatedByProgramTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testKeep() throws Exception {
    runTest(
        "-keep class * { @**MyAnnotation <methods>; }",
        (testClass, otherClass) -> {
          assertThat(testClass, isPresent());
          assertThat(testClass.uniqueMethodWithOriginalName("test"), isPresent());
          assertThat(otherClass, isPresent());
        });
  }

  @Test
  public void testKeepClassMembers() throws Exception {
    runTest(
        "-keep class * -keepclassmembers class * { @**MyAnnotation <methods>; }",
        (testClass, otherClass) -> {
          assertThat(testClass, isPresent());
          assertThat(testClass.uniqueMethodWithOriginalName("test"), isPresent());
          assertThat(otherClass, isPresent());
        });
  }

  @Test
  public void testKeepClassesWithMembers() throws Exception {
    runTest(
        "-keepclasseswithmembers class * { @**MyAnnotation <methods>; }",
        (testClass, otherClass) -> {
          assertThat(testClass, isPresent());
          assertThat(testClass.uniqueMethodWithOriginalName("test"), isPresent());
          assertThat(otherClass, isAbsent());
        });
  }

  private void runTest(String rule, BiConsumer<ClassSubject, ClassSubject> classesConsumer)
      throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepRules(rule)
        .setMinApi(AndroidApiLevel.getDefault())
        .compile()
        .inspect(
            inspector ->
                classesConsumer.accept(
                    inspector.clazz(TestClass.class), inspector.clazz(OtherClass.class)));
  }

  static class TestClass {

    @MyAnnotation
    public static void test() {}
  }

  static class OtherClass {}

  @interface MyAnnotation {}
}
