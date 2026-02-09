// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.libanalyzer.utils.LibraryAnalyzerOptions;
import com.android.tools.r8.utils.AarArchiveResourceProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import java.nio.file.Path;

// TODO(b/479726064): This is using R8 internal APIs, such as Reporter. As a result, this won't work
//  with r8lib.jar on the classpath. Should LibraryAnalyzer relocate internal APIs into, for
//  example, com.android.tools.r8.libanalyzer.r8?
@KeepForApi
public final class LibraryAnalyzerCommand {

  private final AndroidApp app;
  private final AndroidApiLevel minApiLevel;
  private final Path outputPath;
  private final Reporter reporter;
  private final int threadCount;
  private final boolean printHelp;
  private final boolean printVersion;

  private LibraryAnalyzerCommand(
      AndroidApp app,
      AndroidApiLevel minApiLevel,
      Path outputPath,
      Reporter reporter,
      int threadCount) {
    this.app = app;
    this.minApiLevel = minApiLevel;
    this.outputPath = outputPath;
    this.reporter = reporter;
    this.threadCount = threadCount;
    this.printHelp = false;
    this.printVersion = false;
  }

  private LibraryAnalyzerCommand(boolean printHelp, boolean printVersion) {
    this.app = null;
    this.minApiLevel = null;
    this.outputPath = null;
    this.reporter = new Reporter();
    this.threadCount = ThreadUtils.NOT_SPECIFIED;
    this.printHelp = printHelp;
    this.printVersion = printVersion;
  }

  AndroidApp getApp() {
    return app;
  }

  LibraryAnalyzerOptions getInternalOptions() {
    return new LibraryAnalyzerOptions(minApiLevel, outputPath, reporter, threadCount);
  }

  boolean isPrintHelp() {
    return printHelp;
  }

  boolean isPrintVersion() {
    return printVersion;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler handler) {
    return new Builder(handler);
  }

  public static class Builder {

    private final AndroidApp.Builder appBuilder;
    private AndroidApiLevel minApiLevel = AndroidApiLevel.getDefault();
    private Path outputPath;
    private final Reporter reporter;
    private int threadCount = ThreadUtils.NOT_SPECIFIED;

    private boolean printHelp = false;
    private boolean printVersion = false;

    private Builder() {
      this(new Reporter());
    }

    private Builder(DiagnosticsHandler handler) {
      Reporter reporter = handler instanceof Reporter ? (Reporter) handler : new Reporter(handler);
      this.appBuilder = AndroidApp.builder(reporter);
      this.reporter = reporter;
    }

    AndroidApp.Builder getAppBuilder() {
      return appBuilder;
    }

    public Builder setAarPath(Path aarPath) {
      appBuilder.addProgramResourceProvider(AarArchiveResourceProvider.fromArchive(aarPath));
      return this;
    }

    public Builder setOutputPath(Path outputPath) {
      this.outputPath = outputPath;
      return this;
    }

    public Builder setMinApiLevel(int minMajorApiLevel, int minMinorApiLevel) {
      return setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(minMajorApiLevel, minMinorApiLevel));
    }

    Builder setMinApiLevel(AndroidApiLevel minApiLevel) {
      this.minApiLevel = minApiLevel;
      return this;
    }

    public Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    public Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    public Builder setThreadCount(int threadCount) {
      this.threadCount = threadCount;
      return this;
    }

    public LibraryAnalyzerCommand build() {
      if (printHelp || printVersion) {
        return new LibraryAnalyzerCommand(printHelp, printVersion);
      }
      validate();
      return new LibraryAnalyzerCommand(
          appBuilder.build(), minApiLevel, outputPath, reporter, threadCount);
    }

    private void validate() {
      if (appBuilder.getProgramResourceProviders().isEmpty()) {
        reporter.error("LibraryAnalyzer requires an input Android Archive (AAR).");
      }
      reporter.failIfPendingErrors();
    }
  }
}
