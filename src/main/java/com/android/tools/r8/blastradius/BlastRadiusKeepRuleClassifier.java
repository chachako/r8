// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.shaking.ProguardTypeMatcher.MatchTypePattern;
import com.android.tools.r8.utils.TraversalContinuation;

public class BlastRadiusKeepRuleClassifier {

  public static boolean isPackageWideKeepRule(ProguardKeepRuleBase rule) {
    if (rule.isProguardIfRule() || rule.hasClassAnnotations() || rule.hasInheritanceClassName()) {
      return false;
    }
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
    return traversalContinuation.isBreak();
  }
}
