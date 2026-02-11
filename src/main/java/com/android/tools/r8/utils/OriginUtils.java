// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.origin.MavenOrigin;
import com.android.tools.r8.origin.Origin;

public class OriginUtils {

  public static MavenOrigin getMavenOrigin(Origin origin) {
    if (origin instanceof MavenOrigin) {
      return (MavenOrigin) origin;
    }
    if (origin.parent() != null) {
      return getMavenOrigin(origin.parent());
    }
    return null;
  }
}
