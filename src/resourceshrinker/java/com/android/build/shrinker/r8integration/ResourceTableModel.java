// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.build.shrinker.r8integration;

import com.android.aapt.Resources.ResourceTable;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A view on top of a aapt proto resource table to allow for more structured iteration and updates.
 * The underlying resource table has a setup where we have:
 *
 * <pre>
 * ResourceTable
 *  -> Package
 *    -> Type
 *      -> Entry
 *        -> Config
 *          -> Value
 *        ->...
 *      -> ...
 *    -> ...
 *  -> ...
 * </pre>
 *
 * So every ResourceTable can have multiple packages, which each can have many types (string,
 * layout, ...), which can each have many entries. Every entry corresponds exactly to one resource
 * id (e.g., 0x7f020003), but can have many values, one for each configuration that it is specified
 * for. If a string resource has 14 translations, then the entry for that string will have 14
 * configs with a string value each.
 *
 * <p>By design, we do not model the underlying entries with explicit model objects to avoid the
 * memory overhead and object creation cost.
 */
public class ResourceTableModel {

  private final ResourceTable resourceTable;
  private final List<PackageModel> packages;

  public ResourceTableModel(ResourceTable resourceTable) {
    this.resourceTable = resourceTable;
    packages =
        resourceTable.getPackageList().stream()
            .map(aPackage -> new PackageModel(aPackage))
            .collect(Collectors.toList());
  }

  public void forEachProtoXmlFileReference(BiConsumer<Integer, String> onResourceIdToFilePath) {
    packages.forEach(
        packageModel -> packageModel.forEachProtoXmlFileReference(onResourceIdToFilePath));
  }

  public void forEachPackage(Consumer<PackageModel> consumer) {
    packages.forEach(consumer);
  }

  public ResourceTable getResourceTable() {
    return resourceTable;
  }
}
