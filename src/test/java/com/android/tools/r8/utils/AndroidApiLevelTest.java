// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AndroidApiLevelTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AndroidApiLevelTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() {
    assertSame(AndroidApiLevel.V, AndroidApiLevel.getAndroidApiLevel(35, 0));
    assertSame(AndroidApiLevel.BAKLAVA, AndroidApiLevel.getAndroidApiLevel(36, 0));
    assertSame(AndroidApiLevel.BAKLAVA_1, AndroidApiLevel.getAndroidApiLevel(36, 1));
  }

  @Test
  public void testSerializationForAPIDatabase() {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.getAndroidApiLevelsSorted()) {
      assert apiLevel.serializeAsByte() > 0;
      assert apiLevel == AndroidApiLevel.deserializeFromByte(apiLevel.serializeAsByte());
      for (AndroidApiLevel apiLevel2 : AndroidApiLevel.getAndroidApiLevelsSorted()) {
        if (apiLevel != apiLevel2) {
          assert apiLevel.serializeAsByte() != apiLevel2.serializeAsByte();
        }
      }
    }
  }

  @Test
  public void testIllegal() {
    assertThrows(IllegalArgumentException.class, () -> AndroidApiLevel.getAndroidApiLevel(35, 1));
    assertThrows(IllegalArgumentException.class, () -> AndroidApiLevel.getAndroidApiLevel(36, 2));
  }
}
