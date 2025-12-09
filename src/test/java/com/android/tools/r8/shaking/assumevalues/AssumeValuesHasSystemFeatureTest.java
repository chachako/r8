// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumevalues;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeValuesHasSystemFeatureTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    String pmTypeName = PackageManager.class.getTypeName();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumevalues class " + pmTypeName + " {",
            "  java.lang.String hasSystemFeature(* = \"a\") return \"a\";",
            "  java.lang.String hasSystemFeature(* = \"b\") return \"b\";",
            "  java.lang.String hasSystemFeature(* = " + pmTypeName + ".FEATURE_C) return \"c\";",
            "  java.lang.String hasSystemFeature(* = " + pmTypeName + ".FEATURE_D) return \"d\";",
            "  java.lang.String hasSystemFeature(* = " + pmTypeName + ".FEATURE_E) return \"e\";",
            "  java.lang.String hasSystemFeature(* = " + pmTypeName + ".FEATURE_F) return \"f\";",
            "}")
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b", "c", "d", "e", "f");
  }

  @Test
  public void testPackageManagerOnClasspath() throws Exception {
    String pmTypeName = PackageManager.class.getTypeName();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addClasspathClasses(PackageManager.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumevalues class " + pmTypeName + " {",
            "  java.lang.String hasSystemFeature(* = \"a\") return \"a\";",
            "  java.lang.String hasSystemFeature(* = \"b\") return \"b\";",
            "  java.lang.String hasSystemFeature(* = " + pmTypeName + ".FEATURE_C) return \"c\";",
            "  java.lang.String hasSystemFeature(* = " + pmTypeName + ".FEATURE_D) return \"d\";",
            "  java.lang.String hasSystemFeature(* = " + pmTypeName + ".FEATURE_E) return \"e\";",
            "  java.lang.String hasSystemFeature(* = " + pmTypeName + ".FEATURE_F) return \"f\";",
            "}")
        .setMinApi(parameters)
        .compile()
        .addRunClasspathClasses(PackageManager.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b", "c", "d", "e", "f");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(PackageManager.hasSystemFeature("a"));
      System.out.println(PackageManager.hasSystemFeature("b"));
      System.out.println(PackageManager.hasSystemFeature(PackageManager.FEATURE_C));
      System.out.println(PackageManager.hasSystemFeature(PackageManager.FEATURE_D));
      System.out.println(PackageManager.hasSystemFeature(PackageManager.FEATURE_E));
      System.out.println(PackageManager.hasSystemFeature(PackageManager.FEATURE_F));
    }
  }

  public static class PackageManager {

    // Intentionally final.
    public static final String FEATURE_C = "c";
    public static final String FEATURE_D = "d";

    // Intentionally non-final.
    public static String FEATURE_E = "e";
    public static String FEATURE_F = "f";

    public static String hasSystemFeature(String feature) {
      return null;
    }
  }
}
