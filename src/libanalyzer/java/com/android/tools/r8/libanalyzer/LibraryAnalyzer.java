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
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.Version;
import com.android.tools.r8.blastradius.BlastRadiusKeepRuleClassifier;
import com.android.tools.r8.blastradius.RootSetBlastRadius;
import com.android.tools.r8.blastradius.RootSetBlastRadiusForRule;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.libanalyzer.proto.BlockedConsumerKeepRule;
import com.android.tools.r8.libanalyzer.proto.ConfigurationSummary;
import com.android.tools.r8.libanalyzer.proto.D8CompileResult;
import com.android.tools.r8.libanalyzer.proto.ItemCollectionSummary;
import com.android.tools.r8.libanalyzer.proto.KeepRuleBlastRadiusSummary;
import com.android.tools.r8.libanalyzer.proto.LibraryAnalyzerResult;
import com.android.tools.r8.libanalyzer.proto.R8CompileResult;
import com.android.tools.r8.libanalyzer.proto.ValidateConsumerKeepRulesResult;
import com.android.tools.r8.libanalyzer.utils.DexIndexedSizeConsumer;
import com.android.tools.r8.libanalyzer.utils.LibraryAnalyzerOptions;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.processkeeprules.ValidateLibraryConsumerRulesKeepRuleProcessor;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfo;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationParser.ProguardConfigurationSourceParser;
import com.android.tools.r8.utils.AllEmbeddedRulesExtractor;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;

@KeepForApi
public class LibraryAnalyzer {

  private final AndroidApp app;
  private final LibraryAnalyzerOptions options;
  private final Reporter reporter;

  private LibraryAnalyzer(AndroidApp app, LibraryAnalyzerOptions options) {
    this.app = app;
    this.options = options;
    this.reporter = options.reporter;
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
    ExecutorService executorService =
        ThreadUtils.getExecutorService(options.threadCount, options.getThreadingModule());
    try {
      run(command, executorService, options);
    } finally {
      executorService.shutdown();
    }
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
    ValidateConsumerKeepRulesResult validateConsumerKeepRulesResult =
        runValidateConsumerKeepRules();
    writeAnalysisResult(d8CompileResult, r8CompileResult, validateConsumerKeepRulesResult);
  }

  private InternalD8CompileResult runD8(ExecutorService executorService) {
    DexIndexedSizeConsumer sizeConsumer = new DexIndexedSizeConsumer();
    D8Command.Builder commandBuilder =
        D8Command.builder(reporter)
            .setClassConflictResolver((reference, origins, handler) -> origins.iterator().next())
            .setProgramConsumer(sizeConsumer);
    configure(commandBuilder);
    try {
      D8.LibraryAnalyzerEntryPoint.run(
          commandBuilder.build(),
          executorService,
          d8Options -> d8Options.libraryAnalyzerSubCompilation = true);
    } catch (CompilationFailedException e) {
      reporter.warning(new ExceptionDiagnostic(e));
      reporter.clearAbort();
      return null;
    }
    return new InternalD8CompileResult(sizeConsumer.size());
  }

  private R8CompileResult runR8(ExecutorService executorService) {
    DexIndexedSizeConsumer sizeConsumer = new DexIndexedSizeConsumer();
    R8Command.Builder commandBuilder =
        R8Command.builder(reporter)
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
            r8Options.libraryAnalyzerSubCompilation = true;
            r8Options.ignoreUnusedProguardRules = true;
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
      reporter.warning(new ExceptionDiagnostic(e));
      reporter.clearAbort();
      return null;
    }
    return resultBuilder.setDexSizeBytes(sizeConsumer.size()).build();
  }

  private ValidateConsumerKeepRulesResult runValidateConsumerKeepRules() {
    ValidateConsumerKeepRulesResult.Builder resultBuilder =
        ValidateConsumerKeepRulesResult.newBuilder();
    try {
      // TODO(b/486771488): Consider implementing this using ProcessKeepRules, similar to how runD8
      //  and runR8 works above.
      ExceptionUtils.withCompilationHandler(
          reporter, () -> internalRunValidateConsumerKeepRules(resultBuilder));
    } catch (CompilationFailedException e) {
      reporter.warning(new ExceptionDiagnostic(e));
      reporter.clearAbort();
      return null;
    }
    return resultBuilder.build();
  }

  private void internalRunValidateConsumerKeepRules(
      ValidateConsumerKeepRulesResult.Builder resultBuilder) throws ResourceException {
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(
            new DexItemFactory(),
            reporter,
            new ValidateLibraryConsumerRulesKeepRuleProcessor(reporter) {

              @Override
              protected void handleRule(
                  ProguardConfigurationSourceParser parser, Position position, String rule) {
                resultBuilder.addBlockedKeepRules(
                    BlockedConsumerKeepRule.newBuilder().setSource(rule).build());
              }

              @Override
              protected void handleKeepAttribute(
                  ProguardConfigurationSourceParser parser, Position position, String attribute) {
                resultBuilder.addBlockedKeepRules(
                    BlockedConsumerKeepRule.newBuilder()
                        .setSource("-keepattributes " + attribute)
                        .build());
              }
            });
    for (var programResourceProvider : app.getProgramResourceProviders()) {
      var dataResourceProvider = programResourceProvider.getDataResourceProvider();
      if (dataResourceProvider != null) {
        new AllEmbeddedRulesExtractor(dataResourceProvider, reporter)
            .readSources()
            .parseAllRules(parser);
      }
    }
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
            unusedPackageWideKeepRules,
            (x, y) -> {
              if (x.getNumberOfItems() != y.getNumberOfItems()) {
                return y.getNumberOfItems() - x.getNumberOfItems();
              }
              // TODO(b/441055269): Sorting by source is not guaranteed to be
              //  deterministic.
              return x.getSource().compareTo(y.getSource());
            });
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
      InternalD8CompileResult d8CompileResult,
      R8CompileResult r8CompileResult,
      ValidateConsumerKeepRulesResult validateConsumerKeepRulesResult) {
    LibraryAnalyzerResult.Builder resultBuilder = LibraryAnalyzerResult.newBuilder();
    if (d8CompileResult != null) {
      resultBuilder.setD8CompileResult(
          D8CompileResult.newBuilder().setDexSizeBytes(d8CompileResult.size).build());
    }
    if (r8CompileResult != null) {
      resultBuilder.setR8CompileResult(r8CompileResult);
    }
    if (validateConsumerKeepRulesResult != null) {
      resultBuilder.setValidateConsumerKeepRulesResult(validateConsumerKeepRulesResult);
    }
    LibraryAnalyzerResult result = resultBuilder.build();
    if (options.outputConsumer != null) {
      options.outputConsumer.accept(result);
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
