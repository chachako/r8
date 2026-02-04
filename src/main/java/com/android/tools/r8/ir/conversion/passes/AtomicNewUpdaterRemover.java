// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeUtils;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.conversion.MethodProcessor;

public class AtomicNewUpdaterRemover {

  public static void run(
      AppView<?> appView, MethodProcessor methodProcessor, DexEncodedMethod method, IRCode code) {
    if (!appView.options().enableAtomicFieldUpdaterOptimization) {
      return;
    }
    if (!methodProcessor.isPostMethodProcessor()) {
      return;
    }
    if (!method.isClassInitializer()) {
      return;
    }
    var instrumentations = appView.getAtomicFieldUpdaterInstrumentorInfo().getInstrumentations();
    if (!instrumentations.containsKey(method.getHolderType())) {
      return;
    }
    var updaterFields = instrumentations.get(method.getHolderType());
    var it = code.instructionListIterator();
    while (it.hasNext()) {
      StaticPut staticPut = it.nextUntil(Instruction::isStaticPut);
      if (staticPut == null) {
        continue;
      }
      if (!updaterFields.containsKey(staticPut.getField())) {
        continue;
      }
      if (!staticPut.canBeDeadCode(appView, code).isDeadIfOutValueIsDead()) {
        reportFailure(appView, method);
        continue;
      }
      IRCodeUtils.removeInstructionAndTransitiveInputsIfNotUsed(staticPut);
      reportSuccess(appView, method);
    }
  }

  private static void reportSuccess(AppView<?> appView, DexEncodedMethod method) {
    if (appView.testing().enableAtomicFieldUpdaterLogs) {
      appView
          .reporter()
          .info(
              "Can remove AtomicFieldUpdater initialization ("
                  + method.getReference().toSourceString()
                  + ")");
    }
  }

  private static void reportFailure(AppView<?> appView, DexEncodedMethod method) {
    if (appView.testing().enableAtomicFieldUpdaterLogs) {
      appView
          .reporter()
          .info(
              "Cannot remove AtomicFieldUpdater initialization ("
                  + method.getReference().toSourceString()
                  + ")");
    }
  }
}
