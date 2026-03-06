// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.libanalyzer.proto.LibraryAnalyzerResult;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.util.function.Consumer;

public class LibraryAnalyzerCompileResult {

  private final LibraryAnalyzerResult LibraryAnalyzerResult;

  public LibraryAnalyzerCompileResult(LibraryAnalyzerResult LibraryAnalyzerResult) {
    this.LibraryAnalyzerResult = LibraryAnalyzerResult;
  }

  public LibraryAnalyzerCompileResult apply(Consumer<LibraryAnalyzerCompileResult> consumer) {
    consumer.accept(this);
    return this;
  }

  public <E extends Exception> LibraryAnalyzerCompileResult inspectD8CompileResult(
      ThrowingConsumer<D8CompileResultInspector, E> inspector) {
    inspector.acceptWithRuntimeException(
        new D8CompileResultInspector(LibraryAnalyzerResult.getD8CompileResult()));
    return this;
  }

  public <E extends Exception> LibraryAnalyzerCompileResult inspectR8CompileResult(
      ThrowingConsumer<R8CompileResultInspector, E> inspector) {
    inspector.acceptWithRuntimeException(
        new R8CompileResultInspector(LibraryAnalyzerResult.getR8CompileResult()));
    return this;
  }

  public <E extends Exception> LibraryAnalyzerCompileResult inspectValidateConsumerKeepRulesResult(
      ThrowingConsumer<ValidateConsumerKeepRulesResultInspector, E> inspector) {
    inspector.acceptWithRuntimeException(
        new ValidateConsumerKeepRulesResultInspector(
            LibraryAnalyzerResult.getValidateConsumerKeepRulesResult()));
    return this;
  }
}
