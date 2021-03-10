// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.LegacyNestedGraphLens;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.utils.collections.EmptyBidirectionalOneToOneMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Set;

final class PublicizerLens extends LegacyNestedGraphLens {

  private final AppView<?> appView;
  private final Set<DexMethod> publicizedMethods;

  private PublicizerLens(AppView<?> appView, Set<DexMethod> publicizedMethods) {
    super(
        ImmutableMap.of(),
        ImmutableMap.of(),
        new EmptyBidirectionalOneToOneMap<>(),
        new EmptyBidirectionalOneToOneMap<>(),
        appView.graphLens(),
        appView.dexItemFactory());
    this.appView = appView;
    this.publicizedMethods = publicizedMethods;
  }

  @Override
  protected boolean isLegitimateToHaveEmptyMappings() {
    // This lens does not map any DexItem's at all.
    // It will just tweak invoke type for publicized methods from invoke-direct to invoke-virtual.
    return true;
  }

  @Override
  protected FieldLookupResult internalDescribeLookupField(FieldLookupResult previous) {
    return previous;
  }

  @Override
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context) {
    if (previous.getType() == Type.DIRECT && publicizedMethods.contains(previous.getReference())) {
      assert publicizedMethodIsPresentOnHolder(previous.getReference(), context);
      return MethodLookupResult.builder(this)
          .setReference(previous.getReference())
          .setPrototypeChanges(previous.getPrototypeChanges())
          .setType(Type.VIRTUAL)
          .build();
    }
    return previous;
  }

  private boolean publicizedMethodIsPresentOnHolder(DexMethod method, DexMethod context) {
    MethodLookupResult lookup = appView.graphLens().lookupMethod(method, context, Type.VIRTUAL);
    DexMethod signatureInCurrentWorld = lookup.getReference();
    DexClass clazz = appView.definitionFor(signatureInCurrentWorld.holder);
    assert clazz != null;
    DexEncodedMethod actualEncodedTarget = clazz.lookupVirtualMethod(signatureInCurrentWorld);
    assert actualEncodedTarget != null;
    assert actualEncodedTarget.isPublic();
    return true;
  }

  static PublicizedLensBuilder createBuilder() {
    return new PublicizedLensBuilder();
  }

  static class PublicizedLensBuilder {
    private final Set<DexMethod> publicizedMethods = Sets.newIdentityHashSet();

    private PublicizedLensBuilder() {}

    public GraphLens build(AppView appView) {
      if (publicizedMethods.isEmpty()) {
        return appView.graphLens();
      }
      return new PublicizerLens(appView, publicizedMethods);
    }

    public void add(DexMethod publicizedMethod) {
      publicizedMethods.add(publicizedMethod);
    }
  }
}
