// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer.utils;

import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import java.nio.file.Path;

public class LibraryAnalyzerOptions {

  public final Path aarPath;
  public final Reporter reporter;

  private ThreadingModule lazyThreadingModule = null;
  public int threadCount = ThreadUtils.NOT_SPECIFIED;

  public LibraryAnalyzerOptions(Path aarPath, Reporter reporter) {
    this.aarPath = aarPath;
    this.reporter = reporter;
  }

  public ThreadingModule getThreadingModule() {
    if (lazyThreadingModule == null) {
      lazyThreadingModule = ThreadingModule.Loader.load().create();
    }
    return lazyThreadingModule;
  }
}
