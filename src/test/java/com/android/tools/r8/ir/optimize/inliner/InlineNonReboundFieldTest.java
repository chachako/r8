// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ir.optimize.inliner.testclasses.Greeting;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

/** Regression test for b/128604123. */
public class InlineNonReboundFieldTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Greeter: Hello world!");
    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addProgramClasses(
                TestClass.class, Greeter.class, Greeting.class, Greeting.getGreetingBase())
            .addKeepMainRule(TestClass.class)
            .enableNeverClassInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject greeterSubject = inspector.clazz(Greeter.class);
    assertThat(greeterSubject, isPresent());

    // Verify that greet() is not inlined into main() -- that would lead to illegal access errors
    // since main() does not have access to the GreetingBase.greeting field.
    assertThat(greeterSubject.uniqueMethodWithOriginalName("greet"), isPresent());

    // The method greetInternal() should be inlined into greet() since it has a single call site and
    // nothing prevents it from being inlined.
    assertThat(greeterSubject.uniqueMethodWithOriginalName("greetInternal"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      new Greeter("Hello world!").greet();
    }
  }

  @NeverClassInline
  static class Greeter extends Greeting {

    static String TAG = "Greeter";

    Greeter(String greeting) {
      this.greeting = greeting;
    }

    void greet() {
      greetInternal();
    }

    private void greetInternal() {
      System.out.println(TAG + ": " + greeting);
    }
  }
}
