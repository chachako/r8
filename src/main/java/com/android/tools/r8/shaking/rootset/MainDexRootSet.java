// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.rootset;

import static com.android.tools.r8.utils.LensUtils.rewriteAndApplyIfNotPrimitiveType;
import static java.util.Collections.emptyMap;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.DependentMinimumKeepInfoCollection;
import com.android.tools.r8.shaking.InterfaceMethodSyntheticBridgeAction;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardIfRule;
import com.android.tools.r8.utils.PredicateSet;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MainDexRootSet extends RootSet {

  public MainDexRootSet(
      DependentMinimumKeepInfoCollection dependentMinimumKeepInfo,
      ImmutableList<DexReference> reasonAsked,
      Set<ProguardIfRule> ifRules,
      List<InterfaceMethodSyntheticBridgeAction> delayedInterfaceMethodSyntheticBridgeActions) {
    super(
        dependentMinimumKeepInfo,
        reasonAsked,
        Collections.emptySet(),
        Collections.emptySet(),
        PredicateSet.empty(),
        emptyMap(),
        emptyMap(),
        Collections.emptySet(),
        ifRules,
        delayedInterfaceMethodSyntheticBridgeActions,
        ProgramMethodMap.empty(),
        IntSets.EMPTY_SET,
        Collections.emptySet());
  }

  public static MainDexRootSetBuilder builder(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateAppSubtypingInfo subtypingInfo,
      Iterable<? extends ProguardConfigurationRule> rules) {
    return new MainDexRootSetBuilder(appView, subtypingInfo, rules);
  }

  @Override
  public void shouldNotBeMinified(ProgramDefinition definition) {
    // Do nothing.
  }

  @Override
  public MainDexRootSet rewrittenWithLens(GraphLens graphLens, Timing timing) {
    timing.begin("Rewrite MainDexRootSet");
    MainDexRootSet rewrittenMainDexRootSet;
    if (graphLens.isIdentityLens()) {
      rewrittenMainDexRootSet = this;
    } else {
      ImmutableList.Builder<DexReference> rewrittenReasonAsked = ImmutableList.builder();
      reasonAsked.forEach(
          reference ->
              rewriteAndApplyIfNotPrimitiveType(graphLens, reference, rewrittenReasonAsked::add));
      // TODO(b/164019179): If rules can now reference dead items. These should be pruned or
      //  rewritten
      ifRules.forEach(ProguardIfRule::canReferenceDeadTypes);
      // All delayed root set actions should have been processed at this point.
      assert delayedInterfaceMethodSyntheticBridgeActions.isEmpty();
      rewrittenMainDexRootSet =
          new MainDexRootSet(
              getDependentMinimumKeepInfo().rewrittenWithLens(graphLens, timing),
              rewrittenReasonAsked.build(),
              ifRules,
              delayedInterfaceMethodSyntheticBridgeActions);
    }
    timing.end();
    return rewrittenMainDexRootSet;
  }

  public MainDexRootSet withoutPrunedItems(PrunedItems prunedItems, Timing timing) {
    if (prunedItems.isEmpty()) {
      return this;
    }
    timing.begin("Prune MainDexRootSet");
    // TODO(b/164019179): If rules can now reference dead items. These should be pruned or
    //  rewritten.
    ifRules.forEach(ProguardIfRule::canReferenceDeadTypes);
    // All delayed root set actions should have been processed at this point.
    assert delayedInterfaceMethodSyntheticBridgeActions.isEmpty();
    MainDexRootSet result =
        new MainDexRootSet(
            getDependentMinimumKeepInfo(),
            reasonAsked,
            ifRules,
            delayedInterfaceMethodSyntheticBridgeActions);
    timing.end();
    return result;
  }
}
