// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;

// TODO(b/479726064): This is using R8 internal APIs, such as Reporter. As a result, this won't work
//  with r8lib.jar on the classpath. Should LibraryAnalyzer relocate internal APIs into, for
//  example, com.android.tools.r8.libanalyzer.r8?
@KeepForApi
public final class LibraryAnalyzerCommand {

  private final Path aarPath;
  private final Reporter reporter;
  private final boolean printHelp;
  private final boolean printVersion;

  private LibraryAnalyzerCommand(Path aarPath, Reporter reporter) {
    this.aarPath = aarPath;
    this.reporter = reporter;
    this.printHelp = false;
    this.printVersion = false;
  }

  private LibraryAnalyzerCommand(boolean printHelp, boolean printVersion) {
    this.aarPath = null;
    this.reporter = new Reporter();
    this.printHelp = printHelp;
    this.printVersion = printVersion;
  }

  public Path getAarPath() {
    return aarPath;
  }

  public boolean isPrintHelp() {
    return printHelp;
  }

  public boolean isPrintVersion() {
    return printVersion;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler handler) {
    return new Builder(handler);
  }

  public static class Builder {

    private Path aarPath;
    private final Reporter reporter;
    private boolean printHelp = false;
    private boolean printVersion = false;

    private Builder() {
      this(new Reporter());
    }

    private Builder(DiagnosticsHandler handler) {
      this.reporter = handler instanceof Reporter ? (Reporter) handler : new Reporter(handler);
    }

    public Builder setAarPath(Path aarPath) {
      this.aarPath = aarPath;
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

    public LibraryAnalyzerCommand build() {
      if (printHelp || printVersion) {
        return new LibraryAnalyzerCommand(printHelp, printVersion);
      }
      validate();
      return new LibraryAnalyzerCommand(aarPath, reporter);
    }

    private void validate() {
      if (aarPath == null) {
        reporter.error("LibraryAnalyzer requires an input Android Archive (AAR).");
      }
    }
  }
}
