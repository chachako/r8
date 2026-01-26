// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk25.backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class MathBackportJava25Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK25)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public MathBackportJava25Test(TestParameters parameters) {
    super(parameters, Math.class, MathBackportJava25Main.class);
  }

  public static class MathBackportJava25Main {
    public static void main(String[] args) {
      testUnsignedMultiplyExactLongLong();
      testUnsignedMultiplyExactLongInt();
      testUnsignedMultiplyExactIntInt();
      testUnsignedPowExactLong();
      testUnsignedPowExactInt();
      testPowExactLong();
      testPowExactInt();
    }

    static void testPowExactLong() {
      try {
        Math.powExact(42L, -1);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.powExact(0L, 0));
      assertEquals(0, Math.powExact(0L, 1));
      assertEquals(0, Math.powExact(0L, 2));

      assertEquals(1, Math.powExact(-1L, 0));
      assertEquals(-1, Math.powExact(-1L, 1));
      assertEquals(1, Math.powExact(-1L, 2));

      assertEquals(1, Math.powExact(1L, 0));
      assertEquals(1, Math.powExact(1L, 1));
      assertEquals(1, Math.powExact(1L, 2));

      assertEquals(1, Math.powExact(2L, 0));
      assertEquals(2, Math.powExact(2L, 1));
      assertEquals(4, Math.powExact(2L, 2));

      assertEquals(1, Math.powExact(Long.MIN_VALUE, 0));
      assertEquals(Long.MIN_VALUE, Math.powExact(Long.MIN_VALUE, 1));
      try {
        Math.powExact(Long.MIN_VALUE, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.powExact(Long.MAX_VALUE, 0));
      assertEquals(Long.MAX_VALUE, Math.powExact(Long.MAX_VALUE, 1));
      try {
        Math.powExact(Long.MAX_VALUE, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(4096, Math.powExact((long) (256 / 16), 3));
      assertEquals(512, Math.powExact((long) (256 / 32), 3));

      assertEquals(1L << 62, Math.powExact(2L, 62));
      assertEquals(Long.MIN_VALUE, Math.powExact(-2L, 63));
      try {
        Math.powExact(2L, 63);
        fail();
      } catch (ArithmeticException ae) {
      }
    }

    static void testPowExactInt() {
      try {
        Math.powExact(42, -1);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.powExact(0, 0));
      assertEquals(0, Math.powExact(0, 1));
      assertEquals(0, Math.powExact(0, 2));

      assertEquals(1, Math.powExact(-1, 0));
      assertEquals(-1, Math.powExact(-1, 1));
      assertEquals(1, Math.powExact(-1, 2));

      assertEquals(1, Math.powExact(1, 0));
      assertEquals(1, Math.powExact(1, 1));
      assertEquals(1, Math.powExact(1, 2));

      assertEquals(1, Math.powExact(2, 0));
      assertEquals(2, Math.powExact(2, 1));
      assertEquals(4, Math.powExact(2, 2));

      assertEquals(1, Math.powExact(Integer.MIN_VALUE, 0));
      assertEquals(Integer.MIN_VALUE, Math.powExact(Integer.MIN_VALUE, 1));
      try {
        Math.powExact(Integer.MIN_VALUE, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.powExact(Integer.MAX_VALUE, 0));
      assertEquals(Integer.MAX_VALUE, Math.powExact(Integer.MAX_VALUE, 1));
      try {
        Math.powExact(Integer.MAX_VALUE, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(4096, Math.powExact(256 / 16, 3));
      assertEquals(512, Math.powExact(256 / 32, 3));

      assertEquals(1 << -2, Math.powExact(2, Integer.SIZE - 2));
      assertEquals(-1 << -1, Math.powExact(-2, Integer.SIZE - 1));

      try {
        Math.powExact(2, 31);
        fail();
      } catch (ArithmeticException ae) {
      }
    }

    static void testUnsignedPowExactLong() {
      try {
        Math.unsignedPowExact(42L, -1);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.unsignedPowExact(0L, 0));
      assertEquals(0, Math.unsignedPowExact(0L, 1));
      assertEquals(0, Math.unsignedPowExact(0L, 2));

      assertEquals(1, Math.unsignedPowExact(-1L, 0));
      assertEquals(-1, Math.unsignedPowExact(-1L, 1));
      try {
        Math.unsignedPowExact(-1L, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.unsignedPowExact(1L, 0));
      assertEquals(1, Math.unsignedPowExact(1L, 1));
      assertEquals(1, Math.unsignedPowExact(1L, 2));

      assertEquals(1, Math.unsignedPowExact(2L, 0));
      assertEquals(2, Math.unsignedPowExact(2L, 1));
      assertEquals(4, Math.unsignedPowExact(2L, 2));

      assertEquals(1, Math.unsignedPowExact(Long.MIN_VALUE, 0));
      assertEquals(Long.MIN_VALUE, Math.unsignedPowExact(Long.MIN_VALUE, 1));
      try {
        Math.unsignedPowExact(Long.MIN_VALUE, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.unsignedPowExact(Long.MAX_VALUE, 0));
      assertEquals(Long.MAX_VALUE, Math.unsignedPowExact(Long.MAX_VALUE, 1));
      try {
        Math.unsignedPowExact(Long.MAX_VALUE, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(4096, Math.unsignedPowExact((long) (256 / 16), 3));
      assertEquals(512, Math.unsignedPowExact((long) (256 / 32), 3));

      assertEquals(Long.MIN_VALUE, Math.unsignedPowExact(2L, 63));
      try {
        Math.unsignedPowExact(2L, 64);
        fail();
      } catch (ArithmeticException ae) {
      }
    }

    static void testUnsignedPowExactInt() {
      try {
        Math.unsignedPowExact(42, -1);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.unsignedPowExact(0, 0));
      assertEquals(0, Math.unsignedPowExact(0, 1));
      assertEquals(0, Math.unsignedPowExact(0, 2));

      assertEquals(1, Math.unsignedPowExact(-1, 0));
      assertEquals(-1, Math.unsignedPowExact(-1, 1));
      try {
        Math.unsignedPowExact(-1, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.unsignedPowExact(1, 0));
      assertEquals(1, Math.unsignedPowExact(1, 1));
      assertEquals(1, Math.unsignedPowExact(1, 2));

      assertEquals(1, Math.unsignedPowExact(2, 0));
      assertEquals(2, Math.unsignedPowExact(2, 1));
      assertEquals(4, Math.unsignedPowExact(2, 2));

      assertEquals(1, Math.unsignedPowExact(Integer.MIN_VALUE, 0));
      assertEquals(Integer.MIN_VALUE, Math.unsignedPowExact(Integer.MIN_VALUE, 1));
      try {
        Math.unsignedPowExact(Integer.MIN_VALUE, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1, Math.unsignedPowExact(Integer.MAX_VALUE, 0));
      assertEquals(Integer.MAX_VALUE, Math.unsignedPowExact(Integer.MAX_VALUE, 1));
      try {
        Math.unsignedPowExact(Integer.MAX_VALUE, 2);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(4096, Math.unsignedPowExact(256 / 16, 3));
      assertEquals(512, Math.unsignedPowExact(256 / 32, 3));

      assertEquals(Integer.MIN_VALUE, Math.unsignedPowExact(2, 31));
      try {
        Math.unsignedPowExact(2, 32);
        fail();
      } catch (ArithmeticException ae) {
      }
    }

    static void testUnsignedMultiplyExactLongLong() {
      assertEquals(0L, Math.unsignedMultiplyExact(0L, 0L));
      assertEquals(0L, Math.unsignedMultiplyExact(0L, 1L));
      assertEquals(1L, Math.unsignedMultiplyExact(1L, 1L));

      assertEquals(256L, Math.unsignedMultiplyExact(16L, 16L));

      assertEquals(-2L, Math.unsignedMultiplyExact(Long.MAX_VALUE, 2L));
      assertEquals(Long.MIN_VALUE, Math.unsignedMultiplyExact(Long.MIN_VALUE, 1L));

      try {
        Math.unsignedMultiplyExact(Long.MAX_VALUE, 16L);
        fail();
      } catch (ArithmeticException ae) {
      }
      try {
        Math.unsignedMultiplyExact(Long.MIN_VALUE, 16L);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1L << 63, Math.unsignedMultiplyExact(1L << 31, 1L << 32));
      assertEquals(1L << 63, Math.unsignedMultiplyExact(1L << 25, 1L << 38));
      assertEquals(-1L, Math.unsignedMultiplyExact((1L << 32) + 1, (1L << 32) - 1));

      try {
        Math.unsignedMultiplyExact(-1L, -1L);
        fail();
      } catch (ArithmeticException ae) {
      }
    }

    static void testUnsignedMultiplyExactLongInt() {
      assertEquals(0, Math.unsignedMultiplyExact(0L, 0));
      assertEquals(0, Math.unsignedMultiplyExact(0L, 1));
      assertEquals(1, Math.unsignedMultiplyExact(1L, 1));

      assertEquals(256, Math.unsignedMultiplyExact(16L, 16));

      assertEquals(-2, Math.unsignedMultiplyExact(Long.MAX_VALUE, 2));
      assertEquals(Long.MIN_VALUE, Math.unsignedMultiplyExact(Long.MIN_VALUE, 1));

      try {
        Math.unsignedMultiplyExact(Long.MAX_VALUE, 16);
        fail();
      } catch (ArithmeticException ae) {
      }
      try {
        Math.unsignedMultiplyExact(Long.MIN_VALUE, 16);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1L << 50, Math.unsignedMultiplyExact(1L << 25, 1 << 25));
      assertEquals(-1L, Math.unsignedMultiplyExact((1L << 32) + 1, -1));

      try {
        Math.unsignedMultiplyExact(-1L, -1);
        fail();
      } catch (ArithmeticException ae) {
      }
    }

    static void testUnsignedMultiplyExactIntInt() {
      assertEquals(0, Math.unsignedMultiplyExact(0, 0));
      assertEquals(0, Math.unsignedMultiplyExact(0, 1));
      assertEquals(1, Math.unsignedMultiplyExact(1, 1));

      assertEquals(256, Math.unsignedMultiplyExact(16, 16));

      assertEquals(-2, Math.unsignedMultiplyExact(Integer.MAX_VALUE, 2));
      assertEquals(Integer.MIN_VALUE, Math.unsignedMultiplyExact(Integer.MIN_VALUE, 1));

      try {
        Math.unsignedMultiplyExact(Integer.MAX_VALUE, 16);
        fail();
      } catch (ArithmeticException ae) {
      }
      try {
        Math.unsignedMultiplyExact(Integer.MIN_VALUE, 16);
        fail();
      } catch (ArithmeticException ae) {
      }

      assertEquals(1 << 31, Math.unsignedMultiplyExact(1 << 15, 1 << 16));
      assertEquals(1 << 31, Math.unsignedMultiplyExact(1 << 10, 1 << 21));
      assertEquals(-1, Math.unsignedMultiplyExact((1 << 16) + 1, (1 << 16) - 1));

      try {
        Math.unsignedMultiplyExact(-1, -1);
        fail();
      } catch (ArithmeticException ae) {
      }
    }

    static void assertEquals(int x, int y) {
      if (x != y) {
        throw new RuntimeException("Not equals " + x + " and " + y);
      }
    }

    static void assertEquals(long x, long y) {
      if (x != y) {
        throw new RuntimeException("Not equals " + x + " and " + y);
      }
    }

    static void fail() {
      throw new RuntimeException("Test fails.");
    }
  }
}
