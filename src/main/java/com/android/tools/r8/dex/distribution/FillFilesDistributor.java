// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class FillFilesDistributor extends DistributorBase {

  private final ExecutorService executorService;
  private final StartupProfile startupProfile;

  public FillFilesDistributor(
      ApplicationWriter writer,
      Collection<DexProgramClass> classes,
      InternalOptions options,
      ExecutorService executorService,
      StartupProfile startupProfile) {
    super(writer, classes, options, startupProfile);
    this.executorService = executorService;
    this.startupProfile = startupProfile;
  }

  @Override
  public List<VirtualFile> run(Timing timing) throws ExecutionException {
    assert virtualFiles.size() == 1;
    assert virtualFiles.get(0).isEmpty();

    int totalClassNumber = classes.size();
    // First fill required classes into the main dex file.
    fillForMainDexList(classes);
    if (classes.isEmpty()) {
      // All classes ended up in the main dex file, no more to do.
      return virtualFiles;
    }

    List<VirtualFile> filesForDistribution = virtualFiles;
    boolean multidexLegacy = !mainDexFile.isEmpty();
    if (options.minimalMainDex && multidexLegacy) {
      assert virtualFiles.size() == 1;
      assert !virtualFiles.get(0).isEmpty();
      // Don't consider the main dex for distribution.
      filesForDistribution = Collections.emptyList();
    }

    Map<FeatureSplit, Set<DexProgramClass>> featureSplitClasses =
        removeFeatureSplitClassesGetMapping();

    IntBox nextFileId = new IntBox(1);
    if (multidexLegacy && options.enableInheritanceClassInDexDistributor) {
      new InheritanceClassInDexDistributor(
              mainDexFile,
              virtualFiles,
              filesForDistribution,
              classes,
              nextFileId,
              appView,
              executorService)
          .distribute();
    } else {
      new PackageSplitPopulator(
              virtualFiles,
              filesForDistribution,
              appView,
              classes,
              originalNames,
              startupProfile,
              nextFileId)
          .run(executorService, timing);
    }
    addFeatureSplitFiles(featureSplitClasses, startupProfile, executorService, timing);

    assert totalClassNumber == virtualFiles.stream().mapToInt(dex -> dex.classes().size()).sum();
    return virtualFiles;
  }
}
