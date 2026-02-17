// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.BooleanUtils;
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
public class AtomicFieldUpdaterSetTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean dontObfuscate;

  @Parameters(name = "{0}, dontObfuscate:{1}")
  public static List<Object[]> data() {
    // TODO(b/453628974): test all dex and api levels.
    return buildParameters(
        TestParameters.builder()
            .withDexRuntimesStartingFromIncluding(
                Version.V4_4_4) // Unsafe synthetic doesn't work for 4.0.4.
            .withAllApiLevels()
            .build(),
        BooleanUtils.values());
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
        .applyIf(dontObfuscate, TestShrinkerBuilder::addDontObfuscate)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertInfosMatch(
                  diagnosticMessage(containsString("Can instrument")),
                  diagnosticMessage(containsString("Can optimize")),
                  diagnosticMessage(containsString("Can optimize")),
                  // TODO(b/453628974): The field should be removed once nullability analysis is
                  // more precise.
                  diagnosticMessage(containsString("Cannot remove")));
            })
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              assertThat(
                  method,
                  CodeMatchers.invokesMethod(
                      "void",
                      "sun.misc.Unsafe",
                      "putObjectVolatile",
                      ImmutableList.of("java.lang.Object", "long", "java.lang.Object")));
            })
        .run(parameters.getRuntime(), testClass)
        .assertSuccessWithOutputLines("World!");
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
      myString$FU.set(instance, "World!");
      System.out.println(myString$FU.get(instance));
    }
  }
}
