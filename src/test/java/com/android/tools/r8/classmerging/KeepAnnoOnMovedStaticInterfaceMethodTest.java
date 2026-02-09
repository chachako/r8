// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepAnnoOnMovedStaticInterfaceMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepAnnoOnMovedStaticInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8FullTestBuilder testBuilder =
        testForR8(parameters.getBackend())
            .setMinApi(parameters)
            .addInnerClasses(getClass())
            .addKeepMainRule(TestClass.class)
            .addKeepClassAndMembersRules(MyAnnotation.class)
            .enableInliningAnnotations()
            .addKeepRuntimeVisibleAnnotations()
            .enableExperimentalKeepAnnotations(R8TestBuilder.KeepAnnotationLibrary.LEGACY)
            .addVerticallyMergedClassesInspector(
                inspector -> inspector.assertMergedIntoSubtype(MergeSource.class));
    try {
      testBuilder.compile();
    } catch (CompilationFailedException e) {
      assertFalse(parameters.hasDefaultInterfaceMethodsSupport());
    }
  }

  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  public @interface MyAnnotation {}

  interface InterfaceWithMethodMovedByDesugaring {
    @MyAnnotation
    @NeverInline
    static void hello(MergeSource ms) {
      System.out.println(ms);
    }
  }

  @KeepEdge(
      consequences =
          @KeepTarget(
              kind = KeepItemKind.ONLY_METHODS,
              methodAnnotatedByClassConstant = MyAnnotation.class,
              constraints = {}))
  static class TestClass {
    public static void main(String[] args) throws Exception {
      MergeSource ms = new MergeTarget();
      InterfaceWithMethodMovedByDesugaring.hello(ms);
      System.out.println(InterfaceWithMethodMovedByDesugaring.class);
    }
  }

  public static class MergeSource {

    @Override
    public String toString() {
      return "Hello";
    }
  }

  public static class MergeTarget extends MergeSource {

    @Override
    public String toString() {
      return ", world!";
    }
  }
}
