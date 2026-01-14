// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import java.util.function.Consumer;

public class EmptyKeepInfoCollectionEventConsumer implements KeepInfoCollectionEventConsumer {

  @Override
  public void acceptKeepClassInfo(
      DexType type, Consumer<? super KeepClassInfo.Joiner> keepInfoEffect) {}

  @Override
  public void acceptKeepFieldInfo(
      DexField field, Consumer<? super KeepFieldInfo.Joiner> keepInfoEffect) {}

  @Override
  public void acceptKeepMethodInfo(
      DexMethod method, Consumer<? super KeepMethodInfo.Joiner> keepInfoEffect) {}
}
