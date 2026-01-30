// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
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
        .compile()
        .inspectDiagnosticMessages(
            diagnostics -> {
              assertEquals(2, diagnostics.getInfos().size());
              Diagnostic diagnostic = diagnostics.getInfos().get(0);
              List<String> diagnosticLines =
                  StringUtils.splitLines(diagnostic.getDiagnosticMessage());
              for (String message : diagnosticLines) {
                assertTrue(
                    "Does not contain 'Can instrument': " + message,
                    message.contains("Can instrument"));
              }
              assertEquals(1, diagnosticLines.size());
              diagnostic = diagnostics.getInfos().get(1);
              diagnosticLines = StringUtils.splitLines(diagnostic.getDiagnosticMessage());
              for (String message : diagnosticLines) {
                assertTrue(
                    "Does not contain 'Can optimize': " + message,
                    message.contains("Can optimize"));
              }
              assertEquals(1, diagnosticLines.size());
            })
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              assertThat(
                  method,
                  CodeMatchers.invokesMethod(
                      "boolean",
                      "sun.misc.Unsafe",
                      "compareAndSwapObject",
                      ImmutableList.of(
                          "java.lang.Object", "long", "java.lang.Object", "java.lang.Object")));
            })
        .run(parameters.getRuntime(), TestClass.class)
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
