// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.blastradius.proto.BlastRadiusContainer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class BlastRadiusHtmlReportGenerator {

  public static void main(String[] args) throws IOException {
    Path input = Paths.get(args[0]);
    Path output = Paths.get(args[1]);
    BlastRadiusContainer blastRadius;
    try (InputStream is = Files.newInputStream(input)) {
      blastRadius = BlastRadiusContainer.parseFrom(is);
    }
    Files.write(output, generate(blastRadius).getBytes(StandardCharsets.UTF_8));
  }

  public static String generate(BlastRadiusContainer blastRadius) {
    String html = BlastRadiusHtmlReportTemplate.getHtmlTemplate();
    return html.replace(
        "<script id=\"blastradius-data\" type=\"application/octet-stream\"></script>",
        String.join(
            "",
            "<script id=\"blastradius-data\" type=\"application/octet-stream\">",
            encodeToString(blastRadius),
            "</script>"));
  }

  private static String encodeToString(BlastRadiusContainer blastRadius) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      blastRadius.writeTo(baos);
    } catch (IOException e) {
      // Should not happen.
      throw new UncheckedIOException(e);
    }
    return Base64.getEncoder().encodeToString(baos.toByteArray());
  }
}
