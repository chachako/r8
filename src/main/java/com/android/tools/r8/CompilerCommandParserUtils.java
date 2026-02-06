// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CompilerCommandParserUtils {

  public static void addLibraryArgument(
      AndroidApp.Builder appBuilder, String arg, Origin origin, Reporter reporter) {
    Path path = Paths.get(arg);
    if (isJdkHome(path)) {
      try {
        appBuilder.addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(path));
      } catch (IOException e) {
        reporter.error(new ExceptionDiagnostic(e, origin));
      }
    } else {
      appBuilder.addLibraryFiles(path);
    }
  }

  /**
   * This method must match the lookup in {@link
   * com.android.tools.r8.JdkClassFileProvider#fromJdkHome}.
   */
  private static boolean isJdkHome(Path home) {
    Path jrtFsJar = home.resolve("lib").resolve("jrt-fs.jar");
    if (Files.exists(jrtFsJar)) {
      return true;
    }
    // JDK has rt.jar in jre/lib/rt.jar.
    Path rtJar = home.resolve("jre").resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    // JRE has rt.jar in lib/rt.jar.
    rtJar = home.resolve("lib").resolve("rt.jar");
    return Files.exists(rtJar);
  }
}
