// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility;

import com.android.tools.r8.graph.AppView;

public class Reporter {

  public static void reportInfo(AppView<?> appView, Event event, Reason reason) {
    if (!appView.testing().enableAtomicFieldUpdaterLogs) {
      return;
    }
    appView.reporter().info(format(event, reason));
  }

  public static void reportInfo(AppView<?> appView, Event event) {
    reportInfo(appView, event, Reason.NO_REASON);
  }

  private static String format(Event event, Reason reason) {
    String prefix;
    if (event.isFailure()) {
      prefix = "X ";
    } else {
      prefix = "  ";
    }
    String reasonPart;
    if (reason == Reason.NO_REASON) {
      reasonPart = "";
    } else {
      reasonPart = ": " + reason;
    }
    return prefix + event + reasonPart;
  }
}
