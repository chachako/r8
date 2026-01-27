// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.whitespaceinidentifiers;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.container.DexContainerFormatTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReadDexV040Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testD8() throws Exception {
    Path dexV040 =
        testForD8()
            .addProgramClasses(TestClass.class)
            .setMinApi(AndroidApiLevel.R)
            // Force DEX 040 for R, see b/269089718.
            .addOptionsModification(
                options -> options.getTestingOptions().dexVersion40FromApiLevel30 = true)
            .compile()
            .writeToZip();

    for (AndroidApiLevel apiLevel : AndroidApiLevel.getAndroidApiLevelsSorted()) {
      D8TestBuilder builder = testForD8().addProgramFiles(dexV040).setMinApi(apiLevel);
      // This succeeds for 'B', as that is considered "default" even if specified explicitly, and
      // in that case the API level is derived from reading DEX content.
      if (apiLevel.isBetweenNoneIncluded(AndroidApiLevel.B, AndroidApiLevel.R)) {
        assertThrows(
            CompilationFailedException.class,
            () ->
                builder.compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorMessageThatMatches(
                            containsString(
                                "Dex file with version '40' cannot be used with min sdk level '"
                                    + apiLevel.getName()
                                    + "'."))));
      } else {
        Path dex = builder.compile().writeToZip();
        if (apiLevel.isEqualTo(AndroidApiLevel.B)
            || apiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.BAKLAVA)) {
          // No API levels will currently set container DEX.
          DexContainerFormatTestBase.validateDex(dex, 1, DexVersion.V39);
        } else {
          DexContainerFormatTestBase.validateDex(dex, 1, apiLevel.getDexVersion());
        }
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}
