// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package backport;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.backports.AbstractBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ByteBackportJava9Test extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return TestBase.getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public ByteBackportJava9Test(TestParameters parameters) {
    super(parameters, Byte.class, ByteBackportJava9Main.class);

    // Byte.compareUnsigned added in API 31.
    registerTarget(AndroidApiLevel.S, 16);
  }

  public static class ByteBackportJava9Main {

    private static final byte MIN_UNSIGNED_VALUE = (byte) 0;
    private static final byte MAX_UNSIGNED_VALUE = (byte) -1;

    public static void main(String[] args) {
      testCompareUnsigned();
    }

    private static void testCompareUnsigned() {
      assertTrue(Byte.compareUnsigned(MIN_UNSIGNED_VALUE, MIN_UNSIGNED_VALUE) == 0);
      assertTrue(Byte.compareUnsigned(MIN_UNSIGNED_VALUE, Byte.MAX_VALUE) < 0);
      assertTrue(Byte.compareUnsigned(MIN_UNSIGNED_VALUE, Byte.MIN_VALUE) < 0);
      assertTrue(Byte.compareUnsigned(MIN_UNSIGNED_VALUE, MAX_UNSIGNED_VALUE) < 0);

      assertTrue(Byte.compareUnsigned(Byte.MAX_VALUE, MIN_UNSIGNED_VALUE) > 0);
      assertTrue(Byte.compareUnsigned(Byte.MAX_VALUE, Byte.MAX_VALUE) == 0);
      assertTrue(Byte.compareUnsigned(Byte.MAX_VALUE, Byte.MIN_VALUE) < 0);
      assertTrue(Byte.compareUnsigned(Byte.MAX_VALUE, MAX_UNSIGNED_VALUE) < 0);

      assertTrue(Byte.compareUnsigned(Byte.MIN_VALUE, MIN_UNSIGNED_VALUE) > 0);
      assertTrue(Byte.compareUnsigned(Byte.MIN_VALUE, Byte.MAX_VALUE) > 0);
      assertTrue(Byte.compareUnsigned(Byte.MIN_VALUE, Byte.MIN_VALUE) == 0);
      assertTrue(Byte.compareUnsigned(Byte.MIN_VALUE, MAX_UNSIGNED_VALUE) < 0);

      assertTrue(Byte.compareUnsigned(MAX_UNSIGNED_VALUE, MIN_UNSIGNED_VALUE) > 0);
      assertTrue(Byte.compareUnsigned(MAX_UNSIGNED_VALUE, Byte.MAX_VALUE) > 0);
      assertTrue(Byte.compareUnsigned(MAX_UNSIGNED_VALUE, Byte.MIN_VALUE) > 0);
      assertTrue(Byte.compareUnsigned(MAX_UNSIGNED_VALUE, MAX_UNSIGNED_VALUE) == 0);
    }

    private static void assertTrue(boolean value) {
      if (!value) {
        throw new AssertionError("Expected <true> but was <false>");
      }
    }
  }
}
