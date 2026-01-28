// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.blastradius.proto.BlastRadiusContainer;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.android.tools.r8.shaking.KeepClassMembersNoShrinkingOfInitializerOnSubclassesFakeProguardRule;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.shaking.KeepInfo;
import com.android.tools.r8.shaking.KeepInfoCollectionEventConsumer;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.shaking.rules.KeepAnnotationFakeProguardRule;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RootSetBlastRadius {

  private final Map<ProguardKeepRuleBase, RootSetBlastRadiusForRule> blastRadius;

  private RootSetBlastRadius(Map<ProguardKeepRuleBase, RootSetBlastRadiusForRule> blastRadius) {
    this.blastRadius = blastRadius;
  }

  public static Builder builder(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer.Mode mode) {
    InternalOptions options = appView.options();
    return options.hasProguardConfiguration()
            && options.getProguardConfiguration().isPrintBlastRadius()
            && mode.isFinalTreeShaking()
        ? new Builder()
        : null;
  }

  public Collection<RootSetBlastRadiusForRule> getBlastRadius() {
    return blastRadius.values();
  }

  public Collection<RootSetBlastRadiusForRule> getBlastRadiusWithDeterministicOrder() {
    // TODO(b/441055269): Sorting by source is not guaranteed to be deterministic.
    return ListUtils.sort(getBlastRadius(), Comparator.comparing(x -> x.getRule().getSource()));
  }

  public Map<RootSetBlastRadiusForRule, Collection<RootSetBlastRadiusForRule>> getSubsumedByInfo() {
    return new KeepRuleSubsumptionAnalysis(this).run();
  }

  public void writeToFile(AppView<?> appView, Path printBlastRadiusFile) {
    BlastRadiusContainer collection = new RootSetBlastRadiusSerializer(appView).serialize(this);
    try (OutputStream output = Files.newOutputStream(printBlastRadiusFile)) {
      collection.writeTo(output);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static class Builder implements KeepInfoCollectionEventConsumer {

    private final Map<ProguardKeepRuleBase, RootSetBlastRadiusForRule> blastRadius =
        new IdentityHashMap<>();

    @Override
    public void acceptKeepClassInfo(
        DexType type, Consumer<? super KeepClassInfo.Joiner> keepInfoEffect) {
      acceptKeepInfo(
          type,
          keepInfoEffect,
          KeepClassInfo.newEmptyJoiner(),
          RootSetBlastRadiusForRule::addMatchedClass);
    }

    @Override
    public void acceptKeepFieldInfo(
        DexField field, Consumer<? super KeepFieldInfo.Joiner> keepInfoEffect) {
      acceptKeepInfo(
          field,
          keepInfoEffect,
          KeepFieldInfo.newEmptyJoiner(),
          RootSetBlastRadiusForRule::addMatchedField);
    }

    @Override
    public void acceptKeepMethodInfo(
        DexMethod method, Consumer<? super KeepMethodInfo.Joiner> keepInfoEffect) {
      acceptKeepInfo(
          method,
          keepInfoEffect,
          KeepMethodInfo.newEmptyJoiner(),
          RootSetBlastRadiusForRule::addMatchedMethod);
    }

    private <R extends DexReference, J extends KeepInfo.Joiner<?, ?, ?>> void acceptKeepInfo(
        R reference,
        Consumer<? super J> keepInfoEffect,
        J emptyJoiner,
        BiConsumer<RootSetBlastRadiusForRule, R> addReferenceToRuleBlastRadius) {
      keepInfoEffect.accept(emptyJoiner);
      for (ProguardKeepRuleBase rule : emptyJoiner.getRules()) {
        if (rule.isProguardIfRule()) {
          // Perform attribution to the root -if rule.
          rule = rule.asProguardIfRule().getParentOrThis();
        }
        RootSetBlastRadiusForRule ruleBlastRadius =
            blastRadius.computeIfAbsent(rule, RootSetBlastRadiusForRule::new);
        addReferenceToRuleBlastRadius.accept(ruleBlastRadius, reference);
      }
    }

    public RootSetBlastRadius build(AppView<? extends AppInfoWithClassHierarchy> appView) {
      // Add all rules so that the blast radius result also contains empty rules.
      for (var rule : appView.options().getProguardConfiguration().getRules()) {
        if (rule instanceof ProguardKeepRuleBase) {
          blastRadius.computeIfAbsent((ProguardKeepRuleBase) rule, RootSetBlastRadiusForRule::new);
        }
      }
      // Remove fake rules from output.
      blastRadius.keySet().removeIf(Builder::isFakeKeepRule);
      return new RootSetBlastRadius(blastRadius);
    }

    private static boolean isFakeKeepRule(ProguardKeepRuleBase rule) {
      return rule instanceof KeepAnnotationFakeProguardRule
          || rule instanceof KeepClassMembersNoShrinkingOfInitializerOnSubclassesFakeProguardRule;
    }
  }
}
