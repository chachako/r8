// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

public class TopTypeElement extends TypeElement {
  private static final TopTypeElement INSTANCE = new TopTypeElement();

  @Override
  public Nullability nullability() {
    return Nullability.maybeNull();
  }

  static TopTypeElement getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isTop() {
    return true;
  }

  @Override
  public DexType toDexType(DexItemFactory factory) {
    assert false : this;
    return null;
  }

  @Override
  public String toString() {
    return "TOP (everything)";
  }

  @Override
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(INSTANCE);
  }
}
