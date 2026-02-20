// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;

public class AssistantExporter {

  public static AssistantOptions run(AppView<AppInfoWithClassHierarchy> appView) {
    InternalOptions options = appView.options();
    if (options.getAssistantOptions().exportFinalKeepInfoCollectionToDirectory != null) {
      try {
        appView
            .getKeepInfo()
            .exportToDirectory(
                options.getAssistantOptions().exportFinalKeepInfoCollectionToDirectory);
      } catch (IOException e) {
        options.reporter.error("Could not export final keep info collection: " + e.getMessage());
      }
    }
    if (options.getAssistantOptions().finalKeepInfoCollectionConsumer != null) {
      options
          .getAssistantOptions()
          .finalKeepInfoCollectionConsumer
          .accept(appView.getKeepInfo().exportToCollection());
    }
    return options.getAssistantOptions();
  }
}
