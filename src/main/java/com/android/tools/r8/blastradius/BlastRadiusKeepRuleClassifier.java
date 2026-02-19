// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.shaking.ProguardKeepRuleType;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.android.tools.r8.shaking.ProguardMemberType;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import com.android.tools.r8.shaking.ProguardTypeMatcher.MatchTypePattern;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.List;

public class BlastRadiusKeepRuleClassifier {

  public static boolean isPackageWideKeepRule(ProguardKeepRuleBase rule) {
    if (rule.isProguardIfRule()) {
      return isPackageWideKeepRule(rule.asProguardIfRule().getSubsequentRule());
    }
    if (rule.hasClassAnnotations() || rule.hasInheritanceClassName()) {
      return false;
    }
    // Must have exactly one class name that ends in .* or .**.
    ProguardClassNameList classNames = rule.getClassNames();
    if (classNames == null || classNames.size() != 1) {
      return false;
    }
    TraversalContinuation<?, ?> traversalContinuation =
        classNames.traverseTypeMatchers(
            matcher -> {
              if (matcher instanceof MatchTypePattern) {
                String pattern = ((MatchTypePattern) matcher).getPattern();
                return TraversalContinuation.breakIf(
                    pattern.endsWith(".*") || pattern.endsWith(".**"));
              }
              return TraversalContinuation.doContinue();
            });
    if (traversalContinuation.isContinue()) {
      return false;
    }
    // If it's a -keep rule, then this is already bad.
    if (rule.getType() == ProguardKeepRuleType.KEEP) {
      return true;
    }
    // If it's a -keepclassmembers rule, then the member rules must be sufficiently broad for the
    // rule to be classified as package wide.
    if (rule.getType() == ProguardKeepRuleType.KEEP_CLASS_MEMBERS) {
      for (ProguardMemberRule memberRule : rule.getMemberRules()) {
        ProguardMemberType memberType = memberRule.getRuleType();
        // Package wide if type is all.
        if (memberType == ProguardMemberType.ALL
            || memberType == ProguardMemberType.ALL_FIELDS
            || memberType == ProguardMemberType.ALL_METHODS) {
          return true;
        }
        // Package wide if it matches all class initializers.
        if (memberType == ProguardMemberType.CLINIT) {
          return true;
        }
        // Package wide if it matches at least all default instance initializers.
        if (memberType == ProguardMemberType.INIT) {
          List<ProguardTypeMatcher> arguments = memberRule.getArguments();
          if (arguments == null || arguments.isEmpty() || arguments.get(0).isTripleDotPattern()) {
            return true;
          }
        }
      }
    }
    // If it's a -keepclasseswithmembers rule, then report it.
    if (rule.getType() == ProguardKeepRuleType.KEEP_CLASSES_WITH_MEMBERS) {
      return true;
    }
    return false;
  }
}
