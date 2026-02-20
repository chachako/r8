// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.atomicfieldupdater;

import static org.hamcrest.core.AnyOf.anyOf;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.hamcrest.core.AnyOf;

public class AtomicFieldUpdaterBase extends TestBase {

  protected final TestParameters parameters;

  static AnyOf<MethodSubject> INVOKES_UNSAFE =
      anyOf(
          CodeMatchers.invokesMethodWithHolder("sun.misc.Unsafe"),
          CodeMatchers.invokesMethodWithHolder("jdk.internal.misc.Unsafe"));

  public AtomicFieldUpdaterBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  void enableAtomicFieldUpdaterWithInfo(R8TestBuilder<?, ?, ?> builder) {
    builder
        .addOptionsModification(
            options -> {
              assertFalse(options.enableAtomicFieldUpdaterOptimization);
              options.enableAtomicFieldUpdaterOptimization = true;
              assertFalse(options.testing.enableAtomicFieldUpdaterLogs);
              options.testing.enableAtomicFieldUpdaterLogs = true;
            })
        .applyIf(isOptimizationOn(), R8TestBuilder::allowDiagnosticInfoMessages);
  }

  boolean isOptimizationOn() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }
}
