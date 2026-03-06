// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static com.android.tools.r8.utils.DescriptorUtils.javaTypeToDescriptor;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.ByteArrayConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.libanalyzer.proto.LibraryAnalyzerResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public class LibraryAnalyzerTestBuilder {

  public enum AarOrJar {
    AAR,
    JAR
  }

  private final LibraryAnalyzerCommand.Builder commandBuilder;
  private final TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
  private final TemporaryFolder temp;

  private AarOrJar aarOrJar;
  private AndroidApp.Builder androidAppBuilder = AndroidApp.builder();
  private List<String> keepRules = new ArrayList<>();

  private LibraryAnalyzerTestBuilder(TemporaryFolder temp) {
    this.commandBuilder = LibraryAnalyzerCommand.builder(diagnostics);
    this.temp = temp;
  }

  public static LibraryAnalyzerTestBuilder create(TemporaryFolder temp) {
    return new LibraryAnalyzerTestBuilder(temp);
  }

  public LibraryAnalyzerTestBuilder addDefaultLibrary() {
    commandBuilder.addLibraryPath(ToolHelper.getMostRecentAndroidJar());
    return this;
  }

  public LibraryAnalyzerTestBuilder addKeepRules(String... keepRules) {
    return addKeepRules(Arrays.asList(keepRules));
  }

  public LibraryAnalyzerTestBuilder addKeepRules(Collection<String> keepRules) {
    this.keepRules.addAll(keepRules);
    return this;
  }

  public LibraryAnalyzerTestBuilder addProgramClasses(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      androidAppBuilder.addProgramFile(
          ToolHelper.getClassFileForTestClass(clazz),
          Collections.singleton(javaTypeToDescriptor(clazz.getTypeName())));
    }
    return this;
  }

  public LibraryAnalyzerTestBuilder setAar() {
    return setAarOrJar(AarOrJar.AAR);
  }

  public LibraryAnalyzerTestBuilder setAarOrJar(AarOrJar aarOrJar) {
    assertNull(this.aarOrJar);
    this.aarOrJar = aarOrJar;
    return this;
  }

  public LibraryAnalyzerTestBuilder setJar() {
    return setAarOrJar(AarOrJar.JAR);
  }

  public LibraryAnalyzerTestBuilder setMinApi(AndroidApiLevel minApi) {
    commandBuilder.setMinApiLevel(minApi.getLevel(), minApi.getMinor());
    return this;
  }

  public LibraryAnalyzerTestBuilder setOutputConsumer(ByteArrayConsumer<?> outputConsumer) {
    commandBuilder.setOutputConsumer(outputConsumer);
    return this;
  }

  public LibraryAnalyzerCompileResult compile() throws CompilationFailedException {
    assertNotNull("Must call setAar() or setJar() to specify input type.", aarOrJar);
    Box<LibraryAnalyzerResult> LibraryAnalyzerResult = new Box<>();
    if (aarOrJar == AarOrJar.AAR) {
      commandBuilder.addAarPath(createAar());
    } else {
      commandBuilder.addJarPath(createJar());
    }
    LibraryAnalyzerCommand command =
        commandBuilder.setInternalOutputConsumer(LibraryAnalyzerResult::set).build();
    LibraryAnalyzer.run(command);
    diagnostics.assertNoMessages();
    return new LibraryAnalyzerCompileResult(LibraryAnalyzerResult.get());
  }

  private Path createAar() {
    try {
      Path aarDir = temp.newFolder("aar").toPath();
      Path classesJarPath = aarDir.resolve("classes.jar");
      androidAppBuilder.build().writeToZipForTesting(classesJarPath, OutputMode.ClassFile);
      androidAppBuilder = null;

      if (!keepRules.isEmpty()) {
        Path proguardTxtPath = aarDir.resolve("proguard.txt");
        Files.write(proguardTxtPath, keepRules, StandardCharsets.UTF_8);
        keepRules = null;
      }

      Path aarPath = temp.newFile("lib.aar").toPath();
      ZipUtils.zip(aarPath, aarDir);
      return aarPath;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Path createJar() {
    try {
      Path jarDir = temp.newFolder("jar").toPath();
      androidAppBuilder.build().writeToDirectory(jarDir, OutputMode.ClassFile);
      androidAppBuilder = null;

      if (!keepRules.isEmpty()) {
        Path libProPath = jarDir.resolve("META-INF/proguard/lib.pro");
        Files.createDirectories(libProPath.getParent());
        Files.write(libProPath, keepRules, StandardCharsets.UTF_8);
        keepRules = null;
      }

      Path jarPath = temp.newFile("lib.jar").toPath();
      ZipUtils.zip(jarPath, jarDir);
      return jarPath;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
