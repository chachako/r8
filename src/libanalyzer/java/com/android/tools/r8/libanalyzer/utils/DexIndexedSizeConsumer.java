// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer.utils;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import java.util.Set;

public class DexIndexedSizeConsumer implements DexIndexedConsumer {

  private int size = 0;
  private boolean finished = false;

  @Override
  public void accept(
      int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
    size += data.getLength();
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    finished = true;
  }

  public int size() {
    assert finished;
    return size;
  }
}
