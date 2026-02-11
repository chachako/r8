// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.atomicFieldUpdaterOptimization;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class AtomicFieldUpdaterOptimizationMethods {

  public static Object getAndSet(UnsafeStub unsafe, Object o, long offset, Object newValue) {
    Object v;
    do {
      v = unsafe.getObjectVolatile(o, offset);
    } while (!unsafe.compareAndSwapObject(o, offset, v, newValue));
    return v;
  }

  static UnsafeStub getUnsafe() {
    Field theUnsafe = null;
    try {
      theUnsafe = UnsafeStub.class.getDeclaredField("theUnsafe");
    } catch (NoSuchFieldException e) {
      for (Field field : UnsafeStub.class.getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers())
            && UnsafeStub.class.isAssignableFrom(field.getType())) {
          theUnsafe = field;
          break;
        }
      }
      if (theUnsafe != null) {
        throw new UnsupportedOperationException("Couldn't find the Unsafe", e);
      }
    }
    theUnsafe.setAccessible(true);
    try {
      return (UnsafeStub) theUnsafe.get(null);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // This class exists so references can be rewritten into sun.misc.unsafe.
  public static class UnsafeStub {

    public Object getObjectVolatile(Object obj, long offset) {
      throw new RuntimeException("Stub called.");
    }

    public boolean compareAndSwapObject(
        Object receiver, long offset, Object expect, Object update) {
      throw new RuntimeException("Stub called.");
    }
  }
}
