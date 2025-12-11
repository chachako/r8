// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

public class LongUtils {

  /** Encode the bits of a float value losslessly as the 32 least significant bits. */
  public static long encodeFloat(float value) {
    // Important not to use floatToIntBits since that confuses NaN representations.
    return Float.floatToRawIntBits(value);
  }

  /** Decode the 32 least significant bits of a float value losslessly. */
  public static float decodeFloat(long value) {
    return Float.intBitsToFloat((int) value);
  }

  /** Encode the bits of a double value losslessly. */
  public static long encodeDouble(double value) {
    // Important not to use doubleToLongBits since that confuses NaN representations.
    return Double.doubleToRawLongBits(value);
  }

  /** Decode the bits of a double value losslessly. */
  public static double decodeDouble(long value) {
    return Double.longBitsToDouble(value);
  }
}
