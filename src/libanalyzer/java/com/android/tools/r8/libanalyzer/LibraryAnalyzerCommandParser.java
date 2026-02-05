// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Paths;

@KeepForApi
public class LibraryAnalyzerCommandParser {

  private static final String USAGE_MESSAGE =
      StringUtils.lines(
          "Usage: libanalyzer [options] --aar <path>",
          "where options are:",
          "  --help                 # Print this message.",
          "  --version              # Print the version.");

  public static String getUsageMessage() {
    return USAGE_MESSAGE;
  }

  public static void main(String[] args) {
    ExceptionUtils.withMainProgramHandler(
        () -> {
          LibraryAnalyzerCommand.Builder builder = parse(args, CommandLineOrigin.INSTANCE);
          LibraryAnalyzer.run(builder.build());
        });
  }

  public static LibraryAnalyzerCommand.Builder parse(String[] args, Origin origin) {
    Reporter reporter = new Reporter();
    LibraryAnalyzerCommand.Builder builder = LibraryAnalyzerCommand.builder();
    String[] expandedArgs = FlagFile.expandFlagFiles(args, reporter::error);
    for (int i = 0; i < expandedArgs.length; i++) {
      String arg = expandedArgs[i].trim();
      if (arg.isEmpty()) {
        continue;
      } else if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals("--aar")) {
        if (++i < expandedArgs.length) {
          builder.setAarPath(Paths.get(expandedArgs[i]));
        } else {
          reporter.error(new StringDiagnostic("Missing parameter for --aar", origin));
        }
      } else {
        reporter.error(new StringDiagnostic("Unknown option: " + arg, origin));
      }
    }
    return builder;
  }
}
