// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class MonoDexDistributor extends DistributorBase {

  private final ExecutorService executorService;

  public MonoDexDistributor(
      ApplicationWriter writer,
      Collection<DexProgramClass> classes,
      InternalOptions options,
      ExecutorService executorService) {
    super(writer, classes, options, StartupProfile.empty());
    this.executorService = executorService;
  }

  @Override
  public List<VirtualFile> run(Timing timing) throws ExecutionException {
    Map<FeatureSplit, Set<DexProgramClass>> featureSplitClasses =
        removeFeatureSplitClassesGetMapping();
    // Add all classes to the main dex file.
    for (DexProgramClass programClass : classes) {
      mainDexFile.addClass(programClass);
    }
    mainDexFile.commitTransaction();
    mainDexFile.throwIfFull(false, options.reporter);
    if (options.hasFeatureSplitConfiguration()) {
      if (!featureSplitClasses.isEmpty()) {
        // TODO(141334414): Figure out if we allow multidex in features even when mono-dexing
        addFeatureSplitFiles(featureSplitClasses, StartupProfile.empty(), executorService, timing);
      }
    }
    return virtualFiles;
  }
}
