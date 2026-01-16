// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public class BackportMethodsStub {
  // Stub out android.os.Build$VERSION as it does not exist when building R8.
  static class AndroidOsBuildStub {
    public static int getMajorSdkVersion(int sdkIntFull) {
      return -1;
    }

    public static int getMinorSdkVersion(int sdkIntFull) {
      return -1;
    }
  }

  // Stub out android.os.Build$VERSION as it does not exist when building R8.
  static class AndroidOsBuildVersionStub {
    public static int SDK_INT;
    public static int SDK_INT_FULL;
  }

  // Stub out sun.misc.Unsafe to avoid compiler issues with referring to sun.misc.Unsafe.
  static class UnsafeStub {
    public boolean compareAndSwapObject(
        Object receiver, long offset, Object expect, Object update) {
      throw new RuntimeException("Stub called.");
    }

    public Object getObject(Object receiver, long offset) {
      throw new RuntimeException("Stub called.");
    }
  }

  static class LongStub {
    public static long parseUnsignedLong(CharSequence s, int beginIndex, int endIndex, int radix)
        throws NumberFormatException {
      return 0L;
    }
  }

  static class MathStub {

    public static long ceilMod(long x, int y) {
      return 0L;
    }

    public static long ceilMod(long x, long y) {
      return 0L;
    }

    public static int ceilDiv(int x, int y) {
      return 0;
    }

    public static long ceilDiv(long x, int y) {
      return 0L;
    }

    public static long ceilDiv(long x, long y) {
      return 0L;
    }

    public static int unsignedMultiplyExact(int x, int y) {
      return 0;
    }

    public static long unsignedMultiplyExact(long x, int y) {
      return 0L;
    }

    public static long unsignedMultiplyExact(long x, long y) {
      return 0L;
    }

    public static long unsignedMultiplyHigh(long x, long y) {
      return 0L;
    }

    public static long multiplyHigh(long x, long y) {
      return 0L;
    }
  }
}
