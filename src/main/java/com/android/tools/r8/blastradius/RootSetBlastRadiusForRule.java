// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.google.common.collect.Sets;
import java.util.Set;

public class RootSetBlastRadiusForRule {

  private final ProguardKeepRuleBase rule;

  private final Set<DexType> matchedClasses = Sets.newIdentityHashSet();
  private final Set<DexField> matchedFields = Sets.newIdentityHashSet();
  private final Set<DexMethod> matchedMethods = Sets.newIdentityHashSet();

  RootSetBlastRadiusForRule(ProguardKeepRuleBase rule) {
    this.rule = rule;
  }

  void addMatchedClass(DexType type) {
    matchedClasses.add(type);
  }

  void addMatchedField(DexField field) {
    matchedFields.add(field);
  }

  void addMatchedMethod(DexMethod method) {
    matchedMethods.add(method);
  }

  int getNumberOfClasses() {
    return matchedClasses.size();
  }

  int getNumberOfFields() {
    return matchedFields.size();
  }

  int getNumberOfMethods() {
    return matchedMethods.size();
  }

  int getNumberOfItems() {
    return matchedClasses.size() + matchedFields.size() + matchedMethods.size();
  }

  public ProguardKeepRuleBase getRule() {
    return rule;
  }
}
