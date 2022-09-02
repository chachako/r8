// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;

/**
 * API for consuming an ART profile provided by the compiler, which has been rewritten to match the
 * residual, optimized app.
 */
@Keep
public interface ArtProfileConsumer {

  /**
   * Returns an {@link ArtProfileRuleConsumer} that will receive the rules of the residual ART
   * profile. If this returns <code>null</code> no rules will be emitted.
   */
  default ArtProfileRuleConsumer getRuleConsumer() {
    return null;
  }

  /**
   * Callback signifying that all rules of the residual ART profile have been emitted to the rule
   * consumer.
   *
   * @param handler Diagnostics handler for reporting.
   */
  void finished(DiagnosticsHandler handler);
}