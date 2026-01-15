// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.KeepClassMembersNoShrinkingOfInitializerOnSubclassesFakeProguardRule;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.shaking.rules.KeepAnnotationFakeProguardRule;
import com.android.tools.r8.utils.ListUtils;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

// TODO(b/441055269): Account for how many root items are uniquely kept by each rule.
// TODO(b/441055269): Report if some rules are subsumed by others.
// TODO(b/441055269): Consider classifying rules into categories, e.g., package wide keep rules,
//  keep rules with no wildcards, etc.
// TODO(b/441055269): Consider making it possible to group keep rules by origin (e.g., filter app's
//  own rules).
// TODO(b/441055269): Make it possible to inspect the concrete items that are kept by a given rule.
// TODO(b/441055269): Report all rules with blast radius 0 (unused rules).
// TODO(b/441055269): Consider making it possible to "bless" keep rules, so that they don't show up
//  in any future blast radius reports.
// TODO(b/441055269): Create a summary of the impact of each 3p library, and rank the libraries by
//  negative impact.
public class BlastRadiusOrderedByNumberOfItemsReporter implements BlastRadiusReporter {

  @Override
  public void report(Collection<RootSetBlastRadiusForRule> blastRadius) {
    List<RootSetBlastRadiusForRule> sorted =
        ListUtils.sort(
            blastRadius,
            Comparator.comparingInt(RootSetBlastRadiusForRule::getNumberOfItems).reversed());
    System.out.println("======================");
    System.out.println("KEEP RULE BLAST RADIUS");
    System.out.println("=======================");
    int i = 1;
    for (RootSetBlastRadiusForRule blastRadiusForRule : sorted) {
      assert blastRadiusForRule.getNumberOfItems() > 0;
      ProguardKeepRuleBase rule = blastRadiusForRule.getRule();
      if (rule instanceof KeepAnnotationFakeProguardRule) {
        // TODO(b/441055269): Add support for keep annotations.
        continue;
      }
      if (rule instanceof KeepClassMembersNoShrinkingOfInitializerOnSubclassesFakeProguardRule) {
        // Intentionally do not report built-in rules.
        continue;
      }
      if (rule.getPosition() == Position.UNKNOWN) {
        throw new RuntimeException(rule.toString());
      }
      // TODO(b/441055269): Add support for keep rules that allow shrinking.
      assert !rule.getModifiers().allowsShrinking;
      System.out.println(i + ". " + rule);
      System.out.println("   @ " + rule.getOriginString());
      System.out.println();
      System.out.print("Blast radius (dontshrink): " + blastRadiusForRule.getNumberOfItems());
      System.out.print(" (");
      boolean needsComma = false;
      if (blastRadiusForRule.getNumberOfClasses() > 0) {
        System.out.print(blastRadiusForRule.getNumberOfClasses() + " classes");
        needsComma = true;
      }
      if (blastRadiusForRule.getNumberOfMethods() > 0) {
        if (needsComma) {
          System.out.print(", ");
        }
        System.out.print(blastRadiusForRule.getNumberOfMethods() + " methods");
        needsComma = true;
      }
      if (blastRadiusForRule.getNumberOfFields() > 0) {
        if (needsComma) {
          System.out.print(", ");
        }
        System.out.print(blastRadiusForRule.getNumberOfFields() + " fields");
      }
      System.out.println(")");
      System.out.println();
      i++;
    }
  }
}
