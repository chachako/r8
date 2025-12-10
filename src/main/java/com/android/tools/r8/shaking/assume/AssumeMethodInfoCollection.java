// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assume;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.shaking.ProguardMemberRuleValue;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.ObjectUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

public class AssumeMethodInfoCollection {

  private static final AssumeMethodInfoCollection EMPTY =
      new AssumeMethodInfoCollection(AssumeInfo.empty(), Collections.emptyMap());

  private final AssumeInfo unconditionalInfo;
  private final Map<List<ProguardMemberRuleValue>, AssumeInfo> conditionalInfos;

  private AssumeMethodInfoCollection(AssumeInfo unconditionalInfo) {
    this(unconditionalInfo, Collections.emptyMap());
  }

  private AssumeMethodInfoCollection(
      AssumeInfo unconditionalInfo,
      Map<List<ProguardMemberRuleValue>, AssumeInfo> conditionalInfos) {
    this.unconditionalInfo = unconditionalInfo;
    this.conditionalInfos = conditionalInfos;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static AssumeMethodInfoCollection empty() {
    return EMPTY;
  }

  public AssumeInfo getUnconditionalInfo() {
    return unconditionalInfo != null ? unconditionalInfo : AssumeInfo.empty();
  }

  public boolean hasAssumeInfoThatMatches(Predicate<AssumeInfo> predicate) {
    if (unconditionalInfo != null && predicate.test(unconditionalInfo)) {
      return true;
    }
    for (AssumeInfo assumeInfo : conditionalInfos.values()) {
      if (predicate.test(assumeInfo)) {
        return true;
      }
    }
    return false;
  }

  public boolean isEmpty() {
    return unconditionalInfo == null && conditionalInfos.isEmpty();
  }

  public AssumeInfo lookup(AppView<?> appView, InvokeMethod invoke, ProgramMethod context) {
    AssumeInfo result = unconditionalInfo;
    for (Entry<List<ProguardMemberRuleValue>, AssumeInfo> entry : conditionalInfos.entrySet()) {
      List<ProguardMemberRuleValue> condition = entry.getKey();
      if (test(appView, invoke, condition)) {
        AssumeInfo assumeInfo = entry.getValue();
        if (result != null && !result.equals(assumeInfo)) {
          throw appView
              .reporter()
              .fatalError(
                  "Call to "
                      + invoke.getInvokedMethod().toSmaliString()
                      + " in "
                      + context.getReference().toSmaliString()
                      + " matches different assume rules");
        }
        result = assumeInfo;
      }
    }
    return result != null ? result : AssumeInfo.empty();
  }

  private boolean test(
      AppView<?> appView, InvokeMethod invoke, List<ProguardMemberRuleValue> conditions) {
    for (int parameterIndex = 0; parameterIndex < conditions.size(); parameterIndex++) {
      ProguardMemberRuleValue condition = conditions.get(parameterIndex);
      if (condition != null
          && !condition.test(appView, invoke.getArgumentForParameter(parameterIndex))) {
        return false;
      }
    }
    return true;
  }

  public AssumeMethodInfoCollection rewrittenWithLens(AppView<?> appView, GraphLens graphLens) {
    AssumeInfo rewrittenUnconditionalInfo =
        unconditionalInfo != null ? unconditionalInfo.rewrittenWithLens(appView, graphLens) : null;
    if (conditionalInfos.isEmpty()) {
      return ObjectUtils.identical(rewrittenUnconditionalInfo, unconditionalInfo)
          ? new AssumeMethodInfoCollection(rewrittenUnconditionalInfo, conditionalInfos)
          : this;
    }
    Map<List<ProguardMemberRuleValue>, AssumeInfo> rewrittenConditionalInfos =
        MapUtils.transform(
            conditionalInfos,
            HashMap::new,
            Function.identity(),
            assumeInfo -> assumeInfo.rewrittenWithLens(appView, graphLens),
            (condition, value, otherValue) -> value);
    return new AssumeMethodInfoCollection(rewrittenUnconditionalInfo, rewrittenConditionalInfos);
  }

  public AssumeMethodInfoCollection unsetConditionalAssumeRules() {
    return conditionalInfos.isEmpty() ? this : new AssumeMethodInfoCollection(unconditionalInfo);
  }

  public AssumeMethodInfoCollection withoutPrunedItems(PrunedItems prunedItems) {
    AssumeInfo rewrittenUnconditionalInfo =
        unconditionalInfo != null ? unconditionalInfo.withoutPrunedItems(prunedItems) : null;
    if (conditionalInfos.isEmpty()) {
      return ObjectUtils.notIdentical(rewrittenUnconditionalInfo, unconditionalInfo)
          ? new AssumeMethodInfoCollection(rewrittenUnconditionalInfo, conditionalInfos)
          : this;
    }
    Map<List<ProguardMemberRuleValue>, AssumeInfo> rewrittenConditionalInfos =
        MapUtils.transform(
            conditionalInfos,
            HashMap::new,
            Function.identity(),
            assumeInfo -> assumeInfo.withoutPrunedItems(prunedItems),
            (condition, value, otherValue) -> value);
    return new AssumeMethodInfoCollection(rewrittenUnconditionalInfo, rewrittenConditionalInfos);
  }

  public static class Builder {

    private volatile AssumeInfo.Builder unconditionalInfo;

    private final Map<List<ProguardMemberRuleValue>, AssumeInfo.Builder> conditionalInfos =
        new ConcurrentHashMap<>();

    public AssumeInfo.Builder getOrCreateUnconditionalInfo() {
      if (unconditionalInfo == null) {
        synchronized (this) {
          if (unconditionalInfo == null) {
            unconditionalInfo = AssumeInfo.builder();
          }
        }
      }
      return unconditionalInfo;
    }

    public AssumeInfo.Builder getOrCreateConditionalInfo(List<ProguardMemberRuleValue> condition) {
      return conditionalInfos.computeIfAbsent(condition, ignoreKey(AssumeInfo::builder));
    }

    public boolean isEqualTo(Builder builder) {
      if (Objects.isNull(unconditionalInfo) != Objects.isNull(builder.unconditionalInfo)) {
        return false;
      }
      if (unconditionalInfo != null && !unconditionalInfo.isEqualTo(builder.unconditionalInfo)) {
        return false;
      }
      return conditionalInfos.equals(builder.conditionalInfos);
    }

    public Builder meet(Builder builder) {
      if (builder.unconditionalInfo != null) {
        getOrCreateUnconditionalInfo().meet(builder.unconditionalInfo);
      }
      return this;
    }

    public AssumeMethodInfoCollection build() {
      Map<List<ProguardMemberRuleValue>, AssumeInfo> materializedConditionalInfos =
          conditionalInfos.isEmpty()
              ? Collections.emptyMap()
              : MapUtils.transform(
                  conditionalInfos,
                  HashMap::new,
                  Function.identity(),
                  AssumeInfo.Builder::build,
                  (condition, value, otherValue) -> value);
      return new AssumeMethodInfoCollection(
          unconditionalInfo != null ? unconditionalInfo.build() : null,
          materializedConditionalInfos);
    }
  }
}
