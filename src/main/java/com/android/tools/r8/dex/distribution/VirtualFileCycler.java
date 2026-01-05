// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.utils.IntBox;
import com.google.common.collect.Iterators;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Helper class to cycle through the set of virtual files.
 *
 * <p>Iteration starts at the first file and iterates through all files.
 *
 * <p>When {@link VirtualFileCycler#restart()} is called iteration of all files is restarted at the
 * current file.
 *
 * <p>If the fill strategy indicate that the main dex file should be minimal, then the main dex file
 * will not be part of the iteration.
 */
public class VirtualFileCycler {

  private final List<VirtualFile> files;
  private final List<VirtualFile> filesForDistribution;
  private final AppView<?> appView;

  private final IntBox nextFileId;
  private Iterator<VirtualFile> allFilesCyclic;
  private Iterator<VirtualFile> activeFiles;
  private FeatureSplit featureSplit;

  public VirtualFileCycler(
      List<VirtualFile> files,
      List<VirtualFile> filesForDistribution,
      AppView<?> appView,
      IntBox nextFileId) {
    this.files = files;
    this.filesForDistribution = new ArrayList<>(filesForDistribution);
    this.appView = appView;
    this.nextFileId = nextFileId;

    if (filesForDistribution.size() > 0) {
      featureSplit = filesForDistribution.get(0).getFeatureSplit();
    }

    reset();
  }

  public List<VirtualFile> getFilesForDistribution() {
    return filesForDistribution;
  }

  void clearFilesForDistribution() {
    filesForDistribution.clear();
    reset();
  }

  void reset() {
    allFilesCyclic = Iterators.cycle(filesForDistribution);
    restart();
  }

  boolean hasNext() {
    return activeFiles.hasNext();
  }

  public VirtualFile next() {
    return activeFiles.next();
  }

  /** Get next {@link VirtualFile} and create a new empty one if there is no next available. */
  VirtualFile nextOrCreate() {
    if (hasNext()) {
      return next();
    } else {
      VirtualFile newFile = internalAddFile();
      allFilesCyclic = Iterators.cycle(filesForDistribution);
      return newFile;
    }
  }

  /**
   * Get next {@link VirtualFile} accepted by the given filter and create a new empty one if there
   * is no next available.
   *
   * @param filter allows to to reject some of the available {@link VirtualFile}. Rejecting empt
   *     {@link VirtualFile} is not authorized since it would sometimes prevent to find a result.
   */
  VirtualFile nextOrCreate(Predicate<? super VirtualFile> filter) {
    while (true) {
      VirtualFile dex = nextOrCreate();
      if (dex.isEmpty()) {
        assert filter.test(dex);
        return dex;
      } else if (filter.test(dex)) {
        return dex;
      }
    }
  }

  // Start a new iteration over all files, starting at the current one.
  public void restart() {
    activeFiles = Iterators.limit(allFilesCyclic, filesForDistribution.size());
  }

  public VirtualFile addFile() {
    VirtualFile newFile = internalAddFile();
    reset();
    return newFile;
  }

  void addFileForDistribution(VirtualFile file) {
    filesForDistribution.add(file);
    reset();
  }

  private VirtualFile internalAddFile() {
    VirtualFile newFile = new VirtualFile(nextFileId.getAndIncrement(), appView, featureSplit);
    files.add(newFile);
    filesForDistribution.add(newFile);
    return newFile;
  }

  VirtualFileCycler ensureFile() {
    if (filesForDistribution.isEmpty()) {
      addFile();
    }
    return this;
  }
}
