// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assume;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class AssumeInfoCollection {

  private final AppView<?> appView;
  private final Map<DexField, AssumeInfo> fieldInfos;
  private final Map<DexMethod, AssumeMethodInfoCollection> methodInfos;

  AssumeInfoCollection(
      AppView<?> appView,
      Map<DexField, AssumeInfo> fieldInfos,
      Map<DexMethod, AssumeMethodInfoCollection> methodInfos) {
    assert fieldInfos.values().stream().noneMatch(AssumeInfo::isEmpty);
    assert methodInfos.values().stream().noneMatch(AssumeMethodInfoCollection::isEmpty);
    this.appView = appView;
    this.fieldInfos = fieldInfos;
    this.methodInfos = methodInfos;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean contains(DexClassAndMember<?, ?> member) {
    return member.isField()
        ? fieldInfos.containsKey(member.asField().getReference())
        : methodInfos.containsKey(member.asMethod().getReference());
  }

  public AssumeInfo getField(DexField field) {
    return fieldInfos.getOrDefault(field, AssumeInfo.empty());
  }

  public AssumeInfo getField(DexClassAndField field) {
    return getField(field.getReference());
  }

  public AssumeMethodInfoCollection getMethod(DexMethod method) {
    return methodInfos.getOrDefault(method, AssumeMethodInfoCollection.empty());
  }

  public AssumeInfo getMethod(DexMethod method, InvokeMethod invoke, ProgramMethod context) {
    return getMethod(method).lookup(appView, invoke, context);
  }

  public AssumeInfo getMethod(
      DexClassAndMethod method, InvokeMethod invoke, ProgramMethod context) {
    return getMethod(method.getReference(), invoke, context);
  }

  public boolean hasAssumeInfoThatMatches(DexMethod method, Predicate<AssumeInfo> predicate) {
    return getMethod(method).hasAssumeInfoThatMatches(predicate);
  }

  public boolean isEmpty() {
    return fieldInfos.isEmpty() && methodInfos.isEmpty();
  }

  public boolean isMaterializableInAllContexts(
      AppView<? extends AppInfoWithLiveness> appView, DexClassAndField field) {
    AbstractValue assumeValue = getField(field).getAssumeValue();
    return assumeValue.isSingleValue()
        && assumeValue.asSingleValue().isMaterializableInAllContexts(appView);
  }

  public boolean isSideEffectFree(DexField field) {
    return getField(field).isSideEffectFree();
  }

  public boolean isSideEffectFree(DexClassAndField field) {
    return isSideEffectFree(field.getReference());
  }

  public boolean isSideEffectFree(DexMethod method) {
    return getMethod(method).getUnconditionalInfo().isSideEffectFree();
  }

  public boolean isSideEffectFree(DexClassAndMethod method) {
    return isSideEffectFree(method.getReference());
  }

  public boolean neverInlineDueToAssume(
      InvokeMethod invoke,
      SingleResolutionResult<?> resolutionResult,
      DexClassAndMethod singleTarget) {
    return neverInlineDueToAssume(invoke.getInvokedMethod())
        || neverInlineDueToAssume(resolutionResult.getResolvedMethod().getReference())
        || (singleTarget != null && neverInlineDueToAssume(singleTarget.getReference()));
  }

  private boolean neverInlineDueToAssume(DexMethod target) {
    return hasAssumeInfoThatMatches(
        target,
        assumeInfo -> assumeInfo.isSideEffectFree() || !assumeInfo.getAssumeValue().isUnknown());
  }

  public AssumeInfoCollection rewrittenWithLens(
      AppView<?> appView, GraphLens graphLens, GraphLens appliedLens, Timing timing) {
    return timing.time(
        "Rewrite AssumeInfoCollection", () -> rewrittenWithLens(appView, graphLens, appliedLens));
  }

  private AssumeInfoCollection rewrittenWithLens(
      AppView<?> appView, GraphLens graphLens, GraphLens appliedLens) {
    Map<DexField, AssumeInfo> rewrittenFieldInfos = new IdentityHashMap<>();
    fieldInfos.forEach(
        (field, info) -> {
          DexField rewrittenField = graphLens.getRenamedFieldSignature(field, appliedLens);
          AssumeInfo rewrittenInfo = info.rewrittenWithLens(appView, graphLens);
          assert !rewrittenInfo.isEmpty();
          rewrittenFieldInfos.put(rewrittenField, rewrittenInfo);
        });
    Map<DexMethod, AssumeMethodInfoCollection> rewrittenMethodInfos = new IdentityHashMap<>();
    methodInfos.forEach(
        (method, info) -> {
          DexMethod rewrittenMethod = graphLens.getRenamedMethodSignature(method, appliedLens);
          AssumeMethodInfoCollection rewrittenInfo = info.rewrittenWithLens(appView, graphLens);
          assert !rewrittenInfo.isEmpty();
          rewrittenMethodInfos.put(rewrittenMethod, rewrittenInfo);
        });
    return new AssumeInfoCollection(appView, rewrittenFieldInfos, rewrittenMethodInfos);
  }

  public void unsetConditionalAssumeRules() {
    Iterator<Entry<DexMethod, AssumeMethodInfoCollection>> iterator =
        methodInfos.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<DexMethod, AssumeMethodInfoCollection> entry = iterator.next();
      AssumeMethodInfoCollection rewrittenInfo = entry.getValue().unsetConditionalAssumeRules();
      if (rewrittenInfo.isEmpty()) {
        iterator.remove();
      } else {
        entry.setValue(rewrittenInfo);
      }
    }
  }

  public AssumeInfoCollection withoutPrunedItems(PrunedItems prunedItems, Timing timing) {
    timing.begin("Prune AssumeInfoCollection");
    Map<DexField, AssumeInfo> rewrittenFieldInfos = new IdentityHashMap<>();
    fieldInfos.forEach(
        (field, info) -> {
          if (!prunedItems.isRemoved(field)) {
            AssumeInfo rewrittenInfo = info.withoutPrunedItems(prunedItems);
            if (!rewrittenInfo.isEmpty()) {
              rewrittenFieldInfos.put(field, rewrittenInfo);
            }
          }
        });
    Map<DexMethod, AssumeMethodInfoCollection> rewrittenMethodInfos = new IdentityHashMap<>();
    methodInfos.forEach(
        (method, info) -> {
          if (!prunedItems.isRemoved(method)) {
            AssumeMethodInfoCollection rewrittenInfo = info.withoutPrunedItems(prunedItems);
            if (!rewrittenInfo.isEmpty()) {
              rewrittenMethodInfos.put(method, rewrittenInfo);
            }
          }
        });
    AssumeInfoCollection result =
        new AssumeInfoCollection(appView, rewrittenFieldInfos, rewrittenMethodInfos);
    timing.end();
    return result;
  }

  public static class Builder {

    private final Map<DexField, AssumeInfo.Builder> fieldInfos = new ConcurrentHashMap<>();
    private final Map<DexMethod, AssumeMethodInfoCollection.Builder> methodInfos =
        new ConcurrentHashMap<>();

    public Builder applyIf(boolean condition, Consumer<Builder> consumer) {
      if (condition) {
        consumer.accept(this);
      }
      return this;
    }

    public AssumeInfo.Builder getOrCreateFieldInfo(DexField field) {
      return fieldInfos.computeIfAbsent(field, ignoreKey(AssumeInfo::builder));
    }

    public boolean hasMethodInfo(DexMethod method) {
      return methodInfos.containsKey(method);
    }

    public AssumeMethodInfoCollection.Builder getOrCreateMethodInfo(DexMethod method) {
      return methodInfos.computeIfAbsent(method, ignoreKey(AssumeMethodInfoCollection::builder));
    }

    public boolean isEmpty() {
      return fieldInfos.isEmpty() && methodInfos.isEmpty();
    }

    public AssumeInfoCollection build(AppView<?> appView) {
      return new AssumeInfoCollection(
          appView,
          MapUtils.newIdentityHashMap(
              builder ->
                  fieldInfos.forEach(
                      (field, infoBuilder) -> {
                        AssumeInfo info = infoBuilder.build();
                        if (!info.isEmpty()) {
                          builder.accept(field, info);
                        }
                      }),
              fieldInfos.size()),
          MapUtils.newIdentityHashMap(
              builder ->
                  methodInfos.forEach(
                      (reference, infoBuilder) -> {
                        AssumeMethodInfoCollection info = infoBuilder.build();
                        if (!info.isEmpty()) {
                          builder.accept(reference, info);
                        }
                      }),
              methodInfos.size()));
    }
  }
}
