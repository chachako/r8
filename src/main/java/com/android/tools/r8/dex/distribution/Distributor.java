// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public abstract class Distributor {
  protected final AppView<?> appView;
  protected final ApplicationWriter writer;
  protected final List<VirtualFile> virtualFiles = new ArrayList<>();

  Distributor(ApplicationWriter writer) {
    this.appView = writer.appView;
    this.writer = writer;
  }

  public abstract List<VirtualFile> run(Timing timing) throws ExecutionException;

  void addMarkers(VirtualFile virtualFile) {
    if (writer.markerStrings != null && !writer.markerStrings.isEmpty()) {
      for (DexString markerString : writer.markerStrings) {
        virtualFile.getTransaction().addMarkerString(markerString);
      }
      virtualFile.commitTransaction();
    }
  }
}
