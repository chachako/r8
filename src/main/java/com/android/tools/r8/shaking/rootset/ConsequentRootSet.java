// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.rootset;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.DependentMinimumKeepInfoCollection;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.InterfaceMethodSyntheticBridgeAction;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// A partial RootSet that becomes live due to the enabled -if rule or the addition of interface
// keep rules.
public class ConsequentRootSet extends RootSetBase {

  ConsequentRootSet(
      DependentMinimumKeepInfoCollection dependentMinimumKeepInfo,
      Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule,
      List<InterfaceMethodSyntheticBridgeAction> delayedInterfaceMethodSyntheticBridgeActions,
      ProgramMethodMap<ProgramMethod> pendingMethodMoveInverse) {
    super(
        dependentMinimumKeepInfo,
        dependentKeepClassCompatRule,
        delayedInterfaceMethodSyntheticBridgeActions,
        pendingMethodMoveInverse);
  }

  public static ConsequentRootSetBuilder builder(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    return new ConsequentRootSetBuilder(appView, enqueuer, enqueuer.getSubtypingInfo());
  }
}
