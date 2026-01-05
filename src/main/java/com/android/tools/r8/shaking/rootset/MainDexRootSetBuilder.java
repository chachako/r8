// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.rootset;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class MainDexRootSetBuilder extends RootSetBuilder {

  MainDexRootSetBuilder(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateAppSubtypingInfo subtypingInfo,
      Iterable<? extends ProguardConfigurationRule> rules) {
    super(appView, subtypingInfo, rules);
  }

  @Override
  boolean isMainDexRootSetBuilder() {
    return true;
  }

  @Override
  public MainDexRootSet evaluateRulesAndBuild(ExecutorService executorService)
      throws ExecutionException {
    // Call the super builder to have if-tests calculated automatically.
    RootSet rootSet = super.evaluateRulesAndBuild(executorService);
    return new MainDexRootSet(
        rootSet.getDependentMinimumKeepInfo(),
        rootSet.reasonAsked,
        rootSet.ifRules,
        rootSet.delayedInterfaceMethodSyntheticBridgeActions);
  }
}
