// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterKeepTest extends AtomicFieldUpdaterBase {

  public final boolean keepRule;

  public AtomicFieldUpdaterKeepTest(TestParameters parameters, boolean keepRule) {
    super(parameters);
    this.keepRule = keepRule;
  }

  @Parameters(name = "{0}, keeprule:{1}")
  public static List<Object[]> data() {
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
        .applyIf(
            keepRule,
            testing ->
                testing.addKeepFieldRules(
                    Reference.fieldFromField(testClass.getDeclaredField("myString$FU"))))
        .compile()
        .inspectDiagnosticMessagesIf(
            isOptimizationOn(),
            diagnostics -> {
              if (keepRule) {
                diagnostics.assertInfosMatch(
                    diagnosticMessage(containsString("Cannot instrument")));
              } else {
                diagnostics.assertInfoThatMatches(
                    diagnosticMessage(containsString("Can instrument")));
              }
            })
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              if (isOptimizationOn() && !keepRule) {
                assertThat(method, INVOKES_UNSAFE);
              } else {
                assertThat(method, not(INVOKES_UNSAFE));
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
      TestClass instance = new TestClass();
      System.out.println(myString$FU.get(instance));
    }
  }
}
