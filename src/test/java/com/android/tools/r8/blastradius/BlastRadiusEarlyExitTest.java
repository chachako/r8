// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.NopDiagnosticsHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BlastRadiusEarlyExitTest extends TestBase {

  @Parameter(0)
  public boolean earlyExit;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, early exit: {0}")
  public static List<Object[]> data() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withNoneRuntime().build());
  }

  @Test
  public void test() throws Exception {
    Path outputPath = temp.newFolder().toPath().resolve("blastradius.pb");
    BooleanBox seenWaves = new BooleanBox();
    testForR8(Backend.DEX)
        .addProgramClasses(Main.class)
        .addOptionsModification(
            options -> {
              options.getBlastRadiusOptions().outputPath = outputPath.toString();
              options.getTestingOptions().waveModifier = waves -> seenWaves.set();
              if (earlyExit) {
                // Signal finished to the test program consumer.
                ProgramConsumer testProgramConsumer = options.programConsumer;
                assertNotNull(testProgramConsumer);
                testProgramConsumer.finished(new NopDiagnosticsHandler());
                // Use an empty consumer to exit early.
                options.programConsumer = DexIndexedConsumer.emptyConsumer();
              }
            })
        .setMinApi(AndroidApiLevel.N)
        .compile();
    assertTrue(Files.exists(outputPath));
    assertEquals(earlyExit, seenWaves.isFalse());
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
