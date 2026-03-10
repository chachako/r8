// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.attributes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RuntimeInvisibleAnnotationsKeepAttributesExpansionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testWildcards() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .addKeepRules("-keepattributes *Annotation*")
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());
              assertEquals(parameters.isCfRuntime() ? 1 : 0, mainClass.annotations().size());
            });
  }

  @Test
  public void testNoWildcards() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .addKeepRules("-keepattributes RuntimeInvisibleAnnotations")
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());
              assertEquals(1, mainClass.annotations().size());
            });
  }

  @MyAnnotation
  static class Main {

    public static void main(String[] args) {}
  }

  @Retention(RetentionPolicy.CLASS)
  @interface MyAnnotation {}
}
