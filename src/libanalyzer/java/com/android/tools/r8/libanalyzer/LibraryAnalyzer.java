// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static com.google.common.base.Predicates.alwaysTrue;

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
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.libanalyzer.proto.D8CompileResult;
import com.android.tools.r8.libanalyzer.proto.KeepRuleBlastRadius;
import com.android.tools.r8.libanalyzer.proto.LibraryAnalysisResult;
import com.android.tools.r8.libanalyzer.proto.R8CompileResult;
import com.android.tools.r8.libanalyzer.utils.DexIndexedSizeConsumer;
import com.android.tools.r8.libanalyzer.utils.LibraryAnalyzerOptions;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfo;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

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
            r8Options.blastRadiusConsumer =
                (appView, appInfo, blastRadius) -> {
                  resultBuilder.setNumItems(getNumberOfItems(appView));
                  resultBuilder.setNumItemsKept(getNumberOfKeptItems(appInfo));
                  resultBuilder.setNumKeepRules(getNumberOfKeepRules(appView));
                  resultBuilder.setNumKeepRulesPackageWide(
                      getNumberOfPackageWideKeepRules(appView));
                  resultBuilder.addAllKeepRuleBlastRadius(getTopBlastRadiusKeepRules(blastRadius));
                };
          });
    } catch (CompilationFailedException e) {
      options.reporter.warning(new ExceptionDiagnostic(e));
      options.reporter.clearAbort();
      return null;
    }
    return resultBuilder.setSizeBytes(sizeConsumer.size()).build();
  }

  private static int getNumberOfItems(AppView<?> appView) {
    int items = appView.appInfo().classes().size();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      items += clazz.getFieldCollection().size();
      items += clazz.getMethodCollection().size();
    }
    return items;
  }

  private static int getNumberOfKeptItems(AppInfoWithLiveness appInfo) {
    int items = 0;
    KeepInfoCollection keepInfoCollection = appInfo.getKeepInfo();
    InternalOptions options = appInfo.options();
    for (DexProgramClass clazz : appInfo.classes()) {
      if (appInfo.isLiveProgramClass(clazz)) {
        if (isKept(keepInfoCollection.getInfo(clazz), options)) {
          items++;
        }
        for (ProgramField field : clazz.programFields()) {
          if (appInfo.isReachableOrReferencedField(field.getDefinition())) {
            if (isKept(keepInfoCollection.getInfo(field), options)) {
              items++;
            }
          }
        }
        for (ProgramMethod method : clazz.programMethods()) {
          if (appInfo.isLiveOrTargetedMethod(method.getDefinition())) {
            if (isKept(keepInfoCollection.getInfo(method), options)) {
              items++;
            }
          }
        }
      }
    }
    return items;
  }

  private static boolean isKept(KeepInfo<?, ?> keepInfo, InternalOptions options) {
    return !keepInfo.isMinificationAllowed(options)
        || !keepInfo.isOptimizationAllowed(options)
        || !keepInfo.isShrinkingAllowed(options);
  }

  private static int getNumberOfKeepRules(AppView<?> appView) {
    return getNumberOfKeepRules(appView, alwaysTrue());
  }

  private static int getNumberOfKeepRules(
      AppView<?> appView, Predicate<ProguardKeepRuleBase> predicate) {
    ProguardConfiguration configuration = appView.options().getProguardConfiguration();
    if (configuration != null) {
      long count =
          configuration.getRules().stream()
              .filter(r -> r.isProguardKeepRule() || r.isProguardIfRule())
              .filter(r -> predicate.test((ProguardKeepRuleBase) r))
              .count();
      return (int) count;
    }
    return 0;
  }

  private static int getNumberOfPackageWideKeepRules(AppView<?> appView) {
    return getNumberOfKeepRules(appView, BlastRadiusKeepRuleClassifier::isPackageWideKeepRule);
  }

  private static List<KeepRuleBlastRadius> getTopBlastRadiusKeepRules(
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
    while (!keepRulesSorted.isEmpty() && ListUtils.last(keepRulesSorted).getNumberOfItems() == 0) {
      ListUtils.removeLast(keepRulesSorted);
    }
    return ListUtils.map(
        keepRulesSorted,
        rule ->
            KeepRuleBlastRadius.newBuilder()
                .setSource(rule.getSource())
                .setNumItemsKept(rule.getNumberOfItems())
                .build());
  }

  private void writeAnalysisResult(
      InternalD8CompileResult d8CompileResult, R8CompileResult r8CompileResult) {
    LibraryAnalysisResult.Builder resultBuilder = LibraryAnalysisResult.newBuilder();
    if (d8CompileResult != null) {
      resultBuilder.setD8CompileResult(
          D8CompileResult.newBuilder().setSizeBytes(d8CompileResult.size).build());
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
