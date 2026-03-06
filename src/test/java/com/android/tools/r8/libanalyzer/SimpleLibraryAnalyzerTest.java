// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.ByteArrayConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.libanalyzer.LibraryAnalyzerTestBuilder.AarOrJar;
import com.android.tools.r8.libanalyzer.proto.LibraryAnalyzerResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimpleLibraryAnalyzerTest extends TestBase {

  @Parameter(0)
  public AarOrJar aarOrJar;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, {0}")
  public static List<Object[]> data() {
    return buildParameters(AarOrJar.values(), getTestParameters().withNoneRuntime().build());
  }

  @Test
  public void test() throws Exception {
    testForLibraryAnalyzer()
        .addProgramClasses(Main.class)
        .addDefaultLibrary()
        .addKeepRules(
            "-keep class " + Main.class.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}")
        .setAarOrJar(aarOrJar)
        .setMinApi(AndroidApiLevel.getDefault())
        .compile()
        .apply(this::inspect);
  }

  @Test
  public void testArrayConsumer() throws Exception {
    Box<byte[]> output = new Box<>();
    testForLibraryAnalyzer()
        .addProgramClasses(Main.class)
        .addDefaultLibrary()
        .addKeepRules(
            "-keep class " + Main.class.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}")
        .setAarOrJar(aarOrJar)
        .setMinApi(AndroidApiLevel.getDefault())
        .setOutputConsumer((ByteArrayConsumer.ArrayConsumer) output::set)
        .compile();
    inspect(new LibraryAnalyzerCompileResult(LibraryAnalyzerResult.parseFrom(output.get())));
  }

  private void inspect(LibraryAnalyzerCompileResult compileResult) {
    compileResult
        .inspectD8CompileResult(D8CompileResultInspector::assertPresent)
        .inspectR8CompileResult(R8CompileResultInspector::assertPresent)
        .inspectValidateConsumerKeepRulesResult(i -> i.assertPresent().assertNoBlockedKeepRules());
  }

  static class Main {

    public static void main(String[] args) {}
  }
}
