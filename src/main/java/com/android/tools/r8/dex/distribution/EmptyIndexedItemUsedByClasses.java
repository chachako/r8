// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import java.util.Map;
import java.util.Set;

public class EmptyIndexedItemUsedByClasses implements ClassUseCollector {
  @Override
  public void collectClassDependencies(DexProgramClass clazz) {
    // Do nothing.
  }

  @Override
  public void collectClassDependenciesDone() {
    // Do nothing.
  }

  @Override
  public void clear() {
    // DO nothing.
  }

  @Override
  public Map<DexString, Set<DexProgramClass>> getStringsUse() {
    assert false;
    return null;
  }

  @Override
  public Map<DexType, Set<DexProgramClass>> getTypesUse() {
    assert false;
    return null;
  }

  @Override
  public Map<DexProto, Set<DexProgramClass>> getProtosUse() {
    assert false;
    return null;
  }

  @Override
  public Map<DexField, Set<DexProgramClass>> getFieldsUse() {
    assert false;
    return null;
  }

  @Override
  public Map<DexMethod, Set<DexProgramClass>> getMethodsUse() {
    assert false;
    return null;
  }

  @Override
  public Map<DexCallSite, Set<DexProgramClass>> getCallSitesUse() {
    assert false;
    return null;
  }

  @Override
  public Map<DexMethodHandle, Set<DexProgramClass>> getMethodHandlesUse() {
    assert false;
    return null;
  }
}
