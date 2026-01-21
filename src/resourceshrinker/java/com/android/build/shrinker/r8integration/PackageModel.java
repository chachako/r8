// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.build.shrinker.r8integration;

import com.android.aapt.Resources.Package;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class PackageModel {

  private final Package aPackage;
  private final List<TypeModel> types;

  public PackageModel(Package aPackage) {
    this.aPackage = aPackage;
    types =
        aPackage.getTypeList().stream()
            .map(type -> new TypeModel(this, type))
            .collect(Collectors.toList());
  }

  public void forEachProtoXmlFileReference(BiConsumer<Integer, String> onResourceIdToFilePath) {
    types.forEach(type -> type.forEachProtoXmlFileReference(onResourceIdToFilePath));
  }

  public Package getPackage() {
    return aPackage;
  }

  public String getName() {
    return aPackage.getPackageName();
  }
}
