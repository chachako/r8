// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.libanalyzer.proto.D8CompileResult;
import com.android.tools.r8.libanalyzer.proto.LibraryAnalysisResult;
import com.android.tools.r8.libanalyzer.proto.R8CompileResult;
import com.android.tools.r8.libanalyzer.utils.DexIndexedSizeConsumer;
import com.android.tools.r8.libanalyzer.utils.LibraryAnalyzerOptions;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutorService;

// TODO(b/479726064): Extend SanityCheck test to verify that the libanalyzer jar does not contain
//  any entries outside com/android/tools/r8/libanalyzer/.
// TODO(b/479726064): Add support for writing LibraryAnalyzer tests.
// TODO(b/479726064): If this ends up not being bundled into r8.jar, do we need this to have its own
//  Version.java or a --version?
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
    InternalD8CompileResult d8CompileResult = runD8(executorService);
    InternalR8CompileResult r8CompileResult = runR8(executorService);
    writeAnalysisResult(d8CompileResult, r8CompileResult);
  }

  private InternalD8CompileResult runD8(ExecutorService executorService) {
    DexIndexedSizeConsumer sizeConsumer = new DexIndexedSizeConsumer();
    D8Command.Builder commandBuilder =
        D8Command.builder(options.reporter).setProgramConsumer(sizeConsumer);
    configure(commandBuilder);
    try {
      D8.run(commandBuilder.build(), executorService);
    } catch (CompilationFailedException e) {
      options.reporter.warning(new ExceptionDiagnostic(e));
      options.reporter.clearAbort();
      return null;
    }
    return new InternalD8CompileResult(sizeConsumer.size());
  }

  private InternalR8CompileResult runR8(ExecutorService executorService) {
    DexIndexedSizeConsumer sizeConsumer = new DexIndexedSizeConsumer();
    R8Command.Builder commandBuilder =
        R8Command.builder(options.reporter)
            .addProguardConfiguration(List.of("-ignorewarnings"), Origin.unknown())
            .setProgramConsumer(sizeConsumer);
    configure(commandBuilder);
    try {
      R8.run(commandBuilder.build(), executorService);
    } catch (CompilationFailedException e) {
      options.reporter.warning(new ExceptionDiagnostic(e));
      options.reporter.clearAbort();
      return null;
    }
    return new InternalR8CompileResult(sizeConsumer.size());
  }

  private void writeAnalysisResult(
      InternalD8CompileResult d8CompileResult, InternalR8CompileResult r8CompileResult) {
    LibraryAnalysisResult.Builder resultBuilder = LibraryAnalysisResult.newBuilder();
    if (d8CompileResult != null) {
      resultBuilder.setD8CompileResult(
          D8CompileResult.newBuilder().setSizeBytes(d8CompileResult.size).build());
    }
    if (r8CompileResult != null) {
      resultBuilder.setR8CompileResult(
          R8CompileResult.newBuilder().setSizeBytes(r8CompileResult.size).build());
    }
    LibraryAnalysisResult result = resultBuilder.build();
    if (options.outputPath != null) {
      try (OutputStream output = Files.newOutputStream(options.outputPath)) {
        result.writeTo(output);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      System.out.println(result);
    }
  }

  private void configure(BaseCompilerCommand.Builder<?, ?> commandBuilder) {
    commandBuilder
        .setMode(CompilationMode.RELEASE)
        .setMinApiLevel(options.minApiLevel.getLevel(), options.minApiLevel.getMinor());
    app.getProgramResourceProviders().forEach(commandBuilder::addProgramResourceProvider);
    app.getClasspathResourceProviders().forEach(commandBuilder::addClasspathResourceProvider);
    app.getLibraryResourceProviders().forEach(commandBuilder::addLibraryResourceProvider);
  }

  private abstract static class CompileResult {

    final int size;

    CompileResult(int size) {
      this.size = size;
    }
  }

  private static class InternalD8CompileResult extends CompileResult {

    InternalD8CompileResult(int size) {
      super(size);
    }
  }

  private static class InternalR8CompileResult extends CompileResult {

    InternalR8CompileResult(int size) {
      super(size);
    }
  }
}
