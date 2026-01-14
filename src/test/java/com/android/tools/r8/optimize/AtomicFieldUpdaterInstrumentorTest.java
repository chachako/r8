// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
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
public class AtomicFieldUpdaterInstrumentorTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    Class<TestClass> testClass = TestClass.class;
    testForR8(parameters)
        .addOptionsModification(
            options -> {
              assertFalse(options.enableAtomicFieldUpdaterOptimization);
              options.enableAtomicFieldUpdaterOptimization = true;
            })
        .addProgramClasses(testClass)
        .addKeepMainRule(testClass)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              assertThat(
                  method,
                  CodeMatchers.invokesMethod(
                      "boolean",
                      "java.util.concurrent.atomic.AtomicReferenceFieldUpdater",
                      "compareAndSet",
                      ImmutableList.of(
                          "java.lang.Object", "java.lang.Object", "java.lang.Object")));
            });
  }

  // Corresponding to simple kotlin usage of `atomic("Hello")` via atomicfu.
  public static class TestClass {

    private volatile Object myString;

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater myString$FU;

    static {
      myString$FU =
          AtomicReferenceFieldUpdater.newUpdater(TestClass.class, Object.class, "myString");
    }

    public TestClass() {
      super();
      myString = "Hello";
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
      System.out.println(myString$FU.compareAndSet(new TestClass(), "Hello", "World!"));
    }
  }
}
