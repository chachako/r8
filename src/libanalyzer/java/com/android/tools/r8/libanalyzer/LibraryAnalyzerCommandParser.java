// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static com.android.tools.r8.BaseCompilerCommandParser.LIB_FLAG;
import static com.android.tools.r8.BaseCompilerCommandParser.MIN_API_FLAG;
import static com.android.tools.r8.BaseCompilerCommandParser.OUTPUT_FLAG;
import static com.android.tools.r8.BaseCompilerCommandParser.THREAD_COUNT_FLAG;
import static com.android.tools.r8.BaseCompilerCommandParser.parsePositiveIntArgument;

import com.android.tools.r8.CompilerCommandParserUtils;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Paths;
import java.util.Set;

@KeepForApi
public class LibraryAnalyzerCommandParser {

  private static final String AAR_FLAG = "--aar";

  private static final Set<String> OPTIONS_WITH_ONE_PARAMETER =
      ImmutableSet.of(AAR_FLAG, LIB_FLAG, MIN_API_FLAG, OUTPUT_FLAG, THREAD_COUNT_FLAG);

  private static final String USAGE_MESSAGE =
      StringUtils.lines(
          "Usage: libanalyzer [options]",
          "where options are:",
          "  --aar <path>            # Path to Android Archive (AAR) that should be analyzed.",
          "  --lib <path>            # Path to file or JDK home to use as a library resource.",
          "  --min-api <major.minor> # Minimum API level to use for analysis.",
          "  --output <path>         # Path where to write the analysis result (protobuf).",
          "  --thread-count <int>    # Number of threads to use.",
          "  --help                  # Print this message.",
          "  --version               # Print the version.");

  public static String getUsageMessage() {
    return USAGE_MESSAGE;
  }

  public static LibraryAnalyzerCommand.Builder parse(String[] args, Origin origin) {
    Reporter reporter = new Reporter();
    LibraryAnalyzerCommand.Builder builder = LibraryAnalyzerCommand.builder();
    String[] expandedArgs = FlagFile.expandFlagFiles(args, reporter::error);
    for (int i = 0; i < expandedArgs.length; i++) {
      String arg = expandedArgs[i].trim();
      String nextArg = null;
      if (OPTIONS_WITH_ONE_PARAMETER.contains(arg)) {
        if (++i < expandedArgs.length) {
          nextArg = expandedArgs[i];
        } else {
          reporter.error(
              new StringDiagnostic("Missing parameter for " + expandedArgs[i - 1] + ".", origin));
          break;
        }
      }
      if (arg.isEmpty()) {
        continue;
      } else if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals(AAR_FLAG)) {
        builder.setAarPath(Paths.get(nextArg));
      } else if (arg.equals(LIB_FLAG)) {
        CompilerCommandParserUtils.addLibraryArgument(
            builder.getAppBuilder(), nextArg, origin, reporter);
      } else if (arg.equals(MIN_API_FLAG)) {
        builder.setMinApiLevel(AndroidApiLevel.parseAndroidApiLevel(nextArg));
      } else if (arg.equals(OUTPUT_FLAG)) {
        builder.setOutputPath(Paths.get(nextArg));
      } else if (arg.equals(THREAD_COUNT_FLAG)) {
        parsePositiveIntArgument(
            reporter::error, THREAD_COUNT_FLAG, nextArg, origin, builder::setThreadCount);
      } else {
        reporter.error(new StringDiagnostic("Unknown option: " + arg, origin));
      }
    }
    return builder;
  }
}
