// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

/**
 * Represents a computation tree with no open variables other than the arguments of a given method.
 */
abstract class ComputationTreeBaseNode implements ComputationTreeNode {

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();
}
