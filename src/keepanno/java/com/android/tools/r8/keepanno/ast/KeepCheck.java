// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.proto.KeepSpecProtos;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.Check;
import com.android.tools.r8.keepanno.proto.KeepSpecProtos.CheckKind;

public class KeepCheck extends KeepDeclaration {

  public enum KeepCheckKind {
    REMOVED,
    OPTIMIZED_OUT
  }

  public static class Builder {

    private KeepEdgeMetaInfo metaInfo = KeepEdgeMetaInfo.none();
    private KeepCheckKind kind = KeepCheckKind.REMOVED;
    private KeepBindings bindings = KeepBindings.none();
    private KeepBindingReference itemReference;

    public Builder applyProto(KeepSpecProtos.Check check, KeepSpecVersion version) {
      KeepEdgeMetaInfo.builder().applyProto(check.getMetaInfo(), version).build();
      if (check.getKind() == CheckKind.CHECK_OPTIMIZED_OUT) {
        setKind(KeepCheckKind.OPTIMIZED_OUT);
      } else {
        assert check.getKind() == CheckKind.CHECK_REMOVED;
        setKind(KeepCheckKind.REMOVED);
      }
      KeepBindings.Builder bindingsBuilder = KeepBindings.builder().applyProto(check.getBindings());
      setBindings(bindingsBuilder.build());
      setItemReference(
          bindingsBuilder.getBindingReferenceForUserBinding(check.getItem().getName()));
      return this;
    }

    public Builder setMetaInfo(KeepEdgeMetaInfo metaInfo) {
      this.metaInfo = metaInfo;
      return this;
    }

    public Builder setKind(KeepCheckKind kind) {
      this.kind = kind;
      return this;
    }

    public Builder setBindings(KeepBindings bindings) {
      this.bindings = bindings;
      return this;
    }

    public Builder setItemReference(KeepBindingReference itemReference) {
      this.itemReference = itemReference;
      return this;
    }

    public KeepCheck build() {
      if (itemReference == null) {
        throw new KeepEdgeException("KeepCheck must have an item pattern.");
      }

      KeepBindingsNormalizer normalizer = KeepBindingsNormalizer.create(bindings);
      itemReference =
          normalizer.registerAndNormalizeReference(
              itemReference,
              itemReference,
              (newItemReference, oldItemReference) -> newItemReference);

      return new KeepCheck(metaInfo, kind, normalizer.buildBindings(), itemReference);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  private final KeepEdgeMetaInfo metaInfo;
  private final KeepCheckKind kind;
  private final KeepBindings bindings;
  private final KeepBindingReference itemReference;

  private KeepCheck(
      KeepEdgeMetaInfo metaInfo,
      KeepCheckKind kind,
      KeepBindings bindings,
      KeepBindingReference itemReference) {
    this.metaInfo = metaInfo;
    this.kind = kind;
    this.bindings = bindings;
    this.itemReference = itemReference;
  }

  @Override
  public KeepCheck asKeepCheck() {
    return this;
  }

  public KeepEdgeMetaInfo getMetaInfo() {
    return metaInfo;
  }

  public KeepCheckKind getKind() {
    return kind;
  }

  public KeepBindings getBindings() {
    return bindings;
  }

  public KeepItemPattern getItemPattern() {
    return bindings.get(itemReference).getItem();
  }

  @Override
  public String toString() {
    return "KeepCheck{kind=" + kind + ", item=" + itemReference + "}";
  }

  public Check.Builder buildCheckProto() {
    return Check.newBuilder()
        .setMetaInfo(getMetaInfo().buildProto())
        .setBindings(getBindings().buildProto())
        .setItem(itemReference.buildProto())
        .setKind(
            kind == KeepCheckKind.REMOVED
                ? KeepSpecProtos.CheckKind.CHECK_REMOVED
                : KeepSpecProtos.CheckKind.CHECK_OPTIMIZED_OUT);
  }
}
