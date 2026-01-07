// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.dex.VirtualFile.VirtualFileIndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexProgramClass;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.Set;

public class IndexedItemTransactionWithoutReferenceCounting extends IndexedItemTransaction {

  public IndexedItemTransactionWithoutReferenceCounting(
      VirtualFileIndexedItemCollection base, AppView<?> appView) {
    super(base, appView);
  }

  @Override
  <T extends DexItem> boolean addItemWithReferenceCount(
      T item,
      Reference2IntMap<T> baseSet,
      Reference2IntMap<T> transactionSet,
      Set<T> transactionCurrentClassSet) {
    if (baseSet.containsKey(item) || transactionSet.containsKey(item)) {
      return false;
    }
    transactionSet.put(item, NO_REF_COUNT);
    return true;
  }

  @Override
  public void setupForTracing(DexProgramClass clazz) {
    assert currentClass == null;
    currentClass = clazz;
  }

  @Override
  public void tearDownAfterTracing() {
    currentClass = null;
  }
}
