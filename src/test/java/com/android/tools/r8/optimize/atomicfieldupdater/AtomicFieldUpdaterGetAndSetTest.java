// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterGetAndSetTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(b/453628974): test all dex and api levels.
    return TestParameters.builder()
        .withDexRuntimesStartingFromIncluding(
            Version.V4_4_4) // Unsafe synthetic doesn't work for 4.0.4.
        .withAllApiLevels()
        .build();
  }

  @Test
  public void testR8() throws Exception {
    Class<TestClass> testClass = TestClass.class;
    testForR8(parameters)
        .addOptionsModification(
            options -> {
              assertFalse(options.enableAtomicFieldUpdaterOptimization);
              options.enableAtomicFieldUpdaterOptimization = true;
              assertFalse(options.testing.enableAtomicFieldUpdaterLogs);
              options.testing.enableAtomicFieldUpdaterLogs = true;
            })
        .addProgramClasses(testClass)
        .allowDiagnosticInfoMessages()
        .addKeepMainRule(testClass)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    diagnosticMessage(containsString("Can instrument")),
                    diagnosticMessage(containsString("Can optimize")),
                    // TODO(b/453628974): The field should be removed once nullability analysis is
                    // more precise.
                    diagnosticMessage(containsString("Cannot remove"))))
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              boolean isGetAndSetDefined = parameters.canUseUnsafeGetAndSet();
              if (isGetAndSetDefined) {
                assertThat(
                    method,
                    CodeMatchers.invokesMethod(
                        "java.lang.Object",
                        "sun.misc.Unsafe",
                        "getAndSetObject",
                        ImmutableList.of("java.lang.Object", "long", "java.lang.Object")));
              } else {
                ClassSubject helper =
                    inspector.clazz(
                        SyntheticItemsTestUtils.syntheticAtomicFieldUpdaterHelper(TestClass.class));
                assertThat(helper, isPresent());
                assertThat(
                    method,
                    CodeMatchers.invokesMethod(helper.uniqueMethodWithOriginalName("getAndSet")));
              }
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutputLines("Hello");
  }

  // Corresponding to simple kotlin usage of `atomic("Hello")` via atomicfu.
  public static class TestClass {

    private volatile Object myString;

    private static final AtomicReferenceFieldUpdater<TestClass, Object> myString$FU;

    static {
      myString$FU =
          AtomicReferenceFieldUpdater.newUpdater(TestClass.class, Object.class, "myString");
    }

    public TestClass() {
      super();
      myString = "Hello";
    }

    public static void main(String[] args) {
      System.out.println(myString$FU.getAndSet(new TestClass(), "World!"));
    }
  }
}
