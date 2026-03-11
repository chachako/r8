// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexSget;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory.AndroidOsBuildVersionMembers;
import com.android.tools.r8.graph.ProgramMethod;

public class DexToDexCodeOptimizations {

  public static boolean shouldOptimize(AppView<?> appView, ProgramMethod method) {
    return shouldOptimize(appView, method.getDefinition().getCode().asDexCode());
  }

  public static boolean shouldOptimize(AppView<?> appView, DexCode code) {
    if (!appView.options().enableDexToDexCodeOptimizations) {
      return false;
    }
    AndroidOsBuildVersionMembers androidOsBuildVersionMembers =
        appView.dexItemFactory().androidOsBuildVersionMembers;
    for (DexInstruction instruction : code.getInstructions()) {
      if (instruction instanceof DexSget) {
        DexSget sget = (DexSget) instruction;
        DexField field = sget.getField();
        if (field.isIdenticalTo(androidOsBuildVersionMembers.SDK_INT)) {
          return true;
        }
      }
    }
    return false;
  }
}
