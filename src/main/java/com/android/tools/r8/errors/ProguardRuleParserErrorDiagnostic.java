// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class ProguardRuleParserErrorDiagnostic implements Diagnostic {

  private final String message;
  private final String snippet;
  private final Origin origin;
  private final Position position;

  ProguardRuleParserErrorDiagnostic(
      String message, String snippet, Origin origin, Position position) {
    this.message = message;
    this.snippet = snippet;
    this.origin = origin;
    this.position = position;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return message + " at " + snippet;
  }

  // To not include ProguardRuleParserErrorDiagnostic.<init> in the public API.
  public static class Factory {

    public static ProguardRuleParserErrorDiagnostic create(
        String message, String snippet, Origin origin, Position position) {
      return new ProguardRuleParserErrorDiagnostic(message, snippet, origin, position);
    }
  }
}
