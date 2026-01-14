// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.KeepClassInfo;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.shaking.KeepInfoCollectionEventConsumer;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.utils.InternalOptions;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RootSetBlastRadius {

  @SuppressWarnings("UnusedVariable")
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

  public void report() {
    // TODO(b/441055269): Implement.
    throw new Unimplemented();
  }

  public static class Builder implements KeepInfoCollectionEventConsumer {

    private final Map<ProguardKeepRuleBase, RootSetBlastRadiusForRule> blastRadius =
        new IdentityHashMap<>();

    @Override
    public void acceptKeepClassInfo(
        DexType type, Consumer<? super KeepClassInfo.Joiner> keepInfoEffect) {
      KeepClassInfo.Joiner joiner = KeepClassInfo.newEmptyJoiner();
      keepInfoEffect.accept(joiner);
      for (ProguardKeepRuleBase rule : joiner.getRules()) {
        RootSetBlastRadiusForRule ruleBlastRadius =
            blastRadius.computeIfAbsent(rule, ignoreKey(RootSetBlastRadiusForRule::new));
        ruleBlastRadius.addMatchedClass(type);
      }
    }

    @Override
    public void acceptKeepFieldInfo(
        DexField field, Consumer<? super KeepFieldInfo.Joiner> keepInfoEffect) {
      KeepFieldInfo.Joiner joiner = KeepFieldInfo.newEmptyJoiner();
      keepInfoEffect.accept(joiner);
      for (ProguardKeepRuleBase rule : joiner.getRules()) {
        RootSetBlastRadiusForRule ruleBlastRadius =
            blastRadius.computeIfAbsent(rule, ignoreKey(RootSetBlastRadiusForRule::new));
        ruleBlastRadius.addMatchedField(field);
      }
    }

    @Override
    public void acceptKeepMethodInfo(
        DexMethod method, Consumer<? super KeepMethodInfo.Joiner> keepInfoEffect) {
      KeepMethodInfo.Joiner joiner = KeepMethodInfo.newEmptyJoiner();
      keepInfoEffect.accept(joiner);
      for (ProguardKeepRuleBase rule : joiner.getRules()) {
        RootSetBlastRadiusForRule ruleBlastRadius =
            blastRadius.computeIfAbsent(rule, ignoreKey(RootSetBlastRadiusForRule::new));
        ruleBlastRadius.addMatchedMethod(method);
      }
    }

    public RootSetBlastRadius build() {
      return new RootSetBlastRadius(blastRadius);
    }
  }
}
