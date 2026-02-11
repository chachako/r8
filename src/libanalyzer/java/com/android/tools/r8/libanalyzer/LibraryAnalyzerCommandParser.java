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
import com.android.tools.r8.origin.PathBasedMavenOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@KeepForApi
public class LibraryAnalyzerCommandParser {

  private static final String AAR_FLAG = "--aar";
  private static final String BLAST_RADIUS_OUTPUT_FLAG = "--blast-radius-output";
  private static final String JAR_FLAG = "--jar";
  private static final String REPO_FLAG = "--repo";

  private static final Set<String> OPTIONS_WITH_ONE_PARAMETER =
      ImmutableSet.of(
          AAR_FLAG,
          BLAST_RADIUS_OUTPUT_FLAG,
          JAR_FLAG,
          LIB_FLAG,
          MIN_API_FLAG,
          OUTPUT_FLAG,
          REPO_FLAG,
          THREAD_COUNT_FLAG);

  private static final String USAGE_MESSAGE =
      StringUtils.lines(
          "Usage: libanalyzer [options]",
          "where options are:",
          "  --aar <path>                 # Path to Android Archive (AAR) that should be analyzed.",
          "  --blast-radius-output <path> # Path where to write blast radius result (protobuf).",
          "  --jar <path>                 # Path to Java Archive (JAR) that should be analyzed.",
          "  --lib <path>                 # Path to file or JDK home to use as a library resource.",
          "  --maven-coord <x:y:z>        # Set the Maven coordinate of the previous --aar/--jar.",
          "                               # Only allowed after --aar <path> or --jar <path>.",
          "  --min-api <major.minor>      # Minimum API level to use for analysis.",
          "  --output <path>              # Path where to write analysis result (protobuf).",
          "  --repo <path>                # Path to local Maven repository.",
          "  --thread-count <int>         # Number of threads to use.",
          "  --help                       # Print this message.",
          "  --version                    # Print the version.");

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
        Path aarPath = Paths.get(nextArg);
        if (!FileUtils.isAarFile(aarPath)) {
          throw new IllegalArgumentException("Expected AAR, got: " + nextArg);
        }
        if ("--maven-coord".equals(peekArg(expandedArgs, i + 1))) {
          if (peekArg(expandedArgs, i + 2) == null) {
            throw new IllegalArgumentException("Missing parameter for --maven-coord");
          }
          List<String> mavenCoordinate = StringUtils.split(expandedArgs[i + 2], ':');
          builder.addAarPath(
              aarPath,
              new PathBasedMavenOrigin(
                  aarPath, mavenCoordinate.get(0), mavenCoordinate.get(1), mavenCoordinate.get(2)));
          i += 2;
        } else {
          builder.addAarPath(aarPath);
        }
      } else if (arg.equals(BLAST_RADIUS_OUTPUT_FLAG)) {
        builder.setBlastRadiusOutputPath(Paths.get(nextArg));
      } else if (arg.equals(JAR_FLAG)) {
        Path jarPath = Paths.get(nextArg);
        if (!FileUtils.isJarFile(jarPath)) {
          throw new IllegalArgumentException("Expected JAR, got: " + nextArg);
        }
        if ("--maven-coord".equals(peekArg(expandedArgs, i + 1))) {
          if (peekArg(expandedArgs, i + 2) == null) {
            throw new IllegalArgumentException("Missing parameter for --maven-coord");
          }
          List<String> mavenCoordinate = StringUtils.split(expandedArgs[i + 2], ':');
          builder.addJarPath(
              jarPath,
              new PathBasedMavenOrigin(
                  jarPath, mavenCoordinate.get(0), mavenCoordinate.get(1), mavenCoordinate.get(2)));
          i += 2;
        } else {
          builder.addJarPath(jarPath);
        }
      } else if (arg.equals(LIB_FLAG)) {
        CompilerCommandParserUtils.addLibraryArgument(
            builder.getAppBuilder(), nextArg, origin, reporter);
      } else if (arg.equals(MIN_API_FLAG)) {
        builder.setMinApiLevel(AndroidApiLevel.parseAndroidApiLevel(nextArg));
      } else if (arg.equals(OUTPUT_FLAG)) {
        builder.setOutputPath(Paths.get(nextArg));
      } else if (arg.equals(REPO_FLAG)) {
        Path repoPath = Paths.get(nextArg);
        if (!Files.isDirectory(repoPath)) {
          throw new IllegalArgumentException(
              "Invalid parameter for --repo. Expected directory, got: " + nextArg);
        }
        try (Stream<Path> paths = Files.walk(repoPath)) {
          paths
              .filter(path -> FileUtils.isAarFile(path) || FileUtils.isJarFile(path))
              .forEach(
                  path -> {
                    if (FileUtils.isAarFile(path)) {
                      builder.addAarPath(path, getMavenOriginFromArchive(repoPath, path));
                    } else {
                      builder.addJarPath(path, getMavenOriginFromArchive(repoPath, path));
                    }
                  });
        } catch (IOException e) {
          throw new UncheckedIOException("Failed to walk repository path: " + nextArg, e);
        }
      } else if (arg.equals(THREAD_COUNT_FLAG)) {
        parsePositiveIntArgument(
            reporter::error, THREAD_COUNT_FLAG, nextArg, origin, builder::setThreadCount);
      } else {
        reporter.error(new StringDiagnostic("Unknown option: " + arg, origin));
      }
    }
    return builder;
  }

  private static Origin getMavenOriginFromArchive(Path repoPath, Path path) {
    String directoryName = repoPath.relativize(path).getParent().toString();
    int lastSeparator = directoryName.lastIndexOf(File.separatorChar);
    int prevSeparator = directoryName.lastIndexOf(File.separatorChar, lastSeparator - 1);
    String group =
        StringUtils.replaceAll(directoryName.substring(0, prevSeparator), File.separator, ".");
    String module = directoryName.substring(prevSeparator + 1, lastSeparator);
    String version = directoryName.substring(lastSeparator + 1);
    return new PathBasedMavenOrigin(path, group, module, version);
  }

  private static String peekArg(String[] expandedArgs, int index) {
    return ArrayUtils.getOrDefault(expandedArgs, index, null);
  }
}
