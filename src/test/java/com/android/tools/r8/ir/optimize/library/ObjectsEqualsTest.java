// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolderAndName;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ObjectsEqualsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
        .build();
  }

  public ObjectsEqualsTest(TestParameters parameters) {
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

              MethodSubject testNonNullArgumentsMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testNonNullArguments");
              assertThat(testNonNullArgumentsMethodSubject, isPresent());
              assertThat(
                  testNonNullArgumentsMethodSubject,
                  not(invokesMethodWithHolderAndName("java.util.Objects", "equals")));
              assertThat(testNonNullArgumentsMethodSubject, not(invokesMethodWithName("equals")));

              MethodSubject testNullAndNullArgumentsMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testNullAndNullArguments");
              assertThat(testNullAndNullArgumentsMethodSubject, isPresent());
              assertThat(
                  testNullAndNullArgumentsMethodSubject, not(invokesMethodWithName("equals")));

              MethodSubject testNullAndNonNullArgumentsMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testNullAndNonNullArguments");
              assertThat(testNullAndNonNullArgumentsMethodSubject, isPresent());
              assertThat(
                  testNullAndNonNullArgumentsMethodSubject, not(invokesMethodWithName("equals")));

              MethodSubject testNullAndMaybeNullArgumentsMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testNullAndMaybeNullArguments");
              assertThat(testNullAndMaybeNullArgumentsMethodSubject, isPresent());
              assertThat(
                  testNullAndMaybeNullArgumentsMethodSubject, not(invokesMethodWithName("equals")));
              assertThat(
                  testNullAndMaybeNullArgumentsMethodSubject,
                  onlyIf(canUseJavaUtilObjectsIsNull(parameters), invokesMethodWithName("isNull")));

              MethodSubject testOverrideAsSecond =
                  mainClassSubject.uniqueMethodWithOriginalName("testOverrideAsSecond");
              assertThat(testOverrideAsSecond, isPresent());
              assertThat(testOverrideAsSecond, not(invokesMethodWithName("equals")));

              MethodSubject testOverrideAsFirst =
                  mainClassSubject.uniqueMethodWithOriginalName("testOverrideAsFirst");
              assertThat(testOverrideAsFirst, isPresent());
              assertThat(testOverrideAsFirst, invokesMethodWithName("equals"));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false", "true", "false", "false", "false", "false");
  }

  static class Main {

    public static void main(String[] args) {
      testNonNullArguments();
      testNullAndNullArguments();
      testNullAndNonNullArguments();
      testNullAndMaybeNullArguments();
      testOverrideAsSecond();
      testOverrideAsFirst();
    }

    @NeverInline
    static void testNonNullArguments() {
      System.out.println(Objects.equals(new Object(), new Object()));
    }

    @NeverInline
    static void testNullAndNullArguments() {
      System.out.println(Objects.equals(null, null));
    }

    @NeverInline
    static void testNullAndNonNullArguments() {
      System.out.println(Objects.equals(null, new Object()));
    }

    @NeverInline
    static void testNullAndMaybeNullArguments() {
      System.out.println(
          Objects.equals(null, System.currentTimeMillis() > 0 ? new Object() : null));
    }

    @NeverInline
    static void testMaybeNullNoOverride() {
      Object a = System.currentTimeMillis() > 0 ? new Object() : null;
      System.out.println(Objects.equals(a, new Object()));
    }

    @NeverInline
    static void testOverrideAsSecond() {
      FinalWithOverride a = System.currentTimeMillis() > 0 ? new FinalWithOverride() : null;
      System.out.println(Objects.equals(new Object(), a));
    }

    @NeverInline
    static void testOverrideAsFirst() {
      FinalWithOverride a = System.currentTimeMillis() > 0 ? new FinalWithOverride() : null;
      System.out.println(Objects.equals(a, new Object()));
    }
  }

  static final class FinalNoOverride {}

  static final class FinalWithOverride {
    @Override
    @NeverInline
    public boolean equals(Object other) {
      return false;
    }
  }
}
