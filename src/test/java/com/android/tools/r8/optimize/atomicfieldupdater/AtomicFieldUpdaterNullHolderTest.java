// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.atomicfieldupdater;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import org.hamcrest.core.AnyOf;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterNullHolderTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean optimize;

  @Parameters(name = "{0}, optimize:{1}")
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

  private static final Class<? extends Throwable> EXPECTED_EXCEPTION = ClassCastException.class;

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    Class<TestClass> testClass = TestClass.class;
    testForJvm(parameters)
        .addProgramClasses(testClass)
        .run(parameters.getRuntime(), testClass)
        .assertFailureWithErrorThatThrows(EXPECTED_EXCEPTION);
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
        .applyIf(
            !optimize,
            testing ->
                testing.addKeepFieldRules(
                    Reference.fieldFromField(testClass.getDeclaredField("myString$FU"))))
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              if (optimize) {
                diagnostics.assertInfoThatMatches(
                    diagnosticMessage(containsString("Can instrument")));
              } else {
                diagnostics.assertInfosMatch(
                    diagnosticMessage(containsString("Cannot instrument")));
              }
            })
        .inspect(
            inspector -> {
              MethodSubject method = inspector.clazz(testClass).mainMethod();
              AnyOf<MethodSubject> usesUnsafe =
                  anyOf(
                      CodeMatchers.invokesMethodWithHolder("sun.misc.Unsafe"),
                      CodeMatchers.invokesMethodWithHolder("jdk.internal.misc.Unsafe"));
              if (optimize) {
                assertThat(method, usesUnsafe);
              } else {
                assertThat(method, not(usesUnsafe));
              }
            })
        .run(parameters.getRuntime(), testClass)
        .assertFailureWithErrorThatThrows(EXPECTED_EXCEPTION);
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
      TestClass instance;
      if (System.out == null) {
        instance = new TestClass();
      } else {
        instance = null;
      }
      System.out.println(myString$FU.get(instance));
    }
  }
}
