// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Interface for receiving byte[] resource. */
@KeepForApi
public interface ByteArrayConsumer<OS extends OutputStream> {

  OS getOutputStream() throws IOException;

  /**
   * Callback when no further content will be provided for the resource.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then the compiler guaranties to exit with an error.
   *
   * @param handler Diagnostics handler for reporting.
   */
  default void finished(OS closedOutputStream, DiagnosticsHandler handler) {}

  @KeepForApi
  interface ArrayConsumer extends ByteArrayConsumer<ByteArrayOutputStream> {

    void accept(byte[] bytes);

    @Override
    default ByteArrayOutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    @Override
    default void finished(ByteArrayOutputStream closedOutputStream, DiagnosticsHandler handler) {
      accept(closedOutputStream.toByteArray());
    }
  }

  /** File consumer to write contents to a file-system file. */
  @KeepForApi
  class FileConsumer implements ByteArrayConsumer<OutputStream> {

    private final Path outputPath;

    public FileConsumer(Path outputPath) {
      this.outputPath = outputPath;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
      return Files.newOutputStream(outputPath);
    }
  }
}
