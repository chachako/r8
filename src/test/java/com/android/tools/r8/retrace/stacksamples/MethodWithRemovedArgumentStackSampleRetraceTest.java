// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace.stacksamples;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceMethodElement;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.BeforeClass;
import org.junit.Test;

public class MethodWithRemovedArgumentStackSampleRetraceTest extends StackSampleRetraceTestBase {

  private static final String obfuscatedClassName = "a";
  private static final String obfuscatedMethodName = "a";

  static byte[] programClassFileData;

  // Map the line numbers of the Main class so that the line numbers start from 42.
  // This ensures that changes to the test does not impact the line numbers of the test data.
  @BeforeClass
  public static void setup() throws Exception {
    int firstLineNumber = getFirstLineNumber(Main.class);
    programClassFileData = transformer(Main.class).mapLineNumbers(42 - firstLineNumber).transform();
    assertEquals(42, getFirstLineNumber(programClassFileData));
  }

  @Test
  public void test() throws Exception {
    runTest(
        testBuilder ->
            testBuilder
                .addProgramClassFileData(programClassFileData)
                // TODO(b/480068080): Should not need to explicitly add -repackageclasses.
                .addKeepRules("-repackageclasses")
                .enableInliningAnnotations()
                .enableNeverClassInliningAnnotations());
  }

  @Override
  Class<?> getMainClass() {
    return Main.class;
  }

  @Override
  String getExpectedMap() {
    return StringUtils.joinLines(
        "com.android.tools.r8.retrace.stacksamples.MethodWithRemovedArgumentStackSampleRetraceTest$Main"
            + " -> a:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"MethodWithRemovedArgumentStackSampleRetraceTest.java\"}",
        "    1:1:void test(java.lang.Object):50:50 -> a",
        "      # {\"id\":\"com.android.tools.r8.residualsignature\",\"signature\":\"()V\"}",
        "    1:4:void main(java.lang.String[]):45:45 -> main");
  }

  @Override
  String getExpectedOutput() {
    return StringUtils.lines("foo");
  }

  @Override
  void inspectCode(CodeInspector inspector) {
    // Verify Main.test is renamed to a.a and that the unused argument has been removed.
    ClassSubject mainClass = inspector.clazz(Main.class);
    assertEquals(2, mainClass.allMethods().size());
    assertEquals(obfuscatedClassName, mainClass.getFinalName());

    MethodSubject testMethod = mainClass.uniqueMethodWithOriginalName("test");
    assertEquals(obfuscatedMethodName, testMethod.getFinalName());
    assertEquals(0, testMethod.getParameters().size());
  }

  @Override
  void testRetrace(R8TestCompileResultBase<?> compileResult) throws Exception {
    // Expected: `a.a` should retrace to `void Main.test(Object)`.
    RetraceMethodElement retraceResult =
        getSingleRetraceMethodElement(
            Reference.classFromTypeName(obfuscatedClassName), obfuscatedMethodName, compileResult);
    assertEquals(
        Reference.methodFromMethod(Main.class.getDeclaredMethod("test", Object.class)),
        retraceResult.getRetracedMethod().asKnown().getMethodReference());
    assertFalse(retraceResult.isCompilerSynthesized());
  }

  @NeverClassInline
  static class Main {

    public static void main(String[] args) {
      test(null);
    }

    @NeverInline
    static void test(Object unused) {
      System.out.println("foo");
    }
  }
}
