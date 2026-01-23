// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class ProguardIfRule extends ProguardKeepRuleBase {

  private final ProguardIfRule parent;
  private final DexClass precondition;
  private final ProguardKeepRule subsequentRule;

  private Map<DexField, DexField> inlinableFieldsInPrecondition = new ConcurrentHashMap<>();

  @Override
  public ProguardKeepRuleModifiers getModifiers() {
    assert super.getModifiers().isBottom();
    return getSubsequentRule().getModifiers();
  }

  public DexClass getPrecondition() {
    assert precondition != null;
    return precondition;
  }

  public ProguardKeepRule getSubsequentRule() {
    return subsequentRule;
  }

  public void addInlinableFieldMatchingPrecondition(DexField field) {
    if (inlinableFieldsInPrecondition != null) {
      inlinableFieldsInPrecondition.put(field, field);
    }
  }

  public Set<DexField> getAndClearInlinableFieldsMatchingPrecondition() {
    Set<DexField> fields = inlinableFieldsInPrecondition.keySet();
    inlinableFieldsInPrecondition = null;
    return fields;
  }

  public static class Builder extends ProguardKeepRuleBase.Builder<ProguardIfRule, Builder> {

    ProguardKeepRule subsequentRule = null;

    protected Builder() {
      super();
    }

    @Override
    public Builder self() {
      return this;
    }

    public void setSubsequentRule(ProguardKeepRule rule) {
      subsequentRule = rule;
    }

    @Override
    public ProguardIfRule build() {
      assert subsequentRule != null : "Option -if without a subsequent rule.";
      return new ProguardIfRule(
          origin,
          getPosition(),
          source,
          buildClassAnnotations(),
          classAccessFlags,
          negatedClassAccessFlags,
          classTypeNegated,
          classType,
          classNames,
          buildInheritanceAnnotations(),
          inheritanceClassName,
          inheritanceIsExtends,
          memberRules,
          subsequentRule,
          null,
          null);
    }
  }

  private ProguardIfRule(
      Origin origin,
      Position position,
      String source,
      List<ProguardTypeMatcher> classAnnotations,
      ProguardAccessFlags classAccessFlags,
      ProguardAccessFlags negatedClassAccessFlags,
      boolean classTypeNegated,
      ProguardClassType classType,
      ProguardClassNameList classNames,
      List<ProguardTypeMatcher> inheritanceAnnotations,
      ProguardTypeMatcher inheritanceClassName,
      boolean inheritanceIsExtends,
      List<ProguardMemberRule> memberRules,
      ProguardKeepRule subsequentRule,
      DexClass precondition,
      ProguardIfRule parent) {
    super(
        origin,
        position,
        source,
        classAnnotations,
        classAccessFlags,
        negatedClassAccessFlags,
        classTypeNegated,
        classType,
        classNames,
        inheritanceAnnotations,
        inheritanceClassName,
        inheritanceIsExtends,
        memberRules,
        ProguardKeepRuleType.CONDITIONAL,
        ProguardKeepRuleModifiers.builder().setAllowsAll().build());
    this.parent = parent;
    this.precondition = precondition;
    this.subsequentRule = subsequentRule;
  }

  public static Builder builder() {
    return new Builder();
  }

  public ProguardIfRule getParentOrThis() {
    return parent != null ? parent : this;
  }

  @Override
  protected <T extends ProguardWildcard> Iterable<T> getWildcardsThatMatches(
      Predicate<? super ProguardWildcard> predicate) {
    return Iterables.concat(
        super.getWildcardsThatMatches(predicate),
        subsequentRule.getWildcardsThatMatches(predicate));
  }

  @Override
  public boolean isProguardIfRule() {
    return true;
  }

  @Override
  public ProguardIfRule asProguardIfRule() {
    return this;
  }

  protected ProguardKeepRule materialize(DexItemFactory dexItemFactory) {
    markAsUsed();
    return subsequentRule.materialize(dexItemFactory);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProguardIfRule)) {
      return false;
    }
    ProguardIfRule other = (ProguardIfRule) o;
    if (!subsequentRule.equals(other.subsequentRule)) {
      return false;
    }
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode() * 3 + subsequentRule.hashCode();
  }

  @Override
  String typeString() {
    return "if";
  }

  @Override
  protected StringBuilder append(StringBuilder builder) {
    super.append(builder);
    builder.append('\n');
    return subsequentRule.append(builder);
  }

  @Override
  String modifierString() {
    return null;
  }

  public ProguardIfRule withPrecondition(DexClass precondition) {
    return new ProguardIfRule(
        getOrigin(),
        getPosition(),
        getSource(),
        getClassAnnotations(),
        getClassAccessFlags(),
        getNegatedClassAccessFlags(),
        getClassTypeNegated(),
        getClassType(),
        getClassNames(),
        getInheritanceAnnotations(),
        getInheritanceClassName(),
        getInheritanceIsExtends(),
        getMemberRules(),
        getSubsequentRule(),
        precondition,
        getParentOrThis());
  }
}
