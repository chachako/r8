// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class DistributorBase extends Distributor {
  protected Set<DexProgramClass> classes;
  protected Map<DexProgramClass, String> originalNames;
  protected final VirtualFile mainDexFile;
  protected final InternalOptions options;

  DistributorBase(
      ApplicationWriter writer,
      Collection<DexProgramClass> classes,
      InternalOptions options,
      StartupProfile startupProfile) {
    super(writer);
    this.options = options;
    this.classes = SetUtils.newIdentityHashSet(classes);

    // Create the primary dex file. The distribution will add more if needed. We use the startup
    // order (if any) to guide the layout of the primary dex file.
    mainDexFile = new VirtualFile(0, appView, null, null, startupProfile);
    assert virtualFiles.isEmpty();
    virtualFiles.add(mainDexFile);
    addMarkers(mainDexFile);

    originalNames =
        computeOriginalNameMapping(
            classes, appView.graphLens(), appView.appInfo().app().getProguardMap());
  }

  private static Map<DexProgramClass, String> computeOriginalNameMapping(
      Collection<DexProgramClass> classes, GraphLens graphLens, ClassNameMapper proguardMap) {
    Map<DexProgramClass, String> originalNames = new IdentityHashMap<>(classes.size());
    classes.forEach(
        clazz -> {
          DexType originalType = graphLens.getOriginalType(clazz.getType());
          originalNames.put(
              clazz,
              DescriptorUtils.descriptorToJavaType(originalType.toDescriptorString(), proguardMap));
        });
    return originalNames;
  }

  protected void fillForMainDexList(Set<DexProgramClass> classes) {
    MainDexInfo mainDexInfo = appView.appInfo().getMainDexInfo();
    if (mainDexInfo.isEmpty()) {
      return;
    }
    VirtualFile mainDexFile = virtualFiles.get(0);
    mainDexInfo.forEach(
        type -> {
          DexProgramClass clazz = asProgramClassOrNull(appView.appInfo().definitionFor(type));
          if (clazz != null) {
            mainDexFile.addClass(clazz);
            classes.remove(clazz);
          }
          mainDexFile.commitTransaction();
        });
    mainDexFile.throwIfFull(true, options.reporter);
  }

  protected Map<FeatureSplit, Set<DexProgramClass>> removeFeatureSplitClassesGetMapping() {
    assert appView.appInfo().hasClassHierarchy() == appView.enableWholeProgramOptimizations();
    if (!appView.appInfo().hasClassHierarchy()) {
      return ImmutableMap.of();
    }

    AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy =
        appView.withClassHierarchy();
    ClassToFeatureSplitMap classToFeatureSplitMap =
        appViewWithClassHierarchy.appInfo().getClassToFeatureSplitMap();
    if (classToFeatureSplitMap.isEmpty()) {
      return ImmutableMap.of();
    }

    // Pull out the classes that should go into feature splits.
    Map<FeatureSplit, Set<DexProgramClass>> featureSplitClasses =
        classToFeatureSplitMap.getFeatureSplitClasses(classes, appViewWithClassHierarchy);
    if (featureSplitClasses.size() > 0) {
      for (Set<DexProgramClass> featureClasses : featureSplitClasses.values()) {
        classes.removeAll(featureClasses);
      }
    }
    return featureSplitClasses;
  }

  protected void addFeatureSplitFiles(
      Map<FeatureSplit, Set<DexProgramClass>> featureSplitClasses,
      StartupProfile startupProfile,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    if (featureSplitClasses.isEmpty()) {
      return;
    }
    for (Map.Entry<FeatureSplit, Set<DexProgramClass>> featureSplitSetEntry :
        featureSplitClasses.entrySet()) {
      // Add a new virtual file, start from index 0 again
      IntBox nextFileId = new IntBox();
      VirtualFile featureFile =
          new VirtualFile(nextFileId.getAndIncrement(), appView, featureSplitSetEntry.getKey());
      virtualFiles.add(featureFile);
      addMarkers(featureFile);
      List<VirtualFile> files = virtualFiles;
      List<VirtualFile> filesForDistribution = ImmutableList.of(featureFile);
      new PackageSplitPopulator(
              files,
              filesForDistribution,
              appView,
              featureSplitSetEntry.getValue(),
              originalNames,
              startupProfile,
              nextFileId)
          .run(executorService, timing);
    }
  }
}
