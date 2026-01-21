// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.build.shrinker.r8integration;

import com.android.aapt.Resources;
import com.android.aapt.Resources.ConfigValue;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.FileReference;
import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Value;
import com.android.build.shrinker.ResourceTableUtilKt;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TypeModel {

  private final PackageModel parent;
  private final Resources.Type type;

  public TypeModel(PackageModel parent, Resources.Type type) {
    this.parent = parent;
    this.type = type;
  }

  public void forEachProtoXmlFileReference(BiConsumer<Integer, String> onResourceIdToFilePath) {
    forEachValue(
        (id, value) -> {
          // We don't have file references from compound resources, so only handle simple items.
          onItemWithPredicate(
              value,
              Item::hasFile,
              item -> {
                FileReference file = item.getFile();
                if (file.getType() == FileReference.Type.PROTO_XML) {
                  onResourceIdToFilePath.accept(id, file.getPath());
                }
              });
        });
  }

  private static void onItemWithPredicate(
      Value value, Predicate<Item> predicate, Consumer<Item> itemConsumer) {
    if (value.hasItem() && predicate.test(value.getItem())) {
      itemConsumer.accept(value.getItem());
    }
  }

  public void forEachValue(BiConsumer<Integer, Value> valueConsumer) {
    for (Entry entry : type.getEntryList()) {
      for (ConfigValue configValue : entry.getConfigValueList()) {
        if (configValue.hasValue()) {
          valueConsumer.accept(getId(entry), configValue.getValue());
        }
      }
    }
  }

  private int getId(Entry entry) {
    return ResourceTableUtilKt.toIdentifier(parent.getPackage(), type, entry);
  }
}
