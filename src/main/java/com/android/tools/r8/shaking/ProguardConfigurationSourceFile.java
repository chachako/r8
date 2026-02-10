// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ProguardConfigurationSourceFile implements ProguardConfigurationSource {

  private final Path path;
  private final Origin origin;

  public ProguardConfigurationSourceFile(Path path) {
    this(path, new PathOrigin(path));
  }

  public ProguardConfigurationSourceFile(Path path, Origin origin) {
    this.path = path;
    this.origin = origin;
  }

  @Override
  public String get() throws IOException{
    return Files.readString(path);
  }

  @Override
  public Path getBaseDirectory() {
    Path baseDirectory = path.getParent();
    if (baseDirectory == null) {
      // Path parent can be null only if it's root dir or if its a one element path relative to
      // current directory.
      baseDirectory = Paths.get("");
    }
    return baseDirectory;
  }

  @Override
  public String getName() {
    return path.toString();
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }
}
