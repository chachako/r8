// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.libanalyzer.utils.LibraryAnalyzerOptions;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutorService;

// TODO(b/479726064): Extend SanityCheck test to verify that the libanalyzer jar does not contain
//  any entries outside com/android/tools/r8/libanalyzer/.
// TODO(b/479726064): Add support for writing LibraryAnalyzer tests.
// TODO(b/479726064): If this ends up not being bundled into r8.jar, do we need this to have its own
//  Version.java or a --version?
// TODO(b/479726064): Configure error prone.
@KeepForApi
public class LibraryAnalyzer {

  private final LibraryAnalyzerOptions options;

  private LibraryAnalyzer(LibraryAnalyzerOptions options) {
    this.options = options;
  }

  public static void run(LibraryAnalyzerCommand command) throws CompilationFailedException {
    LibraryAnalyzerOptions options = command.getInternalOptions();
    run(
        command,
        ThreadUtils.getExecutorService(options.threadCount, options.getThreadingModule()),
        options);
  }

  public static void run(LibraryAnalyzerCommand command, ExecutorService executorService)
      throws CompilationFailedException {
    run(command, executorService, command.getInternalOptions());
  }

  private static void run(
      LibraryAnalyzerCommand command,
      ExecutorService executorService,
      LibraryAnalyzerOptions options)
      throws CompilationFailedException {
    if (command.isPrintHelp()) {
      System.out.println(LibraryAnalyzerCommandParser.getUsageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      // TODO(b/479726064): Read the version from a field.
      System.out.println("LibraryAnalyzer 0.0.1");
      return;
    }
    ExceptionUtils.withR8CompilationHandler(
        options.reporter,
        () -> {
          new LibraryAnalyzer(options).run(executorService);
        });
  }

  private void run(ExecutorService executorService) {
    // Implementation will go here.
    System.out.println("Running LibraryAnalyzer with AAR: " + options.aarPath);
  }
}
