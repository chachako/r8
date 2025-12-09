// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.assume;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.shaking.assume.AssumeInfoCollection;

public class AssumeInfoLookup {

  public static AssumeInfo lookupAssumeInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      InvokeMethod invoke,
      ProgramMethod context,
      SingleResolutionResult<?> resolutionResult,
      DexClassAndMethod singleTarget) {
    AssumeInfoCollection assumeInfoCollection = appView.getAssumeInfoCollection();
    DexClassAndMethod resolvedMethod = resolutionResult.getResolutionPair();
    AssumeInfo resolvedMethodLookup =
        assumeInfoCollection.getMethod(resolvedMethod, invoke, context);
    AssumeInfo singleTargetLookup =
        singleTarget != null
                && singleTarget.getReference().isNotIdenticalTo(resolvedMethod.getReference())
            ? assumeInfoCollection.getMethod(singleTarget, invoke, context)
            : null;
    return singleTargetLookup != null
        ? resolvedMethodLookup.meet(singleTargetLookup)
        : resolvedMethodLookup;
  }
}
