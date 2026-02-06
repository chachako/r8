// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.libanalyzer.utils.DexIndexedSizeConsumer;
import com.android.tools.r8.libanalyzer.utils.LibraryAnalyzerOptions;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AndroidApp;
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

  private final AndroidApp app;
  private final LibraryAnalyzerOptions options;

  private LibraryAnalyzer(AndroidApp app, LibraryAnalyzerOptions options) {
    this.app = app;
    this.options = options;
  }

  public static void main(String[] args) {
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }

  private static void run(String[] args) throws CompilationFailedException {
    LibraryAnalyzerCommand.Builder builder =
        LibraryAnalyzerCommandParser.parse(args, CommandLineOrigin.INSTANCE);
    run(builder.build());
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
        () -> new LibraryAnalyzer(command.getApp(), options).run(executorService));
  }

  private void run(ExecutorService executorService) {
    D8RunResult runResult = runD8(executorService);
    // TODO(b/479726064): Write to protobuf.
    System.out.println("D8=" + runResult.size);
  }

  private D8RunResult runD8(ExecutorService executorService) {
    DexIndexedSizeConsumer sizeConsumer = new DexIndexedSizeConsumer();
    D8Command.Builder commandBuilder =
        D8Command.builder(options.reporter)
            .setMode(CompilationMode.RELEASE)
            .setMinApiLevel(options.minApiLevel.getLevel(), options.minApiLevel.getMinor())
            .setProgramConsumer(sizeConsumer);
    app.getProgramResourceProviders().forEach(commandBuilder::addProgramResourceProvider);
    app.getClasspathResourceProviders().forEach(commandBuilder::addClasspathResourceProvider);
    app.getLibraryResourceProviders().forEach(commandBuilder::addLibraryResourceProvider);
    try {
      D8.run(commandBuilder.build(), executorService);
    } catch (CompilationFailedException e) {
      throw new CompilationError("D8 compilation failed", e);
    }
    return new D8RunResult(sizeConsumer.size());
  }

  private static class D8RunResult {

    private final int size;

    D8RunResult(int size) {
      this.size = size;
    }
  }
}
