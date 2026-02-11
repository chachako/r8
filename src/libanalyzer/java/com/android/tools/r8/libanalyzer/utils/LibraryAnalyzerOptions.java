// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer.utils;

import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;

public class LibraryAnalyzerOptions {

  public final Path blastRadiusOutputPath;
  public final AndroidApiLevel minApiLevel;
  public final Path outputPath;
  public final Reporter reporter;
  public final int threadCount;

  private ThreadingModule lazyThreadingModule = null;

  public LibraryAnalyzerOptions(
      Path blastRadiusOutputPath,
      AndroidApiLevel minApiLevel,
      Path outputPath,
      Reporter reporter,
      int threadCount) {
    this.blastRadiusOutputPath = blastRadiusOutputPath;
    this.minApiLevel = minApiLevel;
    this.outputPath = outputPath;
    this.reporter = reporter;
    this.threadCount = threadCount;
  }

  public ThreadingModule getThreadingModule() {
    if (lazyThreadingModule == null) {
      lazyThreadingModule = ThreadingModule.Loader.load().create();
    }
    return lazyThreadingModule;
  }
}
