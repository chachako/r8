// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.library.ObjectsEqualsTest.Main;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ObjectEqualsTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "false", //
          "nope", //
          "false", //
          "false", //
          "false", //
          "false", //
          "false", //
          "true");
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMinimumApiLevel().build();
  }

  public ObjectEqualsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());
              mainClassSubject.forAllMethods(
                  method -> {
                    Matcher<MethodSubject> matcher = CodeMatchers.invokesMethodWithName("equals");
                    String name = method.getOriginalMethodName();
                    if (name.endsWith("yes") || name.equals("main")) {
                      matcher = not(matcher);
                    }
                    assertThat(method.getOriginalMethodName(), method, matcher);
                  });
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class Main {
    interface Interface {}

    static class HasOverride implements Interface {
      @Override
      public boolean equals(Object obj) {
        // @NeverInline doesn't work on equals().
        System.currentTimeMillis();
        System.currentTimeMillis();
        System.currentTimeMillis();
        System.currentTimeMillis();
        System.currentTimeMillis();
        return System.currentTimeMillis() < 0;
      }
    }

    static class NoOverride implements Interface {}

    public static void main(String[] args) {
      testNoOverride_yes();
      testNoOverride2_yes();
      testMulitpleUsers_yes();
      testNoOverrideCatchHandlers_yes();
      testLibraryObject();
      testOverride();
      testMaybeNull();
      testInterface();
    }

    @NeverInline
    static void testLibraryObject() {
      System.out.println("".equals(new NoOverride()));
    }

    @NeverInline
    static void testNoOverride_yes() {
      System.out.println(new NoOverride().equals(""));
      // Test no out value.
      new NoOverride().equals("");
    }

    @NeverInline
    static void testNoOverride2_yes() {
      if (new NoOverride().equals(new NoOverride())) {
        System.out.println("yep");
      } else {
        System.out.println("nope");
      }
    }

    @NeverInline
    static void testMulitpleUsers_yes() {
      boolean result = new NoOverride().equals(new NoOverride());
      System.out.println(result);
      if (result) {
        System.out.println("unreached");
      }
    }

    @NeverInline
    static void testNoOverrideCatchHandlers_yes() {
      try {
        System.out.println(new NoOverride().equals(""));
      } catch (RuntimeException e) {
        e.printStackTrace();
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }

    @NeverInline
    static void testOverride() {
      // @NeverInline doesn't work on equals().
      HasOverride obj = new HasOverride();
      System.out.println(
          obj.equals("")
              && obj.equals("")
              && obj.equals("")
              && obj.equals("")
              && obj.equals("")
              && obj.equals("")
              && obj.equals("")
              && obj.equals(""));
    }

    @NeverInline
    static void testMaybeNull() {
      NoOverride inst = System.currentTimeMillis() > 0 ? new NoOverride() : null;
      System.out.println(inst.equals(""));
    }

    @NeverInline
    static void testInterface() {
      Interface[] items = {new NoOverride()};
      Interface first = items[0];
      first.toString();
      System.out.println(first.equals(first));
    }
  }
}
