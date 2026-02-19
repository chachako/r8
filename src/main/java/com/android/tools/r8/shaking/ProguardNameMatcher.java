// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.shaking.ProguardConfigurationParser.IdentifierPatternWithWildcards;
import com.android.tools.r8.shaking.ProguardConfigurationParser.IdentifierPatternWithWildcardsAndNegation;
import com.android.tools.r8.shaking.ProguardWildcard.BackReference;
import com.android.tools.r8.shaking.ProguardWildcard.Pattern;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IterableUtils;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class ProguardNameMatcher {

  private static final ProguardNameMatcher MATCH_ALL_NAMES = new MatchAllNames();

  private ProguardNameMatcher() {
  }

  public static ProguardNameMatcher create(IdentifierPatternWithWildcardsAndNegation pattern) {
    if (!pattern.isNegated()) {
      IdentifierPatternWithWildcards nonNegatedPattern = pattern.getNonNegatedPattern();
      if (nonNegatedPattern.isMatchAllNames()) {
        return MATCH_ALL_NAMES;
      }
      if (nonNegatedPattern.getWildcards().isEmpty()) {
        return new MatchSpecificName(pattern.getStringPattern());
      }
    }
    return new MatchNamePattern(pattern);
  }

  private static boolean matchFieldOrMethodNameImpl(
      String pattern, int patternIndex,
      String name, int nameIndex,
      List<ProguardWildcard> wildcards, int wildcardIndex) {
    ProguardWildcard wildcard;
    Pattern wildcardPattern;
    BackReference backReference;
    for (int i = patternIndex; i < pattern.length(); i++) {
      char patternChar = pattern.charAt(i);
      switch (patternChar) {
        case '*':
          wildcard = wildcards.get(wildcardIndex);
          assert wildcard.isPattern();
          wildcardPattern = wildcard.asPattern();
          // Match the rest of the pattern against the rest of the name.
          for (int nextNameIndex = nameIndex; nextNameIndex <= name.length(); nextNameIndex++) {
            wildcardPattern.setCaptured(name.substring(nameIndex, nextNameIndex));
            if (matchFieldOrMethodNameImpl(
                pattern, i + 1, name, nextNameIndex, wildcards, wildcardIndex + 1)) {
              return true;
            }
          }
          return false;
        case '?':
          wildcard = wildcards.get(wildcardIndex);
          assert wildcard.isPattern();
          if (nameIndex == name.length()) {
            return false;
          }
          wildcardPattern = wildcard.asPattern();
          wildcardPattern.setCaptured(name.substring(nameIndex, nameIndex + 1));
          nameIndex++;
          wildcardIndex++;
          break;
        case '<':
          wildcard = wildcards.get(wildcardIndex);
          assert wildcard.isBackReference();
          backReference = wildcard.asBackReference();
          String captured = backReference.getCaptured();
          if (captured == null
              || name.length() < nameIndex + captured.length()
              || !captured.equals(name.substring(nameIndex, nameIndex + captured.length()))) {
            return false;
          }
          nameIndex = nameIndex + captured.length();
          wildcardIndex++;
          i = pattern.indexOf(">", i);
          break;
        default:
          if (nameIndex == name.length() || patternChar != name.charAt(nameIndex++)) {
            return false;
          }
          break;
      }
    }
    return nameIndex == name.length();
  }

  public abstract boolean matches(String name);

  protected final Iterable<ProguardWildcard> getWildcards() {
    return getWildcardsThatMatches(alwaysTrue());
  }

  protected <T extends ProguardWildcard> Iterable<T> getWildcardsThatMatches(
      Predicate<? super ProguardWildcard> predicate) {
    return IterableUtils.empty();
  }

  static <T extends ProguardWildcard> Iterable<T> getWildcardsThatMatchesOrEmpty(
      ProguardNameMatcher nameMatcher, Predicate<? super ProguardWildcard> predicate) {
    return nameMatcher != null
        ? nameMatcher.getWildcardsThatMatches(predicate)
        : IterableUtils.empty();
  }

  protected ProguardNameMatcher materialize() {
    return this;
  }

  private static class MatchAllNames extends ProguardNameMatcher {
    private final ProguardWildcard wildcard;

    MatchAllNames() {
      this(new Pattern("*"));
    }

    private MatchAllNames(ProguardWildcard wildcard) {
      this.wildcard = wildcard;
    }

    @Override
    public boolean matches(String name) {
      wildcard.setCaptured(name);
      return true;
    }

    @Override
    protected <T extends ProguardWildcard> Iterable<T> getWildcardsThatMatches(
        Predicate<? super ProguardWildcard> predicate) {
      return predicate.test(wildcard) ? Collections.singleton((T) wildcard) : IterableUtils.empty();
    }

    @Override
    protected MatchAllNames materialize() {
      return new MatchAllNames(wildcard.materialize());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MatchAllNames;
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }

    @Override
    public String toString() {
      return "*";
    }
  }

  private static class MatchNamePattern extends ProguardNameMatcher {

    private final String pattern;
    private final List<ProguardWildcard> wildcards;
    private final boolean negated;

    MatchNamePattern(IdentifierPatternWithWildcardsAndNegation pattern) {
      this(pattern.getStringPattern(), pattern.getWildcards(), pattern.isNegated());
    }

    MatchNamePattern(String pattern, List<ProguardWildcard> wildcards, boolean negated) {
      this.pattern = pattern;
      this.wildcards = wildcards;
      this.negated = negated;
    }

    @Override
    public boolean matches(String name) {
      boolean matched = matchFieldOrMethodNameImpl(pattern, 0, name, 0, wildcards, 0) != negated;
      if (!matched) {
        wildcards.forEach(ProguardWildcard::clearCaptured);
      }
      return matched;
    }

    @Override
    protected <T extends ProguardWildcard> Iterable<T> getWildcardsThatMatches(
        Predicate<? super ProguardWildcard> predicate) {
      return IterableUtils.filter(wildcards, predicate);
    }

    @Override
    protected MatchNamePattern materialize() {
      List<ProguardWildcard> materializedWildcards =
          wildcards.stream().map(ProguardWildcard::materialize).collect(Collectors.toList());
      return new MatchNamePattern(pattern, materializedWildcards, negated);
    }

    @Override
    public String toString() {
      return (negated ? "!" : "") + pattern;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || !(o instanceof MatchNamePattern)) {
        return false;
      }
      MatchNamePattern other = (MatchNamePattern) o;
      return pattern.equals(other.pattern) && negated == other.negated;
    }

    @Override
    public int hashCode() {
      return (pattern.hashCode() << 1) | BooleanUtils.intValue(negated);
    }
  }

  private static class MatchSpecificName extends ProguardNameMatcher {

    private final String name;

    MatchSpecificName(String name) {
      this.name = name;
    }

    @Override
    public boolean matches(String name) {
      return this.name.equals(name);
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof MatchSpecificName && name.equals(((MatchSpecificName) o).name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }
}
