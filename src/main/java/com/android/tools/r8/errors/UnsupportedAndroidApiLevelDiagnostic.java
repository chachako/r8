// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.AndroidApiLevel;

@KeepForApi
public class UnsupportedAndroidApiLevelDiagnostic implements Diagnostic {

  private final int apiLevelMajor;
  private final int apiLevelMinor;

  public UnsupportedAndroidApiLevelDiagnostic(int apiLevelMajor, int apiLevelMinor) {
    assert apiLevelMajor > 0;
    assert apiLevelMinor >= 0;
    this.apiLevelMajor = apiLevelMajor;
    this.apiLevelMinor = apiLevelMinor;
  }

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return "An API level of "
        + apiLevelMajor
        + ((apiLevelMinor != 0) ? "." + apiLevelMinor : "")
        + " is not supported by this compiler. Please use an API level of "
        + AndroidApiLevel.LATEST.getLevel()
        + " or earlier)";
  }
}
