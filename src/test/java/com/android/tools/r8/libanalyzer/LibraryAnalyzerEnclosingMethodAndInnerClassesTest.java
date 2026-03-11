// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryAnalyzerEnclosingMethodAndInnerClassesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testR8EnclosingMethod() throws Exception {
    assertFailsCompilation(
        () ->
            testForR8(Backend.DEX)
                .addProgramClasses(Main.class)
                .addKeepRules("-keep class *", "-keepattributes EnclosingMethod")
                .setMinApi(AndroidApiLevel.getDefault())
                .compile());
  }

  @Test
  public void testR8InnerClasses() throws Exception {
    assertFailsCompilation(
        () ->
            testForR8(Backend.DEX)
                .addProgramClasses(Main.class)
                .addKeepRules("-keep class *", "-keepattributes InnerClasses")
                .setMinApi(AndroidApiLevel.getDefault())
                .compile());
  }

  @Test
  public void testLibraryAnalyzerEnclosingMethod() throws Exception {
    testForLibraryAnalyzer()
        .addProgramClasses(Main.class)
        .addDefaultLibrary()
        .addKeepRules("-keep class *", "-keepattributes EnclosingMethod")
        .setAar()
        .setMinApi(AndroidApiLevel.getDefault())
        .compile();
  }

  @Test
  public void testLibraryAnalyzerInnerClasses() throws Exception {
    testForLibraryAnalyzer()
        .addProgramClasses(Main.class)
        .addDefaultLibrary()
        .addKeepRules("-keep class *", "-keepattributes InnerClasses")
        .setAar()
        .setMinApi(AndroidApiLevel.getDefault())
        .compile();
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
