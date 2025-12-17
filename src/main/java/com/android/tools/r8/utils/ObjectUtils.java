// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

public class ObjectUtils {

  public static <T> boolean getBooleanOrElse(T object, Predicate<T> fn, boolean orElse) {
    if (object != null) {
      return fn.test(object);
    }
    return orElse;
  }

  public static int hashZIL(boolean b1, int i2, Object o3) {
    int result = 1;
    result = 31 * result + Boolean.hashCode(b1);
    result = 31 * result + i2;
    result = 31 * result + Objects.hashCode(o3);
    return result;
  }

  public static int hashZILL(boolean b1, int i2, Object o3, Object o4) {
    int result = 1;
    result = 31 * result + Boolean.hashCode(b1);
    result = 31 * result + i2;
    result = 31 * result + Objects.hashCode(o3);
    result = 31 * result + Objects.hashCode(o4);
    return result;
  }

  public static int hashZZZZZZZZZ(
      boolean b1,
      boolean b2,
      boolean b3,
      boolean b4,
      boolean b5,
      boolean b6,
      boolean b7,
      boolean b8,
      boolean b9) {
    int result = 1;
    result = 31 * result + Boolean.hashCode(b1);
    result = 31 * result + Boolean.hashCode(b2);
    result = 31 * result + Boolean.hashCode(b3);
    result = 31 * result + Boolean.hashCode(b4);
    result = 31 * result + Boolean.hashCode(b5);
    result = 31 * result + Boolean.hashCode(b6);
    result = 31 * result + Boolean.hashCode(b7);
    result = 31 * result + Boolean.hashCode(b8);
    result = 31 * result + Boolean.hashCode(b9);
    return result;
  }

  public static int hashZZZLLL(
      boolean b1, boolean b2, boolean b3, Object o4, Object o5, Object o6) {
    int result = 1;
    result = 31 * result + Boolean.hashCode(b1);
    result = 31 * result + Boolean.hashCode(b2);
    result = 31 * result + Boolean.hashCode(b3);
    result = 31 * result + Objects.hashCode(o4);
    result = 31 * result + Objects.hashCode(o5);
    result = 31 * result + Objects.hashCode(o6);
    return result;
  }

  public static int hashZZLLLL(boolean b1, boolean b2, Object o3, Object o4, Object o5, Object o6) {
    int result = 1;
    result = 31 * result + Boolean.hashCode(b1);
    result = 31 * result + Boolean.hashCode(b2);
    result = 31 * result + Objects.hashCode(o3);
    result = 31 * result + Objects.hashCode(o4);
    result = 31 * result + Objects.hashCode(o5);
    result = 31 * result + Objects.hashCode(o6);
    return result;
  }

  public static int hashZLL(boolean b1, Object o2, Object o3) {
    int result = 1;
    result = 31 * result + Boolean.hashCode(b1);
    result = 31 * result + Objects.hashCode(o2);
    result = 31 * result + Objects.hashCode(o3);
    return result;
  }

  public static int hashZLLLLLL(
      boolean b1, Object o2, Object o3, Object o4, Object o5, Object o6, Object o7) {
    int result = 1;
    result = 31 * result + Boolean.hashCode(b1);
    result = 31 * result + Objects.hashCode(o2);
    result = 31 * result + Objects.hashCode(o3);
    result = 31 * result + Objects.hashCode(o4);
    result = 31 * result + Objects.hashCode(o5);
    result = 31 * result + Objects.hashCode(o6);
    result = 31 * result + Objects.hashCode(o7);
    return result;
  }

  public static int hashII(int i1, int i2) {
    int result = 1;
    result = 31 * result + i1;
    result = 31 * result + i2;
    return result;
  }

  public static int hashIIZ(int i1, int i2, boolean b3) {
    int result = 1;
    result = 31 * result + i1;
    result = 31 * result + i2;
    result = 31 * result + Boolean.hashCode(b3);
    return result;
  }

  public static int hashIIIL(int i1, int i2, int i3, Object o4) {
    int result = 1;
    result = 31 * result + i1;
    result = 31 * result + i2;
    result = 31 * result + i3;
    result = 31 * result + Objects.hashCode(o4);
    return result;
  }

  public static int hashIIL(int i1, int i2, Object o3) {
    int result = 1;
    result = 31 * result + i1;
    result = 31 * result + i2;
    result = 31 * result + Objects.hashCode(o3);
    return result;
  }

  public static int hashIJL(int i1, long l2, Object o3) {
    int result = 1;
    result = 31 * result + i1;
    result = 31 * result + Long.hashCode(l2);
    result = 31 * result + Objects.hashCode(o3);
    return result;
  }

  public static int hashIL(int i1, Object o2) {
    int result = 1;
    result = 31 * result + i1;
    result = 31 * result + Objects.hashCode(o2);
    return result;
  }

  public static int hashILL(int i1, Object o2, Object o3) {
    int result = 1;
    result = 31 * result + i1;
    result = 31 * result + Objects.hashCode(o2);
    result = 31 * result + Objects.hashCode(o3);
    return result;
  }

  public static int hashJJ(long l1, long l2) {
    int result = 1;
    result = 31 * result + Long.hashCode(l1);
    result = 31 * result + Long.hashCode(l2);
    return result;
  }

  public static int hashLL(Object o1, Object o2) {
    int result = 1;
    result = 31 * result + Objects.hashCode(o1);
    result = 31 * result + Objects.hashCode(o2);
    return result;
  }

  public static int hashLLL(Object o1, Object o2, Object o3) {
    int result = 1;
    result = 31 * result + Objects.hashCode(o1);
    result = 31 * result + Objects.hashCode(o2);
    result = 31 * result + Objects.hashCode(o3);
    return result;
  }

  public static int hashLLLL(Object o1, Object o2, Object o3, Object o4) {
    int result = 1;
    result = 31 * result + Objects.hashCode(o1);
    result = 31 * result + Objects.hashCode(o2);
    result = 31 * result + Objects.hashCode(o3);
    result = 31 * result + Objects.hashCode(o4);
    return result;
  }

  public static int hashLLLLL(Object o1, Object o2, Object o3, Object o4, Object o5) {
    int result = 1;
    result = 31 * result + Objects.hashCode(o1);
    result = 31 * result + Objects.hashCode(o2);
    result = 31 * result + Objects.hashCode(o3);
    result = 31 * result + Objects.hashCode(o4);
    result = 31 * result + Objects.hashCode(o5);
    return result;
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean identical(Object a, Object b) {
    return a == b;
  }

  public static boolean notIdentical(Object a, Object b) {
    return !identical(a, b);
  }

  public static <S, T> T mapNotNull(S object, Function<? super S, ? extends T> fn) {
    if (object != null) {
      return fn.apply(object);
    }
    return null;
  }

  /**
   * If the object is null return the default value, otherwise compute the function with the value.
   */
  public static <S, T> T mapNotNullOrDefault(S object, T def, Function<? super S, ? extends T> fn) {
    if (object != null) {
      return fn.apply(object);
    }
    return def;
  }
}
