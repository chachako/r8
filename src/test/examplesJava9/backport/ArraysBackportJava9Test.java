// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ArraysBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public ArraysBackportJava9Test(TestParameters parameters) {
    super(parameters, Arrays.class, ByteBackportJava9Main.class);

    // The range version of Arrays.equals where added in Android T, but due to slower
    // implementation they are backported until V (b/433540561).
    registerTarget(AndroidApiLevel.V, 84);
  }

  public static class ByteBackportJava9Main extends MiniAssert {

    public static void main(String[] args) {
      testEqualsIntRange();
      testEqualsLongRange();
      testEqualsShortRange();
      testEqualsByteRange();
      testEqualsCharRange();
      testEqualsBooleanRange();
      testEqualsFloatRange();
      testEqualsDoubleRange();
    }

    private static void testEqualsIntRange() {
      assertTrue(Arrays.equals(new int[] {}, 0, 0, new int[] {}, 0, 0));
      assertTrue(
          Arrays.equals(new int[] {Integer.MAX_VALUE}, 0, 1, new int[] {Integer.MAX_VALUE}, 0, 1));
      assertTrue(Arrays.equals(new int[] {-1, 0, 1}, 0, 3, new int[] {-1, 0, 1}, 0, 3));
      int[] a = new int[] {-3, -2, -1, 0, 1, 2, 3};
      int[] b = new int[] {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};
      assertTrue(Arrays.equals(a, 0, 1, a, 0, 1));
      assertTrue(Arrays.equals(a, 2, 5, b, 1, 4));
      assertTrue(Arrays.equals(a, 1, 1, b, 2, 2));

      assertFalse(Arrays.equals(a, 2, 3, b, 2, 3));
      assertFalse(Arrays.equals(a, 3, 3, b, 1, 3));

      try {
        assertFalse(Arrays.equals(null, 2, 3, b, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
      try {
        assertFalse(Arrays.equals(a, 2, 3, null, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
    }

    private static void testEqualsLongRange() {
      assertTrue(Arrays.equals(new long[] {}, 0, 0, new long[] {}, 0, 0));
      assertTrue(
          Arrays.equals(new long[] {Long.MAX_VALUE}, 0, 1, new long[] {Long.MAX_VALUE}, 0, 1));
      assertTrue(Arrays.equals(new long[] {-1L, 0L, 1L}, 0, 3, new long[] {-1L, 0L, 1L}, 0, 3));
      long[] a = new long[] {-3L, -2L, -1L, 0L, 1L, 2L, 3L};
      long[] b = new long[] {Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE};
      assertTrue(Arrays.equals(a, 0, 1, a, 0, 1));
      assertTrue(Arrays.equals(a, 2, 5, b, 1, 4));
      assertTrue(Arrays.equals(a, 1, 1, b, 2, 2));

      assertFalse(Arrays.equals(a, 2, 3, b, 2, 3));
      assertFalse(Arrays.equals(a, 3, 3, b, 1, 3));

      try {
        assertFalse(Arrays.equals(null, 2, 3, b, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
      try {
        assertFalse(Arrays.equals(a, 2, 3, null, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
    }

    private static void testEqualsShortRange() {
      assertTrue(Arrays.equals(new short[] {}, 0, 0, new short[] {}, 0, 0));
      assertTrue(
          Arrays.equals(new short[] {Short.MAX_VALUE}, 0, 1, new short[] {Short.MAX_VALUE}, 0, 1));
      assertTrue(Arrays.equals(new short[] {-1, 0, 1}, 0, 3, new short[] {-1, 0, 1}, 0, 3));
      short[] a = new short[] {-3, -2, -1, 0, 1, 2, 3};
      short[] b = new short[] {Short.MIN_VALUE, -1, 0, 1, Short.MAX_VALUE};
      assertTrue(Arrays.equals(a, 0, 1, a, 0, 1));
      assertTrue(Arrays.equals(a, 2, 5, b, 1, 4));
      assertTrue(Arrays.equals(a, 1, 1, b, 2, 2));

      assertFalse(Arrays.equals(a, 2, 3, b, 2, 3));
      assertFalse(Arrays.equals(a, 3, 3, b, 1, 3));

      try {
        assertFalse(Arrays.equals(null, 2, 3, b, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
      try {
        assertFalse(Arrays.equals(a, 2, 3, null, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
    }

    private static void testEqualsByteRange() {
      assertTrue(Arrays.equals(new byte[] {}, 0, 0, new byte[] {}, 0, 0));
      assertTrue(
          Arrays.equals(new byte[] {Byte.MAX_VALUE}, 0, 1, new byte[] {Byte.MAX_VALUE}, 0, 1));
      assertTrue(Arrays.equals(new byte[] {-1, 0, 1}, 0, 3, new byte[] {-1, 0, 1}, 0, 3));
      byte[] a = new byte[] {-3, -2, -1, 0, 1, 2, 3};
      byte[] b = new byte[] {Byte.MIN_VALUE, -1, 0, 1, Byte.MAX_VALUE};
      assertTrue(Arrays.equals(a, 0, 1, a, 0, 1));
      assertTrue(Arrays.equals(a, 2, 5, b, 1, 4));
      assertTrue(Arrays.equals(a, 1, 1, b, 2, 2));

      assertFalse(Arrays.equals(a, 2, 3, b, 2, 3));
      assertFalse(Arrays.equals(a, 3, 3, b, 1, 3));

      try {
        assertFalse(Arrays.equals(null, 2, 3, b, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
      try {
        assertFalse(Arrays.equals(a, 2, 3, null, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
    }

    private static void testEqualsCharRange() {
      assertTrue(Arrays.equals(new char[] {}, 0, 0, new char[] {}, 0, 0));
      assertTrue(
          Arrays.equals(
              new char[] {Character.MAX_VALUE}, 0, 1, new char[] {Character.MAX_VALUE}, 0, 1));
      assertTrue(Arrays.equals(new char[] {'a', 'b', 'c'}, 0, 3, new char[] {'a', 'b', 'c'}, 0, 3));
      char[] a = new char[] {'x', 'y', 'a', 'b', 'c', 'd', 'e'};
      char[] b = new char[] {Character.MIN_VALUE, 'a', 'b', 'c', Character.MAX_VALUE};
      assertTrue(Arrays.equals(a, 0, 1, a, 0, 1));
      assertTrue(Arrays.equals(a, 2, 5, b, 1, 4));
      assertTrue(Arrays.equals(a, 1, 1, b, 2, 2));

      assertFalse(Arrays.equals(a, 2, 3, b, 2, 3));
      assertFalse(Arrays.equals(a, 3, 3, b, 1, 3));

      try {
        assertFalse(Arrays.equals(null, 2, 3, b, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
      try {
        assertFalse(Arrays.equals(a, 2, 3, null, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
    }

    private static void testEqualsBooleanRange() {
      assertTrue(Arrays.equals(new boolean[] {}, 0, 0, new boolean[] {}, 0, 0));
      assertTrue(Arrays.equals(new boolean[] {true}, 0, 1, new boolean[] {true}, 0, 1));
      assertTrue(
          Arrays.equals(
              new boolean[] {false, true, false}, 0, 3, new boolean[] {false, true, false}, 0, 3));
      boolean[] a = new boolean[] {true, true, false, true, false, false, true};
      boolean[] b = new boolean[] {false, false, true, false, true};
      assertTrue(Arrays.equals(a, 0, 1, a, 0, 1));
      assertTrue(Arrays.equals(a, 2, 5, b, 1, 4));
      assertTrue(Arrays.equals(a, 1, 1, b, 2, 2));

      assertFalse(Arrays.equals(a, 2, 3, b, 2, 3));
      assertFalse(Arrays.equals(a, 3, 3, b, 1, 3));

      try {
        assertFalse(Arrays.equals(null, 2, 3, b, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
      try {
        assertFalse(Arrays.equals(a, 2, 3, null, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
    }

    private static void testEqualsFloatRange() {
      assertTrue(Arrays.equals(new float[] {}, 0, 0, new float[] {}, 0, 0));
      assertTrue(
          Arrays.equals(new float[] {Float.MAX_VALUE}, 0, 1, new float[] {Float.MAX_VALUE}, 0, 1));
      assertTrue(
          Arrays.equals(
              new float[] {-1.0f, 0.0f, 1.0f}, 0, 3, new float[] {-1.0f, 0.0f, 1.0f}, 0, 3));
      float[] a = new float[] {-3.0f, -2.0f, -1.0f, 0.0f, 1.0f, 2.0f, 3.0f};
      float[] b = new float[] {Float.MIN_VALUE, -1.0f, 0.0f, 1.0f, Float.MAX_VALUE};
      float[] c = new float[] {Float.NaN, -0.0f, 0.0f, Float.NaN};
      assertTrue(Arrays.equals(a, 0, 1, a, 0, 1));
      assertTrue(Arrays.equals(a, 2, 5, b, 1, 4));
      assertTrue(Arrays.equals(a, 1, 1, b, 2, 2));
      assertTrue(Arrays.equals(c, 0, 1, c, 3, 4));

      assertFalse(Arrays.equals(a, 2, 3, b, 2, 3));
      assertFalse(Arrays.equals(a, 3, 3, b, 1, 3));
      assertFalse(Arrays.equals(c, 1, 2, c, 2, 3));

      try {
        assertFalse(Arrays.equals(null, 2, 3, b, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
      try {
        assertFalse(Arrays.equals(a, 2, 3, null, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
    }

    private static void testEqualsDoubleRange() {
      assertTrue(Arrays.equals(new double[] {}, 0, 0, new double[] {}, 0, 0));
      assertTrue(
          Arrays.equals(
              new double[] {Double.MAX_VALUE}, 0, 1, new double[] {Double.MAX_VALUE}, 0, 1));
      assertTrue(
          Arrays.equals(
              new double[] {-1.0d, 0.0d, 1.0d}, 0, 3, new double[] {-1.0d, 0.0d, 1.0d}, 0, 3));
      double[] a = new double[] {-3.0d, -2.0d, -1.0d, 0.0d, 1.0d, 2.0d, 3.0d};
      double[] b = new double[] {Double.MIN_VALUE, -1.0d, 0.0d, 1.0d, Double.MAX_VALUE};
      double[] c = new double[] {Double.NaN, -0.0d, 0.0d, Double.NaN};
      assertTrue(Arrays.equals(a, 0, 1, a, 0, 1));
      assertTrue(Arrays.equals(a, 2, 5, b, 1, 4));
      assertTrue(Arrays.equals(a, 1, 1, b, 2, 2));
      assertTrue(Arrays.equals(c, 0, 1, c, 3, 4));

      assertFalse(Arrays.equals(a, 2, 3, b, 2, 3));
      assertFalse(Arrays.equals(a, 3, 3, b, 1, 3));
      assertFalse(Arrays.equals(c, 1, 2, c, 2, 3));

      try {
        assertFalse(Arrays.equals(null, 2, 3, b, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
      try {
        assertFalse(Arrays.equals(a, 2, 3, null, 1, 2));
        fail("Expected NullPointerException");
      } catch (NullPointerException e) {
        // Expected.
      }
    }
  }
}
