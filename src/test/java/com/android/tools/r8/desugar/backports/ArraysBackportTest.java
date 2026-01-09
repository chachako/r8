// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.util.Arrays;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ArraysBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public ArraysBackportTest(TestParameters parameters) {
    super(parameters, Arrays.class, Main.class);
    registerTarget(AndroidApiLevel.B, 0);
    registerTarget(AndroidApiLevel.V, 68);
  }

  protected void configureD8Options(D8TestBuilder d8TestBuilder) throws IOException {
    d8TestBuilder.addOptionsModification(options -> options.testing.backportArraysEquals = true);
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testEqualsInt();
      testEqualsLong();
      testEqualsShort();
      testEqualsByte();
      testEqualsChar();
      testEqualsBoolean();
      testEqualsFloat();
      testEqualsDouble();
    }

    private static void testEqualsInt() {
      assertTrue(Arrays.equals(new int[] {}, new int[] {}));
      assertTrue(Arrays.equals(new int[] {Integer.MAX_VALUE}, new int[] {Integer.MAX_VALUE}));
      assertTrue(Arrays.equals(new int[] {-1, 0, 1}, new int[] {-1, 0, 1}));
      int[] a = new int[] {Integer.MIN_VALUE};
      int[] b = new int[] {Integer.MAX_VALUE};
      assertTrue(Arrays.equals(a, a));

      assertFalse(Arrays.equals(a, b));
      assertFalse(Arrays.equals(a, new int[] {-1, 0, 1}));
      assertFalse(Arrays.equals(null, b));
      assertFalse(Arrays.equals(a, null));
    }

    private static void testEqualsLong() {
      assertTrue(Arrays.equals(new long[] {}, new long[] {}));
      assertTrue(Arrays.equals(new long[] {Long.MAX_VALUE}, new long[] {Long.MAX_VALUE}));
      assertTrue(Arrays.equals(new long[] {-1, 0, 1}, new long[] {-1, 0, 1}));
      long[] a = new long[] {Long.MIN_VALUE};
      long[] b = new long[] {Long.MAX_VALUE};
      assertTrue(Arrays.equals(a, a));

      assertFalse(Arrays.equals(a, b));
      assertFalse(Arrays.equals(a, new long[] {-1, 0, 1}));
      assertFalse(Arrays.equals(null, b));
      assertFalse(Arrays.equals(a, null));
    }

    private static void testEqualsShort() {
      assertTrue(Arrays.equals(new short[] {}, new short[] {}));
      assertTrue(Arrays.equals(new short[] {Short.MAX_VALUE}, new short[] {Short.MAX_VALUE}));
      assertTrue(Arrays.equals(new short[] {-1, 0, 1}, new short[] {-1, 0, 1}));
      short[] a = new short[] {Short.MIN_VALUE};
      short[] b = new short[] {Short.MAX_VALUE};
      assertTrue(Arrays.equals(a, a));

      assertFalse(Arrays.equals(a, b));
      assertFalse(Arrays.equals(a, new short[] {-1, 0, 1}));
      assertFalse(Arrays.equals(null, b));
      assertFalse(Arrays.equals(a, null));
    }

    private static void testEqualsByte() {
      assertTrue(Arrays.equals(new byte[] {}, new byte[] {}));
      assertTrue(Arrays.equals(new byte[] {Byte.MAX_VALUE}, new byte[] {Byte.MAX_VALUE}));
      assertTrue(Arrays.equals(new byte[] {-1, 0, 1}, new byte[] {-1, 0, 1}));
      byte[] a = new byte[] {Byte.MIN_VALUE};
      byte[] b = new byte[] {Byte.MAX_VALUE};
      assertTrue(Arrays.equals(a, a));

      assertFalse(Arrays.equals(a, b));
      assertFalse(Arrays.equals(a, new byte[] {-1, 0, 1}));
      assertFalse(Arrays.equals(null, b));
      assertFalse(Arrays.equals(a, null));
    }

    private static void testEqualsChar() {
      assertTrue(Arrays.equals(new char[] {}, new char[] {}));
      assertTrue(Arrays.equals(new char[] {Character.MAX_VALUE}, new char[] {Character.MAX_VALUE}));
      assertTrue(Arrays.equals(new char[] {1, 2, 3}, new char[] {1, 2, 3}));
      char[] a = new char[] {Character.MIN_VALUE};
      char[] b = new char[] {Character.MAX_VALUE};
      assertTrue(Arrays.equals(a, a));

      assertFalse(Arrays.equals(a, b));
      assertFalse(Arrays.equals(a, new char[] {1, 2, 3}));
      assertFalse(Arrays.equals(null, b));
      assertFalse(Arrays.equals(a, null));
    }

    private static void testEqualsBoolean() {
      assertTrue(Arrays.equals(new boolean[] {}, new boolean[] {}));
      assertTrue(Arrays.equals(new boolean[] {true}, new boolean[] {true}));
      assertTrue(Arrays.equals(new boolean[] {false, true}, new boolean[] {false, true}));
      boolean[] a = new boolean[] {false};
      boolean[] b = new boolean[] {true};
      assertTrue(Arrays.equals(a, a));

      assertFalse(Arrays.equals(a, b));
      assertFalse(Arrays.equals(a, new boolean[] {false, true}));
      assertFalse(Arrays.equals(null, b));
      assertFalse(Arrays.equals(a, null));
    }

    private static void testEqualsFloat() {
      assertTrue(Arrays.equals(new float[] {}, new float[] {}));
      assertTrue(Arrays.equals(new float[] {Float.MAX_VALUE}, new float[] {Float.MAX_VALUE}));
      assertTrue(Arrays.equals(new float[] {-1, 0, 1}, new float[] {-1, 0, 1}));
      float[] a = new float[] {Float.MIN_VALUE};
      float[] b = new float[] {Float.MAX_VALUE};
      assertTrue(Arrays.equals(a, a));
      assertTrue(Arrays.equals(new float[] {Float.NaN}, new float[] {Float.NaN}));

      assertFalse(Arrays.equals(a, b));
      assertFalse(Arrays.equals(a, new float[] {-1, 0, 1}));
      assertFalse(Arrays.equals(null, b));
      assertFalse(Arrays.equals(a, null));
      assertFalse(Arrays.equals(new float[] {-0f}, new float[] {0f}));
    }

    private static void testEqualsDouble() {
      assertTrue(Arrays.equals(new double[] {}, new double[] {}));
      assertTrue(Arrays.equals(new double[] {Double.MAX_VALUE}, new double[] {Double.MAX_VALUE}));
      assertTrue(Arrays.equals(new double[] {-1, 0, 1}, new double[] {-1, 0, 1}));
      double[] a = new double[] {Double.MIN_VALUE};
      double[] b = new double[] {Double.MAX_VALUE};
      assertTrue(Arrays.equals(a, a));
      assertTrue(Arrays.equals(new double[] {Double.NaN}, new double[] {Double.NaN}));

      assertFalse(Arrays.equals(a, b));
      assertFalse(Arrays.equals(a, new double[] {-1, 0, 1}));
      assertFalse(Arrays.equals(null, b));
      assertFalse(Arrays.equals(a, null));
      assertFalse(Arrays.equals(new double[] {-0d}, new double[] {0d}));
    }
  }
}
