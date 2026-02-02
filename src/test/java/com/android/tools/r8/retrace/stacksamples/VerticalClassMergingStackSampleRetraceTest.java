// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace.stacksamples;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceMethodElement;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;

public class VerticalClassMergingStackSampleRetraceTest extends StackSampleRetraceTestBase {

  private static final String obfuscatedClassName = "a";
  private static final String obfuscatedMethodNameAFoo = "d";
  private static final String obfuscatedMethodNameABar = "b";
  private static final String obfuscatedMethodNameABaz = "c";
  private static final String obfuscatedMethodNameBBar = "a";

  static List<byte[]> programClassFileData;

  // Map the line numbers of the Main class so that the line numbers start from 42.
  // This ensures that changes to the test does not impact the line numbers of the test data.
  @BeforeClass
  public static void setup() throws Exception {
    programClassFileData =
        Stream.of(Main.class, A.class, B.class)
            .map(
                clazz ->
                    transformer(clazz).mapLineNumbers(42 - getFirstLineNumber(clazz)).transform())
            .collect(Collectors.toList());
  }

  @Test
  public void test() throws Exception {
    runTest(
        testBuilder ->
            testBuilder
                .addProgramClassFileData(programClassFileData)
                .addOptionsModification(
                    options -> options.inlinerOptions().enableConstructorInlining = false)
                .addVerticallyMergedClassesInspector(
                    inspector ->
                        inspector.assertMergedIntoSubtype(A.class).assertNoOtherClassesMerged())
                .enableInliningAnnotations()
                .enableNeverClassInliningAnnotations()
                .enableNoMethodStaticizingAnnotations());
  }

  @Override
  Class<?> getMainClass() {
    return Main.class;
  }

  @Override
  String getExpectedMap() {
    return StringUtils.joinLines(
        "com.android.tools.r8.retrace.stacksamples.VerticalClassMergingStackSampleRetraceTest$A ->"
            + " R8$$REMOVED$$CLASS$$0:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"VerticalClassMergingStackSampleRetraceTest.java\"}",
        "com.android.tools.r8.retrace.stacksamples.VerticalClassMergingStackSampleRetraceTest$B ->"
            + " a:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"VerticalClassMergingStackSampleRetraceTest.java\"}",
        "    2:5:void <init>():42:42 -> <init>",
        "    6:6:void"
            + " com.android.tools.r8.retrace.stacksamples.VerticalClassMergingStackSampleRetraceTest$A.<init>():42:42"
            + " -> <init>",
        "      # {\"id\":\"com.android.tools.r8.residualsignature\",\"signature\":\"(I)V\"}",
        "    1:3:void bar():47:47 -> a",
        "    4:11:void bar():48:48 -> a",
        "    1:8:void"
            + " com.android.tools.r8.retrace.stacksamples.VerticalClassMergingStackSampleRetraceTest$A.bar():52:52"
            + " -> b",
        "    1:8:void"
            + " com.android.tools.r8.retrace.stacksamples.VerticalClassMergingStackSampleRetraceTest$A.baz():57:57"
            + " -> c",
        "    1:8:void"
            + " com.android.tools.r8.retrace.stacksamples.VerticalClassMergingStackSampleRetraceTest$A.foo():47:47"
            + " -> d",
        "com.android.tools.r8.retrace.stacksamples.VerticalClassMergingStackSampleRetraceTest$Main"
            + " -> b:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"VerticalClassMergingStackSampleRetraceTest.java\"}",
        "    1:4:void main(java.lang.String[]):45:48 -> main");
  }

  @Override
  String getExpectedOutput() {
    return StringUtils.lines("A.foo", "A.bar", "B.bar", "A.baz");
  }

  @Override
  void inspectCode(CodeInspector inspector) {
    // Verify that A.foo(), A.bar() and A.baz() are all present on B, and that they have been
    // renamed to d(), b() and c(), respectively.
    ClassSubject bClass = inspector.clazz(B.class);
    assertThat(bClass, isPresent());
    assertEquals(obfuscatedClassName, bClass.getFinalName());

    MethodSubject aFooMethod = bClass.uniqueMethodWithOriginalName("foo");
    assertThat(aFooMethod, isPresent());
    assertEquals(obfuscatedMethodNameAFoo, aFooMethod.getFinalName());

    MethodSubject aBarMethod =
        bClass.uniqueMethodThatMatches(
            m ->
                m.getOriginalMethodName().equals("bar")
                    && m.streamInstructions().anyMatch(x -> x.isConstString("A.bar")));
    assertThat(aBarMethod, isPresent());
    assertEquals(obfuscatedMethodNameABar, aBarMethod.getFinalName());

    MethodSubject aBazMethod = bClass.uniqueMethodWithOriginalName("baz");
    assertThat(aBazMethod, isPresent());
    assertEquals(obfuscatedMethodNameABaz, aBazMethod.getFinalName());

    MethodSubject bBarMethod =
        bClass.uniqueMethodThatMatches(
            m ->
                m.getOriginalMethodName().equals("bar")
                    && m.streamInstructions().anyMatch(x -> x.isConstString("B.bar")));
    assertThat(bBarMethod, isPresent());
    assertEquals(obfuscatedMethodNameBBar, bBarMethod.getFinalName());
  }

  @Override
  void testRetrace(R8TestCompileResultBase<?> compileResult) throws Exception {
    // Expected: `a.a` should retrace to `void B.bar()`.
    {
      RetraceMethodElement retraceResult =
          getSingleRetraceMethodElement(
              Reference.classFromTypeName(obfuscatedClassName),
              obfuscatedMethodNameBBar,
              compileResult);
      assertEquals(
          Reference.methodFromMethod(B.class.getDeclaredMethod("bar")),
          retraceResult.getRetracedMethod().asKnown().getMethodReference());
      assertFalse(retraceResult.isCompilerSynthesized());
    }

    // Expected: `a.b` should retrace to `void A.bar()`.
    {
      RetraceMethodElement retraceResult =
          getSingleRetraceMethodElement(
              Reference.classFromTypeName(obfuscatedClassName),
              obfuscatedMethodNameABar,
              compileResult);
      assertEquals(
          Reference.methodFromMethod(A.class.getDeclaredMethod("bar")),
          retraceResult.getRetracedMethod().asKnown().getMethodReference());
      assertFalse(retraceResult.isCompilerSynthesized());
    }

    // Expected: `a.c` should retrace to `void A.baz()`.
    {
      RetraceMethodElement retraceResult =
          getSingleRetraceMethodElement(
              Reference.classFromTypeName(obfuscatedClassName),
              obfuscatedMethodNameABaz,
              compileResult);
      assertEquals(
          Reference.methodFromMethod(A.class.getDeclaredMethod("baz")),
          retraceResult.getRetracedMethod().asKnown().getMethodReference());
      assertFalse(retraceResult.isCompilerSynthesized());
    }

    // Expected: `a.d` should retrace to `void A.foo()`.
    {
      RetraceMethodElement retraceResult =
          getSingleRetraceMethodElement(
              Reference.classFromTypeName(obfuscatedClassName),
              obfuscatedMethodNameAFoo,
              compileResult);
      assertEquals(
          Reference.methodFromMethod(A.class.getDeclaredMethod("foo")),
          retraceResult.getRetracedMethod().asKnown().getMethodReference());
      assertFalse(retraceResult.isCompilerSynthesized());
    }
  }

  static class Main {

    public static void main(String[] args) {
      A b = new B();
      b.foo();
      b.bar();
      A.baz();
    }
  }

  public abstract static class A {

    @NeverInline
    @NoMethodStaticizing
    public void foo() {
      System.out.println("A.foo");
    }

    @NeverInline
    public void bar() {
      System.out.println("A.bar");
    }

    @NeverInline
    public static void baz() {
      System.out.println("A.baz");
    }
  }

  @NeverClassInline
  public static class B extends A {

    @NeverInline
    @Override
    public void bar() {
      super.bar();
      System.out.println("B.bar");
    }
  }
}
