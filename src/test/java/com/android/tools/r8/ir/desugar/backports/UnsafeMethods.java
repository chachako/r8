// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.ir.desugar.backports.BackportMethodsStub.UnsafeStub;

public final class UnsafeMethods {
  // Workaround Android S issue with compareAndSwapObject (b/211646483).
  public static boolean compareAndSwapObject(
      UnsafeStub unsafe, Object receiver, long offset, Object expect, Object update) {
    do {
      if (unsafe.compareAndSwapObject(receiver, offset, expect, update)) {
        return true;
      }
    } while (unsafe.getObject(receiver, offset) == expect);
    return false;
  }
}
