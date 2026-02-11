// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.origin;

import java.nio.file.Path;

public class PathBasedMavenOrigin extends PathOrigin implements MavenOrigin {

  private final String group;
  private final String module;
  private final String version;

  public PathBasedMavenOrigin(Path path, String group, String module, String version) {
    super(path);
    this.group = group;
    this.module = module;
    this.version = version;
  }

  @Override
  public String getDisplayName() {
    return group + ":" + module + ":" + version;
  }

  @Override
  public String getGroup() {
    return group;
  }

  @Override
  public String getModule() {
    return module;
  }

  @Override
  public String getVersion() {
    return version;
  }
}
