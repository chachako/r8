// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.arithmetic.AbstractCalculator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FlowGraphStateProvider;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ObjectUtils;

public class ComputationTreeLogicalBinopOrNode extends ComputationTreeLogicalBinopNode {

  private ComputationTreeLogicalBinopOrNode(ComputationTreeNode left, ComputationTreeNode right) {
    super(left, right);
  }

  public static ComputationTreeNode create(
      AppView<AppInfoWithLiveness> appView, ComputationTreeNode left, ComputationTreeNode right) {
    if (left.isUnknown() && right.isUnknown()) {
      return AbstractValue.unknown();
    }
    ComputationTreeLogicalBinopOrNode orNode = new ComputationTreeLogicalBinopOrNode(left, right);
    if (!left.hasBaseInFlow() && !right.hasBaseInFlow()) {
      return orNode.evaluate(appView, FlowGraphStateProvider.unreachable());
    }
    return orNode;
  }

  @Override
  public AbstractValue evaluate(
      AppView<AppInfoWithLiveness> appView, FlowGraphStateProvider flowGraphStateProvider) {
    assert getNumericType().isInt();
    AbstractValue leftValue = left.evaluate(appView, flowGraphStateProvider);
    if (leftValue.isBottom()) {
      return leftValue;
    }
    AbstractValue rightValue = right.evaluate(appView, flowGraphStateProvider);
    if (rightValue.isBottom()) {
      return rightValue;
    }
    return AbstractCalculator.orIntegers(appView, leftValue, rightValue);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ComputationTreeLogicalBinopOrNode)) {
      return false;
    }
    ComputationTreeLogicalBinopOrNode node = (ComputationTreeLogicalBinopOrNode) obj;
    return internalIsEqualTo(node);
  }

  @Override
  public int computeHashCode() {
    return ObjectUtils.hashLLL(getClass(), left, right);
  }

  @Override
  public String toString() {
    return left.toStringWithParenthesis() + " | " + right.toStringWithParenthesis();
  }
}
