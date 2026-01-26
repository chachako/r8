// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.shaking.ProguardKeepRuleModifiers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class KeepRuleSubsumptionAnalysis {

  private final RootSetBlastRadius blastRadius;
  private final Map<RootSetBlastRadiusForRule, Collection<RootSetBlastRadiusForRule>> subsumedBy =
      new IdentityHashMap<>();

  KeepRuleSubsumptionAnalysis(RootSetBlastRadius blastRadius) {
    this.blastRadius = blastRadius;
  }

  Map<RootSetBlastRadiusForRule, Collection<RootSetBlastRadiusForRule>> run() {
    // Visit all keep rule pairs.
    // TODO(b/441055269): Parallelize.
    // TODO(b/441055269): Consider narrowing the candidate pairs by syntactic analysis of the keep
    //  rules. For example, consider `-keep class com.**` and `-keep class com.example.**` but not
    //  `-keep class com.example.**` and `-keep class com.google.**`.
    List<RootSetBlastRadiusForRule> blastRadiusForRules =
        new ArrayList<>(blastRadius.getBlastRadius());
    for (int i = 0; i < blastRadiusForRules.size(); i++) {
      RootSetBlastRadiusForRule blastRadiusForRule = blastRadiusForRules.get(i);
      for (int j = i + 1; j < blastRadiusForRules.size(); j++) {
        RootSetBlastRadiusForRule blastRadiusForOtherRule = blastRadiusForRules.get(j);
        if (hasSameModifiers(blastRadiusForRule, blastRadiusForOtherRule)) {
          if (isSubsumedBy(blastRadiusForRule, blastRadiusForOtherRule)) {
            addSubsumedBy(blastRadiusForRule, blastRadiusForOtherRule);
          }
          if (isSubsumedBy(blastRadiusForOtherRule, blastRadiusForRule)) {
            addSubsumedBy(blastRadiusForOtherRule, blastRadiusForRule);
          }
        }
      }
    }
    return subsumedBy;
  }

  private void addSubsumedBy(
      RootSetBlastRadiusForRule blastRadiusForRule,
      RootSetBlastRadiusForRule blastRadiusForOtherRule) {
    subsumedBy
        .computeIfAbsent(blastRadiusForRule, ignoreKey(ArrayList::new))
        .add(blastRadiusForOtherRule);
  }

  private static boolean hasSameModifiers(
      RootSetBlastRadiusForRule blastRadiusForRule,
      RootSetBlastRadiusForRule blastRadiusForOtherRule) {
    ProguardKeepRuleModifiers modifiers = blastRadiusForRule.getRule().getModifiers();
    ProguardKeepRuleModifiers otherModifiers = blastRadiusForOtherRule.getRule().getModifiers();
    return modifiers.equals(otherModifiers);
  }

  private static boolean isSubsumedBy(
      RootSetBlastRadiusForRule blastRadiusForRule,
      RootSetBlastRadiusForRule blastRadiusForOtherRule) {
    // Fast path based on size of blast radius.
    if (blastRadiusForRule.getMatchedClasses().size()
            > blastRadiusForOtherRule.getMatchedClasses().size()
        || blastRadiusForRule.getMatchedFields().size()
            > blastRadiusForOtherRule.getMatchedFields().size()
        || blastRadiusForRule.getMatchedMethods().size()
            > blastRadiusForOtherRule.getMatchedMethods().size()) {
      return false;
    }
    return isMatchedItemsSubsumedBy(
            blastRadiusForRule,
            blastRadiusForOtherRule,
            RootSetBlastRadiusForRule::getMatchedClasses)
        && isMatchedItemsSubsumedBy(
            blastRadiusForRule,
            blastRadiusForOtherRule,
            RootSetBlastRadiusForRule::getMatchedFields)
        && isMatchedItemsSubsumedBy(
            blastRadiusForRule,
            blastRadiusForOtherRule,
            RootSetBlastRadiusForRule::getMatchedMethods);
  }

  private static <T> boolean isMatchedItemsSubsumedBy(
      RootSetBlastRadiusForRule blastRadiusForRule,
      RootSetBlastRadiusForRule blastRadiusForOtherRule,
      Function<RootSetBlastRadiusForRule, Set<T>> fn) {
    Set<T> matchedItems = fn.apply(blastRadiusForRule);
    Set<T> otherMatchedItems = fn.apply(blastRadiusForOtherRule);
    assert matchedItems.size() <= otherMatchedItems.size();
    return otherMatchedItems.containsAll(matchedItems);
  }
}
