// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.rootset;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.DependentMinimumKeepInfoCollection;
import com.android.tools.r8.shaking.InterfaceMethodSyntheticBridgeAction;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class RootSetBase {

  private final DependentMinimumKeepInfoCollection dependentMinimumKeepInfo;
  public final Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule;
  public final List<InterfaceMethodSyntheticBridgeAction>
      delayedInterfaceMethodSyntheticBridgeActions;
  public final ProgramMethodMap<ProgramMethod> pendingMethodMoveInverse;

  RootSetBase(
      DependentMinimumKeepInfoCollection dependentMinimumKeepInfo,
      Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule,
      List<InterfaceMethodSyntheticBridgeAction> delayedInterfaceMethodSyntheticBridgeActions,
      ProgramMethodMap<ProgramMethod> pendingMethodMoveInverse) {
    this.dependentMinimumKeepInfo = dependentMinimumKeepInfo;
    this.dependentKeepClassCompatRule = dependentKeepClassCompatRule;
    this.delayedInterfaceMethodSyntheticBridgeActions =
        delayedInterfaceMethodSyntheticBridgeActions;
    this.pendingMethodMoveInverse = pendingMethodMoveInverse;
  }

  public Set<ProguardKeepRuleBase> getDependentKeepClassCompatRule(DexType type) {
    return dependentKeepClassCompatRule.get(type);
  }

  public DependentMinimumKeepInfoCollection getDependentMinimumKeepInfo() {
    return dependentMinimumKeepInfo;
  }
}
