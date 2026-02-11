// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.utils.SystemPropertyUtils;

public class BlastRadiusOptions {

  public final String outputDirectory =
      System.getProperty("com.android.tools.r8.dumpblastradiustodirectory");
  public String outputPath = System.getProperty("com.android.tools.r8.dumpblastradiustofile");
  public final boolean enableSubsumptionAnalysis =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.blastradius.enablesubsumptionanalysis", true);
}
