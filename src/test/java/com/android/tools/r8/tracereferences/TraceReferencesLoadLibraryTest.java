// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.MethodOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesLoadLibraryTest extends TestBase {
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public static Path input1Jar;
  public static Origin input1MethodOrigin;
  public static Path input2Jar;
  public static Origin input2Method1Origin;
  public static Origin input2Method2Origin;
  public static Path loadUnknown1Jar;
  public static Origin loadUnknown1MethodOrigin;
  public static Path loadUnknown2Jar;
  public static Origin loadUnknown2Method1Origin;
  public static Origin loadUnknown2Method2Origin;

  @BeforeClass
  public static void createTestJars() throws Exception {
    Box<Origin> jarOrigin = new Box<>();
    Path dir = getStaticTemp().newFolder().toPath();
    createJarAndOrigin(dir, Class1.class, "input1.jar", path -> input1Jar = path, jarOrigin::set);
    input1MethodOrigin =
        new MethodOrigin(Reference.methodFromMethod(Class1.class.getMethod("m")), jarOrigin.get());
    createJarAndOrigin(dir, Class2.class, "input2.jar", path -> input2Jar = path, jarOrigin::set);
    input2Method1Origin =
        new MethodOrigin(Reference.methodFromMethod(Class2.class.getMethod("m1")), jarOrigin.get());
    input2Method2Origin =
        new MethodOrigin(Reference.methodFromMethod(Class2.class.getMethod("m2")), jarOrigin.get());
    createJarAndOrigin(
        dir,
        ClassLoadUnknownLibrary1.class,
        "loadunknown1.jar",
        path -> loadUnknown1Jar = path,
        jarOrigin::set);
    loadUnknown1MethodOrigin =
        new MethodOrigin(
            Reference.methodFromMethod(ClassLoadUnknownLibrary1.class.getMethod("m", String.class)),
            jarOrigin.get());
    createJarAndOrigin(
        dir,
        ClassLoadUnknownLibrary2.class,
        "loadunknown2.jar",
        path -> loadUnknown2Jar = path,
        jarOrigin::set);
    loadUnknown2Method1Origin =
        new MethodOrigin(
            Reference.methodFromMethod(
                ClassLoadUnknownLibrary2.class.getMethod("m1", String.class)),
            jarOrigin.get());
    loadUnknown2Method2Origin =
        new MethodOrigin(
            Reference.methodFromMethod(
                ClassLoadUnknownLibrary2.class.getMethod("m2", String.class)),
            jarOrigin.get());
  }

  private static void createJarAndOrigin(
      Path dir,
      Class<?> clazz,
      String filename,
      Consumer<Path> pathConsumer,
      Consumer<Origin> originConsumer)
      throws Exception {
    Path jarPath = dir.resolve(filename);
    Path classFile = ToolHelper.getClassFileForTestClass(clazz);
    ZipBuilder.builder(jarPath)
        .addFilesRelative(ToolHelper.getClassPathForTests(), classFile)
        .build();
    Origin origin =
        new ArchiveEntryOrigin(
            ZipUtils.zipEntryFromPath(ToolHelper.getClassPathForTests().relativize(classFile)),
            new PathOrigin(jarPath));
    pathConsumer.accept(jarPath);
    originConsumer.accept(origin);
  }

  public TraceReferencesLoadLibraryTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testSingleLoadLibrary() throws Throwable {
    NativeReferencesTestingConsumer NativeReferencesTestingConsumer =
        new NativeReferencesTestingConsumer();
    testForTraceReferences()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addSourceFiles(input1Jar)
        .setConsumer(TraceReferencesConsumer.emptyConsumer())
        .setNativeReferencesConsumer(NativeReferencesTestingConsumer)
        .trace();

    NativeReferencesTestingConsumer.expectLoadLibrary("library1", input1MethodOrigin).thatsAll();
  }

  @Test
  public void testMultipleLoadLibrary() throws Throwable {
    NativeReferencesTestingConsumer NativeReferencesTestingConsumer =
        new NativeReferencesTestingConsumer();
    testForTraceReferences()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addSourceFiles(input2Jar)
        .setConsumer(TraceReferencesConsumer.emptyConsumer())
        .setNativeReferencesConsumer(NativeReferencesTestingConsumer)
        .trace();

    NativeReferencesTestingConsumer.expectLoadLibrary("library1", input2Method1Origin)
        .expectLoadLibrary("library2", input2Method2Origin)
        .thatsAll();
  }

  @Test
  public void testMultipleLoadLibraryDuplicate() throws Throwable {
    NativeReferencesTestingConsumer NativeReferencesTestingConsumer =
        new NativeReferencesTestingConsumer();
    testForTraceReferences()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addSourceFiles(input1Jar)
        .addSourceFiles(input2Jar)
        .setConsumer(TraceReferencesConsumer.emptyConsumer())
        .setNativeReferencesConsumer(NativeReferencesTestingConsumer)
        .trace();

    NativeReferencesTestingConsumer.expectLoadLibrary("library1", input1MethodOrigin)
        .expectLoadLibrary("library1", input2Method1Origin)
        .expectLoadLibrary("library2", input2Method2Origin)
        .thatsAll();
  }

  @Test
  public void testUnknownLoadLibraryCall() throws Throwable {
    NativeReferencesTestingConsumer NativeReferencesTestingConsumer =
        new NativeReferencesTestingConsumer();
    testForTraceReferences()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addSourceFiles(loadUnknown1Jar)
        .setConsumer(TraceReferencesConsumer.emptyConsumer())
        .setNativeReferencesConsumer(NativeReferencesTestingConsumer)
        .trace();

    NativeReferencesTestingConsumer.expectLoadLibraryAny(loadUnknown1MethodOrigin).thatsAll();
  }

  @Test
  public void testUnknownLoadLibraryCallMultiple() throws Throwable {
    NativeReferencesTestingConsumer NativeReferencesTestingConsumer =
        new NativeReferencesTestingConsumer();
    testForTraceReferences()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addSourceFiles(loadUnknown2Jar)
        .setConsumer(TraceReferencesConsumer.emptyConsumer())
        .setNativeReferencesConsumer(NativeReferencesTestingConsumer)
        .trace();

    NativeReferencesTestingConsumer.expectLoadLibraryAny(loadUnknown2Method1Origin)
        .expectLoadLibraryAny(loadUnknown2Method2Origin)
        .thatsAll();
  }

  @Test
  public void testUnknownLoadLibraryAll() throws Throwable {
    NativeReferencesTestingConsumer NativeReferencesTestingConsumer =
        new NativeReferencesTestingConsumer();
    testForTraceReferences()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addSourceFiles(input1Jar)
        .addSourceFiles(input2Jar)
        .addSourceFiles(loadUnknown1Jar)
        .addSourceFiles(loadUnknown2Jar)
        .setConsumer(TraceReferencesConsumer.emptyConsumer())
        .setNativeReferencesConsumer(NativeReferencesTestingConsumer)
        .trace();

    NativeReferencesTestingConsumer.expectLoadLibrary("library1", input1MethodOrigin)
        .expectLoadLibrary("library1", input2Method1Origin)
        .expectLoadLibrary("library2", input2Method2Origin)
        .expectLoadLibraryAny(loadUnknown1MethodOrigin)
        .expectLoadLibraryAny(loadUnknown2Method1Origin)
        .expectLoadLibraryAny(loadUnknown2Method2Origin)
        .thatsAll();
  }

  static class Class1 {
    public static void m() {
      System.loadLibrary("library1");
    }
  }

  static class Class2 {
    public static void m1() {
      System.loadLibrary("library1");
    }

    public static void m2() {
      System.loadLibrary("library2");
    }
  }

  static class ClassLoadUnknownLibrary1 {
    public static void m(String s) {
      System.loadLibrary(s);
    }
  }

  static class ClassLoadUnknownLibrary2 {
    public static void m1(String s) {
      System.loadLibrary(s);
    }

    public static void m2(String s) {
      System.loadLibrary(s);
    }
  }
}
