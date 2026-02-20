// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterCompareAndSetTest extends AtomicFieldUpdaterBase {

  public AtomicFieldUpdaterCompareAndSetTest(TestParameters parameters) {
    super(parameters);
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    Class<TestClass> testClass = TestClass.class;
    boolean isCompareAndSetBackported =
        isOptimizationOn() && parameters.getApiLevel().isLessThan(AndroidApiLevel.Sv2);
    testForR8(parameters)
        .apply(this::enableAtomicFieldUpdaterWithInfo)
        .addProgramClasses(testClass)
        .addKeepMainRule(testClass)
        .compile()
        .inspectDiagnosticMessagesIf(
            isOptimizationOn(),
            diagnostics -> {
              List<Matcher<Diagnostic>> matchers = new ArrayList<>(4);
              matchers.add(diagnosticMessage(containsString("Can instrument")));
              matchers.add(diagnosticMessage(containsString("Can optimize")));
              // TODO(b/453628974): The field should be removed once nullability analysis is
              // more precise.
              matchers.add(diagnosticMessage(containsString("Cannot remove")));
              if (isCompareAndSetBackported) {
                // Another call is inserted by the inlined backport.
                matchers.add(diagnosticMessage(containsString("Can optimize")));
              }
              diagnostics.assertInfosMatch(matchers);
            })
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              if (isOptimizationOn()) {
                assertThat(
                    method,
                    CodeMatchers.invokesMethodWithHolderAndName(
                        "sun.misc.Unsafe", "compareAndSwapObject"));
              } else {
                assertThat(
                    method,
                    CodeMatchers.invokesMethodWithHolderAndName(
                        "java.util.concurrent.atomic.AtomicReferenceFieldUpdater",
                        "compareAndSet"));
              }
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutputLines("true");
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
      System.out.println(myString$FU.compareAndSet(new TestClass(), "Hello", "World!"));
    }
  }
}
