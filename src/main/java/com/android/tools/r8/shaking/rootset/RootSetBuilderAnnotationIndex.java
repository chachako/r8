// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.rootset;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.shaking.NoAccessModificationRule;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardKeepRuleType;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import com.android.tools.r8.shaking.ProguardTypeMatcher.MatchSpecificType;
import com.android.tools.r8.shaking.ProguardTypeMatcher.MatchSpecificTypes;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.android.tools.r8.utils.timing.TimingMerger;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Used to optimize rules on the form {@code -keep... class * { @<specific type> ...; }}.
 *
 * <p>This creates a mapping from specific member annotation types to the classes that have a member
 * which is annotated with the given type. This makes it possible to efficiently lookup the relevant
 * classes for a given rule.
 */
public class RootSetBuilderAnnotationIndex {

  // Limit the total size of the index to ~2% of the number of classes in the app.
  private static final int MAX_TOTAL_SIZE_PERCENTAGE = 2;
  // Once max total size is exceeded, the set corresponding to each annotation type is bounded to
  // size 100.
  private static final int MAX_BUCKET_SIZE = 100;

  private static final RootSetBuilderAnnotationIndex NONE = new RootSetBuilderAnnotationIndex(null);

  // Map from annotation types to the classes that have a member that is annotated with the given
  // type.
  private final Map<DexType, Set<DexProgramClass>> memberAnnotationTypeToClasses;

  RootSetBuilderAnnotationIndex(Map<DexType, Set<DexProgramClass>> memberAnnotationTypeToClasses) {
    this.memberAnnotationTypeToClasses = memberAnnotationTypeToClasses;
  }

  // Given a rule, returns the relevant classes for that rule, or null, if nothing is known about
  // the relevant classes for the rule.
  public Set<DexProgramClass> getRelevantCandidatesOrNull(ProguardConfigurationRule rule) {
    if (isEligible(rule)) {
      ProguardTypeMatcher memberRuleAnnotationMatcher = getMemberRuleAnnotation(rule);
      if (memberRuleAnnotationMatcher != null) {
        if (memberRuleAnnotationMatcher.hasSpecificType()) {
          DexType specificType = memberRuleAnnotationMatcher.getSpecificType();
          return hasClassesWithMemberAnnotatedBy(specificType)
              ? getClassesWithMemberAnnotatedBy(specificType)
              : null;
        } else if (memberRuleAnnotationMatcher.hasSpecificTypes()) {
          // We can only return the relevant classes if they are known for each specific type.
          Set<DexType> specificTypes = memberRuleAnnotationMatcher.getSpecificTypes();
          return Iterables.all(specificTypes, this::hasClassesWithMemberAnnotatedBy)
              ? SetUtils.flatMapIdentitySet(specificTypes, this::getClassesWithMemberAnnotatedBy)
              : null;
        }
      }
    }
    return null;
  }

  private boolean isEligible(ProguardConfigurationRule rule) {
    // -keep rules are not eligible, since a -keep class * { @Anno ...; } rule should match all
    // classes, not only the classes that contain a member that has an @Anno annotation.
    if (rule.isProguardKeepRule()
        && rule.asProguardKeepRule().getType() == ProguardKeepRuleType.KEEP) {
      return false;
    }
    if (rule instanceof NoAccessModificationRule) {
      return false;
    }
    return true;
  }

  private boolean hasClassesWithMemberAnnotatedBy(DexType annotationType) {
    return memberAnnotationTypeToClasses != null
        && memberAnnotationTypeToClasses.containsKey(annotationType);
  }

  private Set<DexProgramClass> getClassesWithMemberAnnotatedBy(DexType annotationType) {
    return memberAnnotationTypeToClasses.get(annotationType);
  }

  private static ProguardTypeMatcher getMemberRuleAnnotation(ProguardConfigurationRule rule) {
    // Note: We require that the class rule matches all classes, since the interpretation of member
    // rules may traverse up in the hierarchy.
    if (rule.isTrivialAllClassMatch() && rule.getMemberRules().size() == 1) {
      ProguardMemberRule memberRule = rule.getMemberRule(0);
      if (memberRule.getAnnotations().size() == 1) {
        return memberRule.getAnnotations().get(0);
      }
    }
    return null;
  }

  public static RootSetBuilderAnnotationIndex createForRootSetBuilder(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Iterable<? extends ProguardConfigurationRule> rules,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    try (Timing t0 = timing.begin("Build annotation index")) {
      // Find the rules that have a single member rule with a single annotation matcher.
      // Extract the annotation types, but also the annotation matchers when the matcher does not
      // have a specific type.
      Set<DexType> memberRuleAnnotations = Sets.newIdentityHashSet();
      Map<ProguardConfigurationRule, ProguardTypeMatcher> memberRuleAnnotationMatchers =
          new IdentityHashMap<>();
      for (ProguardConfigurationRule rule : rules) {
        ProguardTypeMatcher memberRuleAnnotationMatcher = getMemberRuleAnnotation(rule);
        if (memberRuleAnnotationMatcher != null) {
          if (memberRuleAnnotationMatcher.hasSpecificType()) {
            memberRuleAnnotations.add(memberRuleAnnotationMatcher.getSpecificType());
          } else {
            memberRuleAnnotationMatchers.put(rule, memberRuleAnnotationMatcher);
          }
        }
      }

      // Interpret the annotation matchers that do not match a specific type against all program
      // classes and rewrite the matchers into MatchSpecificTypes matchers, when possible.
      convertAnnotationMatchersToSpecificTypes(
          appView, memberRuleAnnotations, memberRuleAnnotationMatchers, executorService);

      // If we didn't find any annotation types, then there is no more to be done.
      if (memberRuleAnnotations.isEmpty()) {
        return none();
      }

      // Build the final map from annotation types to the classes that have a member annotated by
      // the annotation.
      return buildIndex(appView, memberRuleAnnotations, executorService);
    }
  }

  private static RootSetBuilderAnnotationIndex buildIndex(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Set<DexType> memberRuleAnnotations,
      ExecutorService executorService)
      throws ExecutionException {
    Map<DexType, Set<DexProgramClass>> memberAnnotationTypeToClasses = new ConcurrentHashMap<>();
    for (DexType annotationType : memberRuleAnnotations) {
      memberAnnotationTypeToClasses.put(annotationType, Sets.newIdentityHashSet());
    }
    AtomicInteger memberAnnotationTypeToClassesSize = new AtomicInteger();
    int memberAnnotationTypeToClassesMaxSize =
        Math.round(((float) appView.appInfo().classes().size() / 100) * MAX_TOTAL_SIZE_PERCENTAGE);
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          Set<DexType> seen = Sets.newIdentityHashSet();
          for (DexEncodedMember<?, ?> member : clazz.members()) {
            handleMemberAnnotations(
                clazz,
                member.annotations(),
                memberAnnotationTypeToClasses,
                memberAnnotationTypeToClassesSize,
                memberAnnotationTypeToClassesMaxSize,
                memberRuleAnnotations,
                seen);
            handleMemberAnnotations(
                clazz,
                member.getParameterAnnotations(),
                memberAnnotationTypeToClasses,
                memberAnnotationTypeToClassesSize,
                memberAnnotationTypeToClassesMaxSize,
                memberRuleAnnotations,
                seen);
          }
        },
        appView.options().getThreadingModule(),
        executorService);
    if (memberAnnotationTypeToClassesSize.get() > memberAnnotationTypeToClassesMaxSize) {
      // Prune all sets above the bucket size. This is important for determinism.
      memberAnnotationTypeToClasses
          .values()
          .removeIf(value -> value == null || value.size() > MAX_BUCKET_SIZE);
    }
    return new RootSetBuilderAnnotationIndex(new IdentityHashMap<>(memberAnnotationTypeToClasses));
  }

  /**
   * Used to efficiently handle rule such as the following.
   *
   * <pre>
   * -keepclasseswithmembers,includedescriptorclasses class ** {
   *   @**org.jni_zero.CalledByNative <methods>;
   * }
   * -keepclassmembers class * {
   *   @com.google.android.filament.proguard.UsedBy* *;
   * }
   * </pre>
   */
  private static void convertAnnotationMatchersToSpecificTypes(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Set<DexType> memberRuleAnnotations,
      Map<ProguardConfigurationRule, ProguardTypeMatcher> memberRuleAnnotationMatchers,
      ExecutorService executorService)
      throws ExecutionException {
    Map<ProguardConfigurationRule, Set<DexType>> specificTypes = new ConcurrentHashMap<>();
    for (ProguardConfigurationRule rule : memberRuleAnnotationMatchers.keySet()) {
      specificTypes.put(rule, SetUtils.newIdentityHashSet());
    }
    Set<DexType> annotationTypes = computeAnnotationTypes(appView, executorService);
    processAnnotationsConcurrently(
        appView, annotationTypes, memberRuleAnnotationMatchers, specificTypes, executorService);

    specificTypes.forEach(
        (rule, ruleAnnotationTypes) -> {
          if (ruleAnnotationTypes != null) {
            assert rule.getMemberRules().size() == 1;
            assert rule.getMemberRule(0).getAnnotations().size() == 1;
            // If the matching is empty, then the member rule will not match anything and can be
            // removed.
            if (ruleAnnotationTypes.isEmpty()) {
              rule.getMemberRules().clear();
              return;
            }
            ProguardTypeMatcher currentAnnotationMatcher = rule.getMemberRule(0).getAnnotation(0);
            ProguardTypeMatcher replacementAnnotationMatcher;
            if (ruleAnnotationTypes.size() == 1) {
              DexType annotationType = ruleAnnotationTypes.iterator().next();
              replacementAnnotationMatcher =
                  new MatchSpecificType(annotationType, currentAnnotationMatcher);
            } else {
              replacementAnnotationMatcher =
                  new MatchSpecificTypes(ruleAnnotationTypes, currentAnnotationMatcher);
            }
            rule.getMemberRule(0).getAnnotations().set(0, replacementAnnotationMatcher);
            memberRuleAnnotations.addAll(ruleAnnotationTypes);
          }
        });
  }

  private static Set<DexType> computeAnnotationTypes(
      AppView<? extends AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    Set<DexType> annotationTypes = ConcurrentHashMap.newKeySet();
    Iterable<DexClass> allClasses =
        Iterables.concat(
            appView.appInfo().classes(),
            appView.app().asDirect().classpathClasses(),
            appView.app().asDirect().libraryClasses());
    for (DexClass clazz : allClasses) {
      if (clazz.isAnnotation()) {
        annotationTypes.add(clazz.getType());
      }
    }
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          Set<DexType> seen = Sets.newIdentityHashSet();
          clazz.forEachAnnotation(
              annotation -> {
                DexType annotationType = annotation.getAnnotationType();
                if (appView.definitionFor(annotationType) == null
                    && seen.add(annotation.getAnnotationType())) {
                  annotationTypes.add(annotationType);
                }
              });
        },
        appView.options().getThreadingModule(),
        executorService);
    return annotationTypes;
  }

  private static void processAnnotationsConcurrently(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Collection<DexType> annotationTypes,
      Map<ProguardConfigurationRule, ProguardTypeMatcher> memberRuleAnnotationMatchers,
      Map<ProguardConfigurationRule, Set<DexType>> specificTypes,
      ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItemsThatMatches(
        annotationTypes,
        alwaysTrue(),
        (annotationType, threadTiming) ->
            memberRuleAnnotationMatchers.forEach(
                (rule, annotationMatcher) -> {
                  if (annotationMatcher.matches(annotationType)) {
                    specificTypes.compute(
                        rule,
                        (k, v) -> {
                          if (v != null) {
                            v.add(annotationType);
                            if (v.size() > 10) {
                              return null;
                            }
                          }
                          return v;
                        });
                  }
                }),
        appView.options(),
        executorService,
        Timing.empty(),
        TimingMerger.empty());
  }

  private static void handleMemberAnnotations(
      DexProgramClass clazz,
      DexAnnotationSet annotations,
      Map<DexType, Set<DexProgramClass>> memberAnnotationTypeToClasses,
      AtomicInteger memberAnnotationTypeToClassesSize,
      int memberAnnotationTypeToClassesMaxSize,
      Set<DexType> memberRuleAnnotations,
      Set<DexType> seen) {
    for (DexAnnotation annotation : annotations.getAnnotations()) {
      DexType annotationType = annotation.getAnnotationType();
      if (memberRuleAnnotations.contains(annotationType) && seen.add(annotationType)) {
        memberAnnotationTypeToClasses.compute(
            annotationType,
            (k, v) -> {
              if (v == null) {
                return v;
              }
              if (v.add(clazz)) {
                int newSize = memberAnnotationTypeToClassesSize.incrementAndGet();
                if (newSize > memberAnnotationTypeToClassesMaxSize && v.size() > MAX_BUCKET_SIZE) {
                  return null;
                }
              }
              return v;
            });
      }
    }
  }

  private static void handleMemberAnnotations(
      DexProgramClass clazz,
      ParameterAnnotationsList parameterAnnotations,
      Map<DexType, Set<DexProgramClass>> memberAnnotationTypeToClasses,
      AtomicInteger memberAnnotationTypeToClassesSize,
      int memberAnnotationTypeToClassesMaxSize,
      Set<DexType> memberRuleAnnotations,
      Set<DexType> seen) {
    for (DexAnnotationSet annotations : parameterAnnotations.getAnnotationSets()) {
      handleMemberAnnotations(
          clazz,
          annotations,
          memberAnnotationTypeToClasses,
          memberAnnotationTypeToClassesSize,
          memberAnnotationTypeToClassesMaxSize,
          memberRuleAnnotations,
          seen);
    }
  }

  public static RootSetBuilderAnnotationIndex none() {
    return NONE;
  }
}
