// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

public final class ArraysMethods {

  public static void checkValidRange(int arrayLength, int fromIndex, int toIndex) {
    if (fromIndex > toIndex) {
      throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
    }
    if (fromIndex < 0) {
      throw new ArrayIndexOutOfBoundsException(fromIndex);
    }
    if (toIndex > arrayLength) {
      throw new ArrayIndexOutOfBoundsException(toIndex);
    }
  }

  public static boolean equalsInt(int[] a, int[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsIntRange(int[] a, int aFrom, int aTo, int[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsLong(long[] a, long[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsLongRange(
      long[] a, int aFrom, int aTo, long[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsFloat(float[] a, float[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (Float.floatToIntBits(a[i]) != Float.floatToIntBits(b[i])) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsFloatRange(
      float[] a, int aFrom, int aTo, float[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (Float.floatToIntBits(a[aFrom++]) != Float.floatToIntBits(b[bFrom++])) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsDouble(double[] a, double[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (Double.doubleToLongBits(a[i]) != Double.doubleToLongBits(b[i])) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsDoubleRange(
      double[] a, int aFrom, int aTo, double[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (Double.doubleToLongBits(a[aFrom++]) != Double.doubleToLongBits(b[bFrom++])) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsShort(short[] a, short[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsShortRange(
      short[] a, int aFrom, int aTo, short[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsByte(byte[] a, byte[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsByteRange(
      byte[] a, int aFrom, int aTo, byte[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsChar(char[] a, char[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsCharRange(
      char[] a, int aFrom, int aTo, char[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsBoolean(boolean[] a, boolean[] b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a.length != b.length) {
      return false;
    }
    for (int i = 0; i < a.length; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean equalsBooleanRange(
      boolean[] a, int aFrom, int aTo, boolean[] b, int bFrom, int bTo) {
    checkValidRange(a.length, aFrom, aTo);
    checkValidRange(b.length, bFrom, bTo);

    int aLength = aTo - aFrom;
    int bLength = bTo - bFrom;
    if (aLength != bLength) {
      return false;
    }
    for (int i = 0; i < aLength; i++) {
      if (a[aFrom++] != b[bFrom++]) {
        return false;
      }
    }
    return true;
  }
}
