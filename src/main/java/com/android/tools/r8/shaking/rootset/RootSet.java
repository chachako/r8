// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.rootset;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.errors.InlinableStaticFinalFieldPreconditionDiagnostic;
import com.android.tools.r8.errors.UnusedProguardKeepRuleDiagnostic;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.DependentMinimumKeepInfoCollection;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.EnqueuerEvent.UnconditionalKeepInfoEvent;
import com.android.tools.r8.shaking.InterfaceMethodSyntheticBridgeAction;
import com.android.tools.r8.shaking.MinimumKeepInfoCollection;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardIfRule;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.PredicateSet;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RootSet extends RootSetBase {

  public final ImmutableList<DexReference> reasonAsked;
  public final Set<DexMethod> alwaysInline;
  public final Set<DexMethod> reprocess;
  public final PredicateSet<DexType> alwaysClassInline;
  public final Map<DexReference, ProguardMemberRule> mayHaveSideEffects;
  public final Set<DexMember<?, ?>> identifierNameStrings;
  public final Set<ProguardIfRule> ifRules;
  public final IntSet resourceIds;
  public final Set<DexType> rootNonProgramTypes;

  RootSet(
      DependentMinimumKeepInfoCollection dependentMinimumKeepInfo,
      ImmutableList<DexReference> reasonAsked,
      Set<DexMethod> alwaysInline,
      Set<DexMethod> reprocess,
      PredicateSet<DexType> alwaysClassInline,
      Map<DexReference, ProguardMemberRule> mayHaveSideEffects,
      Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule,
      Set<DexMember<?, ?>> identifierNameStrings,
      Set<ProguardIfRule> ifRules,
      List<InterfaceMethodSyntheticBridgeAction> delayedInterfaceMethodSyntheticBridgeActions,
      ProgramMethodMap<ProgramMethod> pendingMethodMoveInverse,
      IntSet resourceIds,
      Set<DexType> rootNonProgramTypes) {
    super(
        dependentMinimumKeepInfo,
        dependentKeepClassCompatRule,
        delayedInterfaceMethodSyntheticBridgeActions,
        pendingMethodMoveInverse);
    this.reasonAsked = reasonAsked;
    this.alwaysInline = alwaysInline;
    this.reprocess = reprocess;
    this.alwaysClassInline = alwaysClassInline;
    this.mayHaveSideEffects = mayHaveSideEffects;
    this.identifierNameStrings = Collections.unmodifiableSet(identifierNameStrings);
    this.ifRules = Collections.unmodifiableSet(ifRules);
    this.resourceIds = resourceIds;
    this.rootNonProgramTypes = rootNonProgramTypes;
  }

  public void checkAllRulesAreUsed(InternalOptions options) {
    if (!options.isShrinking()) {
      return;
    }
    if (options.ignoreUnusedProguardRules) {
      return;
    }
    List<ProguardConfigurationRule> rules = options.getProguardConfiguration().getRules();
    if (rules == null) {
      return;
    }
    for (ProguardConfigurationRule rule : rules) {
      if (rule.isProguardIfRule()) {
        ProguardIfRule ifRule = rule.asProguardIfRule();
        Set<DexField> unorderedFields = ifRule.getAndClearInlinableFieldsMatchingPrecondition();
        if (!unorderedFields.isEmpty()) {
          List<DexField> fields = new ArrayList<>(unorderedFields);
          fields.sort(DexField::compareTo);
          options.reporter.warning(
              new InlinableStaticFinalFieldPreconditionDiagnostic(ifRule, fields));
          continue;
        }
      }
      if (!rule.isUsed()) {
        options.reporter.info(new UnusedProguardKeepRuleDiagnostic(rule));
      }
    }
  }

  public void addConsequentRootSet(ConsequentRootSet consequentRootSet) {
    consequentRootSet.dependentKeepClassCompatRule.forEach(
        (type, rules) ->
            dependentKeepClassCompatRule.computeIfAbsent(type, k -> new HashSet<>()).addAll(rules));
    delayedInterfaceMethodSyntheticBridgeActions.addAll(
        consequentRootSet.delayedInterfaceMethodSyntheticBridgeActions);
  }

  public boolean isShrinkingDisallowedUnconditionally(
      ProgramDefinition definition, InternalOptions options) {
    if (!options.isShrinking()) {
      return true;
    }
    return getDependentMinimumKeepInfo()
        .getOrDefault(UnconditionalKeepInfoEvent.get(), MinimumKeepInfoCollection.empty())
        .hasMinimumKeepInfoThatMatches(
            definition.getReference(),
            minimumKeepInfoForDefinition -> !minimumKeepInfoForDefinition.isShrinkingAllowed());
  }

  public void pruneDeadItems(DexDefinitionSupplier definitions, Enqueuer enqueuer, Timing timing) {
    timing.begin("Prune keep info");
    getDependentMinimumKeepInfo().pruneDeadItems(definitions, enqueuer);
    timing.end();
    timing.begin("Prune others");
    pruneDeadReferences(alwaysInline, definitions, enqueuer);
    timing.end();
  }

  private static void pruneDeadReferences(
      Set<? extends DexReference> references,
      DexDefinitionSupplier definitions,
      Enqueuer enqueuer) {
    references.removeIf(
        reference -> {
          Definition definition =
              reference.apply(
                  definitions::definitionFor,
                  field ->
                      field.lookupMemberOnClass(definitions.definitionFor(field.getHolderType())),
                  method ->
                      method.lookupMemberOnClass(
                          definitions.definitionFor(method.getHolderType())));
          return definition == null || !enqueuer.isReachable(definition);
        });
  }

  public void pruneItems(PrunedItems prunedItems, Timing timing) {
    timing.begin("Prune RootSet");
    getDependentMinimumKeepInfo()
        .removeIf(
            minimumKeepInfo -> {
              minimumKeepInfo.pruneItems(prunedItems);
              return minimumKeepInfo.isEmpty();
            });
    timing.end();
  }

  public RootSet rewrittenWithLens(GraphLens graphLens, Timing timing) {
    timing.begin("Rewrite RootSet");
    RootSet rewrittenRootSet;
    if (graphLens.isIdentityLens()) {
      rewrittenRootSet = this;
    } else {
      // TODO(b/164019179): If rules can now reference dead items. These should be pruned or
      //  rewritten
      ifRules.forEach(ProguardIfRule::canReferenceDeadTypes);
      rewrittenRootSet =
          new RootSet(
              getDependentMinimumKeepInfo().rewrittenWithLens(graphLens, timing),
              reasonAsked,
              alwaysInline,
              reprocess,
              alwaysClassInline,
              mayHaveSideEffects,
              dependentKeepClassCompatRule,
              identifierNameStrings,
              ifRules,
              delayedInterfaceMethodSyntheticBridgeActions,
              pendingMethodMoveInverse,
              resourceIds,
              rootNonProgramTypes);
    }
    timing.end();
    return rewrittenRootSet;
  }

  public void shouldNotBeMinified(ProgramDefinition definition) {
    getDependentMinimumKeepInfo()
        .getOrCreateUnconditionalMinimumKeepInfoFor(definition.getReference())
        .disallowMinification()
        .applyIf(
            definition.isProgramClass(), joiner -> joiner.asClassJoiner().disallowRepackaging());
  }

  public boolean verifyKeptFieldsAreAccessedAndLive(AppView<AppInfoWithLiveness> appView) {
    getDependentMinimumKeepInfo()
        .getUnconditionalMinimumKeepInfoOrDefault(MinimumKeepInfoCollection.empty())
        .forEachThatMatches(
            (reference, minimumKeepInfo) ->
                reference.isDexField() && !minimumKeepInfo.isShrinkingAllowed(),
            (reference, minimumKeepInfo) -> {
              DexField fieldReference = reference.asDexField();
              DexProgramClass holder =
                  asProgramClassOrNull(appView.definitionForHolder(fieldReference));
              ProgramField field = fieldReference.lookupOnProgramClass(holder);
              if (field != null
                  && (field.getAccessFlags().isStatic()
                      || isKeptDirectlyOrIndirectly(field.getHolderType(), appView))) {
                assert appView.appInfo().isFieldRead(field)
                    : "Expected kept field `" + fieldReference.toSourceString() + "` to be read";
                assert appView.appInfo().isFieldWritten(field)
                    : "Expected kept field `" + fieldReference.toSourceString() + "` to be written";
              }
            });
    return true;
  }

  public boolean verifyKeptMethodsAreTargetedAndLive(AppView<AppInfoWithLiveness> appView) {
    getDependentMinimumKeepInfo()
        .getUnconditionalMinimumKeepInfoOrDefault(MinimumKeepInfoCollection.empty())
        .forEachThatMatches(
            (reference, minimumKeepInfo) ->
                reference.isDexMethod() && !minimumKeepInfo.isShrinkingAllowed(),
            (reference, minimumKeepInfo) -> {
              DexMethod methodReference = reference.asDexMethod();
              assert appView.appInfo().isTargetedMethod(methodReference)
                  : "Expected kept method `" + reference.toSourceString() + "` to be targeted";
              DexEncodedMethod method =
                  appView.definitionForHolder(methodReference).lookupMethod(methodReference);
              if (!method.isAbstract()
                  && isKeptDirectlyOrIndirectly(methodReference.getHolderType(), appView)) {
                assert appView.appInfo().isLiveMethod(methodReference)
                    : "Expected non-abstract kept method `"
                        + reference.toSourceString()
                        + "` to be live";
              }
            });
    return true;
  }

  public boolean verifyKeptTypesAreLive(AppView<AppInfoWithLiveness> appView) {
    getDependentMinimumKeepInfo()
        .getUnconditionalMinimumKeepInfoOrDefault(MinimumKeepInfoCollection.empty())
        .forEachThatMatches(
            (reference, minimumKeepInfo) ->
                reference.isDexType() && !minimumKeepInfo.isShrinkingAllowed(),
            (reference, minimumKeepInfo) -> {
              DexType type = reference.asDexType();
              assert appView.appInfo().isLiveProgramType(type)
                  : "Expected kept type `" + type.toSourceString() + "` to be live";
            });
    return true;
  }

  private boolean isKeptDirectlyOrIndirectly(DexType type, AppView<AppInfoWithLiveness> appView) {
    DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
    if (clazz == null) {
      return false;
    }
    if (isShrinkingDisallowedUnconditionally(clazz, appView.options())) {
      return true;
    }
    if (clazz.superType != null) {
      return isKeptDirectlyOrIndirectly(clazz.superType, appView);
    }
    return false;
  }

  public boolean verifyKeptItemsAreKept(AppView<? extends AppInfoWithClassHierarchy> appView) {
    AppInfoWithClassHierarchy appInfo = appView.appInfo();
    // Create a mapping from each required type to the set of required members on that type.
    Map<DexType, Set<DexMember<?, ?>>> requiredMembersPerType = new IdentityHashMap<>();
    getDependentMinimumKeepInfo()
        .getUnconditionalMinimumKeepInfoOrDefault(MinimumKeepInfoCollection.empty())
        .forEachThatMatches(
            (reference, minimumKeepInfo) -> !minimumKeepInfo.isShrinkingAllowed(),
            (reference, minimumKeepInfo) -> {
              if (reference.isDexType()) {
                DexType type = reference.asDexType();
                assert !appInfo.hasLiveness()
                        || appInfo.withLiveness().isPinnedWithDefinitionLookup(type)
                    : "Expected reference `" + type.toSourceString() + "` to be pinned";
                requiredMembersPerType.computeIfAbsent(type, key -> Sets.newIdentityHashSet());
              } else {
                DexMember<?, ?> member = reference.asDexMember();
                assert !appInfo.hasLiveness()
                        || appInfo.withLiveness().isPinnedWithDefinitionLookup(member)
                    : "Expected reference `" + member.toSourceString() + "` to be pinned";
                requiredMembersPerType
                    .computeIfAbsent(member.holder, key -> Sets.newIdentityHashSet())
                    .add(member);
              }
            });

    // Run through each class in the program and check that it has members it must have.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      Set<DexMember<?, ?>> requiredMembers =
          requiredMembersPerType.getOrDefault(clazz.type, ImmutableSet.of());

      Set<DexField> fields = null;
      Set<DexMethod> methods = null;

      for (DexMember<?, ?> requiredMember : requiredMembers) {
        if (requiredMember.isDexField()) {
          DexField requiredField = requiredMember.asDexField();
          if (fields == null) {
            // Create a Set of the fields to avoid quadratic behavior.
            fields =
                Streams.stream(clazz.fields())
                    .map(DexEncodedField::getReference)
                    .collect(Collectors.toSet());
          }
          assert fields.contains(requiredField)
              : "Expected field `"
                  + requiredField.toSourceString()
                  + "` from the root set to be present";
        } else {
          DexMethod requiredMethod = requiredMember.asDexMethod();
          if (methods == null) {
            // Create a Set of the methods to avoid quadratic behavior.
            methods =
                Streams.stream(clazz.methods())
                    .map(DexEncodedMethod::getReference)
                    .collect(Collectors.toSet());
          }
          assert methods.contains(requiredMethod)
              : "Expected method `"
                  + requiredMethod.toSourceString()
                  + "` from the root set to be present";
        }
      }
      requiredMembersPerType.remove(clazz.type);
    }

    // If the map is non-empty, then a type in the root set was not in the application.
    if (!requiredMembersPerType.isEmpty()) {
      DexType type = requiredMembersPerType.keySet().iterator().next();
      DexClass clazz = appView.definitionFor(type);
      assert clazz == null || clazz.isProgramClass()
          : "Unexpected library type in root set: `" + type + "`";
      assert requiredMembersPerType.isEmpty()
          : "Expected type `" + type.toSourceString() + "` to be present";
    }

    return true;
  }

  public static RootSetBuilder builder(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateAppSubtypingInfo subtypingInfo) {
    return new RootSetBuilder(appView, subtypingInfo);
  }

  public static RootSetBuilder builder(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateAppSubtypingInfo subtypingInfo,
      Iterable<? extends ProguardConfigurationRule> rules) {
    return new RootSetBuilder(appView, subtypingInfo, rules);
  }
}
