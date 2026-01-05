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

public interface ClassUseCollector {

  void collectClassDependencies(DexProgramClass clazz);

  void collectClassDependenciesDone();

  void clear();

  Map<DexString, Set<DexProgramClass>> getStringsUse();

  Map<DexType, Set<DexProgramClass>> getTypesUse();

  Map<DexProto, Set<DexProgramClass>> getProtosUse();

  Map<DexField, Set<DexProgramClass>> getFieldsUse();

  Map<DexMethod, Set<DexProgramClass>> getMethodsUse();

  Map<DexCallSite, Set<DexProgramClass>> getCallSitesUse();

  Map<DexMethodHandle, Set<DexProgramClass>> getMethodHandlesUse();
}
