// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.structural.RepresentativeMap;
import com.google.common.hash.Hasher;

/**
 * Definition of a synthetic method item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticMethodDefinition
    extends SyntheticDefinition<SyntheticMethodReference, SyntheticMethodDefinition> {

  private final ProgramMethod method;

  SyntheticMethodDefinition(SyntheticKind kind, SynthesizingContext context, ProgramMethod method) {
    super(kind, context);
    this.method = method;
  }

  public ProgramMethod getMethod() {
    return method;
  }

  @Override
  SyntheticMethodReference toReference() {
    return new SyntheticMethodReference(getKind(), getContext(), method.getReference());
  }

  @Override
  DexProgramClass getHolder() {
    return method.getHolder();
  }

  @Override
  void internalComputeHash(Hasher hasher, RepresentativeMap map) {
    method.getDefinition().hashWithTypeEquivalence(hasher, map);
  }

  @Override
  int internalCompareTo(SyntheticMethodDefinition other, RepresentativeMap map) {
    return method.getDefinition().compareWithTypeEquivalenceTo(other.method.getDefinition(), map);
  }

  @Override
  public boolean isValid() {
    return SyntheticMethodBuilder.isValidSyntheticMethod(method.getDefinition());
  }

  @Override
  public String toString() {
    return "SyntheticMethodDefinition{" + method + '}';
  }
}
