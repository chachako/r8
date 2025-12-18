// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlowComparator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlowKind;

/**
 * Represents a computation tree with no open variables other than the arguments of a given method.
 */
abstract class ComputationTreeBaseNode implements ComputationTreeNode {

  private static final int NOT_COMPUTED_HASH_VALUE = -1;
  private static final int SENTINEL_HASH_VALUE = 0;

  private volatile int hash = NOT_COMPUTED_HASH_VALUE;

  @Override
  public InFlowKind getKind() {
    return InFlowKind.ABSTRACT_COMPUTATION;
  }

  @Override
  public int internalCompareToSameKind(InFlow inFlow, InFlowComparator comparator) {
    SourcePosition position = comparator.getComputationTreePosition(this);
    SourcePosition otherPosition =
        comparator.getComputationTreePosition(inFlow.asAbstractComputation());
    return position.compareTo(otherPosition);
  }

  @Override
  public final boolean isComputationLeaf() {
    return false;
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public final int hashCode() {
    int cache = hash;
    if (cache == NOT_COMPUTED_HASH_VALUE) {
      cache = computeHashCode();
      if (cache == NOT_COMPUTED_HASH_VALUE) {
        cache = SENTINEL_HASH_VALUE;
      }
      hash = cache;
    }
    return cache;
  }

  public abstract int computeHashCode();

  @Override
  public abstract String toString();
}
