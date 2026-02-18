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
import com.android.tools.r8.Version;
import com.android.tools.r8.blastradius.BlastRadiusKeepRuleClassifier;
import com.android.tools.r8.blastradius.RootSetBlastRadius;
import com.android.tools.r8.blastradius.RootSetBlastRadiusForRule;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.libanalyzer.proto.ConfigurationSummary;
import com.android.tools.r8.libanalyzer.proto.D8CompileResult;
import com.android.tools.r8.libanalyzer.proto.ItemCollectionSummary;
import com.android.tools.r8.libanalyzer.proto.KeepRuleBlastRadiusSummary;
import com.android.tools.r8.libanalyzer.proto.LibraryAnalysisResult;
import com.android.tools.r8.libanalyzer.proto.R8CompileResult;
import com.android.tools.r8.libanalyzer.utils.DexIndexedSizeConsumer;
import com.android.tools.r8.libanalyzer.utils.LibraryAnalyzerOptions;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfo;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;

// TODO(b/479726064): Add support for writing LibraryAnalyzer tests.
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
      System.out.println("LibraryAnalyzer " + Version.getVersionString());
      return;
    }
    ExceptionUtils.withR8CompilationHandler(
        options.reporter,
        () -> new LibraryAnalyzer(command.getApp(), options).run(executorService));
  }

  private void run(ExecutorService executorService) {
    InternalD8CompileResult d8CompileResult = runD8(executorService);
    R8CompileResult r8CompileResult = runR8(executorService);
    writeAnalysisResult(d8CompileResult, r8CompileResult);
  }

  private InternalD8CompileResult runD8(ExecutorService executorService) {
    DexIndexedSizeConsumer sizeConsumer = new DexIndexedSizeConsumer();
    D8Command.Builder commandBuilder =
        D8Command.builder(options.reporter)
            .setClassConflictResolver((reference, origins, handler) -> origins.iterator().next())
            .setProgramConsumer(sizeConsumer);
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

  private R8CompileResult runR8(ExecutorService executorService) {
    DexIndexedSizeConsumer sizeConsumer = new DexIndexedSizeConsumer();
    R8Command.Builder commandBuilder =
        R8Command.builder(options.reporter)
            .addProguardConfiguration(List.of("-ignorewarnings"), Origin.unknown())
            .setClassConflictResolver((reference, origins, handler) -> origins.iterator().next())
            .setProgramConsumer(sizeConsumer);
    configure(commandBuilder);
    R8CompileResult.Builder resultBuilder = R8CompileResult.newBuilder();
    try {
      R8.LibraryAnalyzerEntryPoint.run(
          commandBuilder.build(),
          executorService,
          r8Options -> {
            if (options.blastRadiusOutputPath != null) {
              r8Options.getBlastRadiusOptions().outputPath =
                  options.blastRadiusOutputPath.toString();
            }
            r8Options.getBlastRadiusOptions().blastRadiusConsumer =
                (appView, appInfo, blastRadius) ->
                    resultBuilder
                        .setConfiguration(
                            ConfigurationSummary.newBuilder()
                                .addAllKeepRules(getTopBlastRadiusKeepRules(blastRadius))
                                .addAllUsedPackageWideKeepRules(
                                    getPackageWideKeepRules(blastRadius, r -> !r.isEmpty()))
                                .addAllUnusedPackageWideKeepRules(
                                    getPackageWideKeepRules(blastRadius, r -> r.isEmpty())))
                        .setClasses(
                            getItemCollectionSummary(
                                appInfo,
                                IterableUtils::singleton,
                                appInfo.getKeepInfo()::getClassInfo))
                        .setFields(
                            getItemCollectionSummary(
                                appInfo,
                                DexProgramClass::programFields,
                                appInfo.getKeepInfo()::getFieldInfo))
                        .setMethods(
                            getItemCollectionSummary(
                                appInfo,
                                DexProgramClass::programMethods,
                                appInfo.getKeepInfo()::getMethodInfo));
          });
    } catch (CompilationFailedException e) {
      options.reporter.warning(new ExceptionDiagnostic(e));
      options.reporter.clearAbort();
      return null;
    }
    return resultBuilder.setDexSizeBytes(sizeConsumer.size()).build();
  }

  private static <T> ItemCollectionSummary getItemCollectionSummary(
      AppInfoWithLiveness appInfo, Function<DexProgramClass, Iterable<T>> getItems,
      Function<T, KeepInfo<?, ?>> getKeepInfo) {
    int itemCount = 0;
    int keptItemCount = 0;
    int noObfuscationCount = 0;
    int noOptimizationCount = 0;
    int noShrinkingCount = 0;
    InternalOptions options = appInfo.options();
    for (DexProgramClass clazz : appInfo.classes()) {
      for (T item : getItems.apply(clazz)) {
        KeepInfo<?, ?> keepInfo = getKeepInfo.apply(item);
        itemCount++;
        boolean isKept = false;
        if (!keepInfo.isMinificationAllowed(options)) {
          noObfuscationCount++;
          isKept = true;
        }
        if (!keepInfo.isOptimizationAllowed(options)) {
          noOptimizationCount++;
          isKept = true;
        }
        if (!keepInfo.isShrinkingAllowed(options)) {
          noShrinkingCount++;
          isKept = true;
        }
        if (isKept) {
          keptItemCount++;
        }
      }
    }
    return ItemCollectionSummary.newBuilder()
        .setItemCount(itemCount)
        .setKeptItemCount(keptItemCount)
        .setNoObfuscationCount(noObfuscationCount)
        .setNoOptimizationCount(noOptimizationCount)
        .setNoShrinkingCount(noShrinkingCount)
        .build();
  }

  private static List<KeepRuleBlastRadiusSummary> getTopBlastRadiusKeepRules(
      RootSetBlastRadius blastRadius) {
    ArrayList<RootSetBlastRadiusForRule> keepRulesSorted =
        ListUtils.sort(
            blastRadius.getBlastRadius(),
            (x, y) -> {
              if (x.getNumberOfItems() != y.getNumberOfItems()) {
                return y.getNumberOfItems() - x.getNumberOfItems();
              }
              // TODO(b/441055269): Sorting by source is not guaranteed to be
              //  deterministic.
              return x.getSource().compareTo(y.getSource());
            });
    while (keepRulesSorted.size() >= 5) {
      ListUtils.removeLast(keepRulesSorted);
    }
    while (!keepRulesSorted.isEmpty() && ListUtils.last(keepRulesSorted).isEmpty()) {
      ListUtils.removeLast(keepRulesSorted);
    }
    return ListUtils.map(
        keepRulesSorted,
        rule ->
            KeepRuleBlastRadiusSummary.newBuilder()
                .setSource(rule.getSource())
                .setKeptItemCount(rule.getNumberOfItems())
                .build());
  }

  private static List<KeepRuleBlastRadiusSummary> getPackageWideKeepRules(
      RootSetBlastRadius blastRadius, Predicate<RootSetBlastRadiusForRule> predicate) {
    List<RootSetBlastRadiusForRule> unusedPackageWideKeepRules =
        ListUtils.filter(
            blastRadius.getBlastRadius(),
            rule ->
                BlastRadiusKeepRuleClassifier.isPackageWideKeepRule(rule.getRule())
                    && predicate.test(rule));
    List<RootSetBlastRadiusForRule> unusedPackageWideKeepRulesSorted =
        ListUtils.sort(
            unusedPackageWideKeepRules, Comparator.comparing(RootSetBlastRadiusForRule::getSource));
    return ListUtils.map(
        unusedPackageWideKeepRulesSorted,
        rule ->
            KeepRuleBlastRadiusSummary.newBuilder()
                .setSource(rule.getSource())
                .setKeptItemCount(rule.getNumberOfItems())
                .setNoObfuscation(rule.isNoObfuscationSet())
                .setNoOptimization(rule.isNoOptimizationSet())
                .setNoShrinking(rule.isNoShrinkingSet())
                .build());
  }

  private void writeAnalysisResult(
      InternalD8CompileResult d8CompileResult, R8CompileResult r8CompileResult) {
    LibraryAnalysisResult.Builder resultBuilder = LibraryAnalysisResult.newBuilder();
    if (d8CompileResult != null) {
      resultBuilder.setD8CompileResult(
          D8CompileResult.newBuilder().setDexSizeBytes(d8CompileResult.size).build());
    }
    if (r8CompileResult != null) {
      resultBuilder.setR8CompileResult(r8CompileResult);
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
}
