// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
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
public class AtomicFieldUpdaterDontObfuscateTest extends AtomicFieldUpdaterBase {

  public final boolean dontObfuscate;

  public AtomicFieldUpdaterDontObfuscateTest(TestParameters parameters, boolean dontObfuscate) {
    super(parameters);
    this.dontObfuscate = dontObfuscate;
  }

  @Parameters(name = "{0}, dontObfuscate:{1}")
  public static List<Object[]> data() {
    // TODO(b/453628974): test all dex and api levels.
    return buildParameters(
        TestParameters.builder().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testR8() throws Exception {
    Class<TestClass> testClass = TestClass.class;
    testForR8(parameters)
        .apply(this::enableAtomicFieldUpdaterWithInfo)
        .addProgramClasses(testClass)
        .addKeepMainRule(testClass)
        .applyIf(dontObfuscate, TestShrinkerBuilder::addDontObfuscate)
        .compile()
        .inspectDiagnosticMessagesIf(
            isOptimizationOn(),
            diagnostics -> {
              List<Matcher<Diagnostic>> matchers = new ArrayList<>(7);
              matchers.add(diagnosticMessage(containsString("Can instrument")));
              matchers.add(diagnosticMessage(containsString("Can optimize")));
              matchers.add(diagnosticMessage(containsString("Can optimize")));
              matchers.add(diagnosticMessage(containsString("Can optimize")));
              // TODO(b/453628974): The field should be removed once nullability analysis is
              // more precise.
              matchers.add(diagnosticMessage(containsString("Cannot remove")));
              diagnostics.assertInfosMatch(matchers);
            })
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              if (isOptimizationOn()) {
                assertThat(
                    method,
                    not(
                        CodeMatchers.invokesMethodWithHolder(
                            "java.util.concurrent.atomic.AtomicReferenceFieldUpdater")));
              } else {
                assertThat(method, not(INVOKES_UNSAFE));
              }
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutputLines("Hello!!");
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
      TestClass instance = new TestClass();
      Object old = myString$FU.getAndSet(instance, "World");
      myString$FU.set(instance, old.toString() + "!!");
      System.out.println(myString$FU.get(instance));
    }
  }
}
