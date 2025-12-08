// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assume;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.utils.ObjectUtils;
import java.util.Objects;

public class AssumeMethodInfoCollection {

  private static final AssumeMethodInfoCollection EMPTY =
      new AssumeMethodInfoCollection(AssumeInfo.empty());

  private final AssumeInfo unconditionalInfo;

  private AssumeMethodInfoCollection(AssumeInfo unconditionalInfo) {
    this.unconditionalInfo = unconditionalInfo;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static AssumeMethodInfoCollection empty() {
    return EMPTY;
  }

  public AssumeInfo getUnconditionalInfo() {
    return unconditionalInfo;
  }

  public boolean isEmpty() {
    return unconditionalInfo == null;
  }

  public AssumeInfo lookup(InvokeMethod invoke) {
    return getUnconditionalInfo();
  }

  public AssumeMethodInfoCollection rewrittenWithLens(AppView<?> appView, GraphLens graphLens) {
    AssumeInfo rewrittenUnconditionalInfo =
        unconditionalInfo != null ? unconditionalInfo.rewrittenWithLens(appView, graphLens) : null;
    return ObjectUtils.notIdentical(rewrittenUnconditionalInfo, unconditionalInfo)
        ? new AssumeMethodInfoCollection(rewrittenUnconditionalInfo)
        : this;
  }

  public AssumeMethodInfoCollection withoutPrunedItems(PrunedItems prunedItems) {
    AssumeInfo rewrittenUnconditionalInfo =
        unconditionalInfo != null ? unconditionalInfo.withoutPrunedItems(prunedItems) : null;
    return ObjectUtils.notIdentical(rewrittenUnconditionalInfo, unconditionalInfo)
        ? new AssumeMethodInfoCollection(rewrittenUnconditionalInfo)
        : this;
  }

  public static class Builder {

    private volatile AssumeInfo.Builder unconditionalInfo;

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

    public boolean isEqualTo(Builder builder) {
      if (Objects.isNull(unconditionalInfo) != Objects.isNull(builder.unconditionalInfo)) {
        return false;
      }
      if (unconditionalInfo != null && !unconditionalInfo.isEqualTo(builder.unconditionalInfo)) {
        return false;
      }
      return true;
    }

    public Builder meet(Builder builder) {
      if (builder.unconditionalInfo != null) {
        getOrCreateUnconditionalInfo().meet(builder.unconditionalInfo);
      }
      return this;
    }

    public AssumeMethodInfoCollection build() {
      return new AssumeMethodInfoCollection(
          unconditionalInfo != null ? unconditionalInfo.build() : null);
    }
  }
}
