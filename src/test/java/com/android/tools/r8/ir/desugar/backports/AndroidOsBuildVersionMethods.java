// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.ir.desugar.backports.BackportMethodsStub.AndroidOsBuildVersionStub;

public final class AndroidOsBuildVersionMethods {

  // Android runtime value of field android.os.Build$VERSION.SDK_INT_FULL for all Android
  // versions. Calculated from android.os.Build$VERSION.SDK_INT before Baklava (API level 36).
  // See android.os.Build$VERSION_CODES_FULL for the constants for versions before Baklava.
  public static int getSdkIntFull() {
    if (AndroidOsBuildVersionStub.SDK_INT < 36) {
      // Based on the constants in android.os.Build$VERSION_CODES_FULL.
      return AndroidOsBuildVersionStub.SDK_INT * 100_000;
    }
    return AndroidOsBuildVersionStub.SDK_INT_FULL;
  }
}
