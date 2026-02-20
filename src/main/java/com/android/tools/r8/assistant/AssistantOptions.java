// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import static com.android.tools.r8.utils.SystemPropertyUtils.parsePathFromSystemProperty;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;
import com.android.tools.r8.utils.InternalOptions;
import java.nio.file.Path;
import java.util.function.Consumer;

public class AssistantOptions {

  private final InternalOptions options;

  public AssistantOptions(InternalOptions options) {
    this.options = options;
  }

  public Path exportFinalKeepInfoCollectionToDirectory =
      parsePathFromSystemProperty("com.android.tools.r8.assistant.exportFinalKeepInfoCollection");
  public Consumer<KeepInfoCollectionExported> finalKeepInfoCollectionConsumer = null;

  public boolean shouldExitEarly() {
    // TODO(b/486089172): Consider making this an option instead.
    return (exportFinalKeepInfoCollectionToDirectory != null
            || finalKeepInfoCollectionConsumer != null)
        && options.programConsumer == DexIndexedConsumer.emptyConsumer();
  }
}
