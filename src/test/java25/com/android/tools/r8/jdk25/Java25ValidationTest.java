// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk25;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.WithAllCfRuntimes;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

// Test to validate that the tests_java_25 module is built with JDK-25.
public class Java25ValidationTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  protected static CfVersion extractClassFileVersion(Path classFile) throws IOException {
    class ClassFileVersionExtractor extends ClassVisitor {
      private int version;

      private ClassFileVersionExtractor() {
        super(ASM_VERSION);
      }

      @Override
      public void visit(
          int version,
          int access,
          String name,
          String signature,
          String superName,
          String[] interfaces) {
        this.version = version;
      }

      @Override
      public void visitAttribute(Attribute attribute) {}

      CfVersion getClassFileVersion() {
        return CfVersion.fromRaw(version);
      }
    }

    ClassReader reader = new ClassReader(Files.newInputStream(classFile));
    ClassFileVersionExtractor extractor = new ClassFileVersionExtractor();
    reader.accept(
        extractor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return extractor.getClassFileVersion();
  }

  @ParameterizedTest
  @WithAllCfRuntimes
  public void testTestClassClassFileVersion(TestParameters parameters) throws Exception {
    assertEquals(
        CfVersion.V25,
        extractClassFileVersion(ToolHelper.getClassFileForTestClass(TestClass.class)));
  }

  @ParameterizedTest
  @WithAllCfRuntimes
  public void testRunning(TestParameters parameters) throws Exception {
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.getCfRuntime().isOlderThan(CfVm.JDK25),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class),
            r -> r.assertSuccessWithOutput(EXPECTED));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
