// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Objects;

/**
 * Closed interval of two longs.
 */
public class LongInterval {

  private final long min;
  private final long max;

  public LongInterval(int value) {
    this(value, value);
  }

  public LongInterval(int min, int max) {
    assert min <= max;
    this.min = min;
    this.max = max;
  }

  public LongInterval(long value) {
    this(value, value);
  }

  public LongInterval(long min, long max) {
    assert min <= max;
    this.min = min;
    this.max = max;
  }

  public long getMin() {
    return min;
  }

  public long getMax() {
    return max;
  }

  public boolean isSingleValue() {
    return min == max;
  }

  public boolean isSingleValue(int value) {
    return isSingleValue() && getSingleValue() == value;
  }

  public long getSingleValue() {
    assert isSingleValue();
    return min;
  }

  public boolean containsValue(long value) {
    return min <= value && value <= max;
  }

  public boolean doesntOverlapWith(LongInterval other) {
    return other.max < min || max < other.min;
  }

  public boolean overlapsWith(LongInterval other) {
    return other.max >= min && max >= other.min;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof LongInterval) {
      LongInterval other = (LongInterval) obj;
      return min == other.min && max == other.max;
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(min, max);
  }

  @Override
  public String toString() {
    return "[" + min + ", " + max + "]";
  }
}
