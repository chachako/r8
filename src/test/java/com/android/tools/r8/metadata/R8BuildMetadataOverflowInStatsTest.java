// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8BuildMetadataOverflowInStatsTest extends TestBase {

  private static final String versionWithFix = "99.99.99";

  private static String json;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    json =
        testForR8(getStaticTemp(), Backend.DEX)
            .addProgramClasses(Main.class)
            .addKeepMainRule(Main.class)
            .addOptionsModification(options -> options.buildMetadataVersion = versionWithFix)
            .collectBuildMetadata()
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .getBuildMetadata()
            .toJson();
  }

  @Test
  public void test() throws Exception {
    // 8.9
    runTest("8.9.42", true);

    // 8.10
    runTest("8.10.13-dev", true);
    runTest("8.10.14", true);
    runTest("8.10.33", false);

    // 8.11
    runTest("8.11.13-dev", true);
    runTest("8.11.14", true);
    runTest("8.11.23", false);

    // 8.12
    runTest("8.12.13-dev", true);
    runTest("8.12.14", true);
    runTest("8.12.19", false);

    // 8.13
    runTest("8.13.2-dev", true);
    runTest("8.13.3", true);
    runTest("8.13.4", false);

    // 9.0
    runTest("9.0.0-dev", true);
    runTest("9.0.1-dev", false);
    runTest("9.0.21", false);

    // 9.1
    runTest("9.1.0-dev", false);
    runTest("9.1.42", false);

    // main
    runTest("main", true);
  }

  private void runTest(String testVersion, boolean mayHaveOverflow) {
    // Replace the version in the JSON by the test version.
    String currentVersionInJson = "\"version\":\"" + versionWithFix + "\"";
    int versionStart = json.indexOf(currentVersionInJson) + "\"version\":\"".length();
    String jsonWithNewVersion =
        json.substring(0, versionStart)
            + testVersion
            + json.substring(versionStart + versionWithFix.length());

    // Check that the new JSON looks correct.
    String testVersionInJson = "\"version\":\"" + testVersion + "\"";
    assertThat(jsonWithNewVersion, containsString(testVersionInJson));

    // Verify that the build metadata is stripped when deserializing.
    R8BuildMetadata buildMetadata = R8BuildMetadata.fromJson(jsonWithNewVersion);
    if (mayHaveOverflow) {
      assertNull(buildMetadata.getStatsMetadata());
    } else {
      assertNotNull(buildMetadata.getStatsMetadata());
    }

    // Verify that round tripping works after stripping.
    R8BuildMetadata buildMetadataRoundtrip = R8BuildMetadata.fromJson(buildMetadata.toJson());
    if (mayHaveOverflow) {
      assertNull(buildMetadataRoundtrip.getStatsMetadata());
    } else {
      assertNotNull(buildMetadataRoundtrip.getStatsMetadata());
    }
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
