// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Distribute each type to its individual virtual except for types synthesized during this
 * compilation. Synthesized classes are emitted in the individual virtual files of the input classes
 * they were generated from. Shared synthetic classes may then be distributed in several individual
 * virtual files.
 */
public class FilePerInputClassDistributor extends Distributor {
  private final Collection<DexProgramClass> classes;
  private final boolean combineSyntheticClassesWithPrimaryClass;

  public FilePerInputClassDistributor(
      ApplicationWriter writer,
      Collection<DexProgramClass> classes,
      boolean combineSyntheticClassesWithPrimaryClass) {
    super(writer);
    this.classes = classes;
    this.combineSyntheticClassesWithPrimaryClass = combineSyntheticClassesWithPrimaryClass;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public List<VirtualFile> run(Timing timing) {
    Map<DexType, VirtualFile> files = new IdentityHashMap<>();
    Map<DexType, List<DexProgramClass>> derivedSynthetics = new LinkedHashMap<>();
    // Assign dedicated virtual files for all program classes.
    for (DexProgramClass clazz : classes) {
      if (combineSyntheticClassesWithPrimaryClass) {
        DexType inputContextType =
            appView
                .getSyntheticItems()
                .getSynthesizingInputContext(clazz.getType(), appView.options());
        if (inputContextType != null && inputContextType != clazz.getType()) {
          derivedSynthetics.computeIfAbsent(inputContextType, k -> new ArrayList<>()).add(clazz);
          continue;
        }
      }
      VirtualFile file = new VirtualFile(virtualFiles.size(), appView, clazz);
      virtualFiles.add(file);
      addMarkers(file);
      file.addClass(clazz);
      files.put(clazz.getType(), file);
      // Commit this early, so that we do not keep the transaction state around longer than
      // needed and clear the underlying sets.
      file.commitTransaction();
    }
    derivedSynthetics.forEach(
        (inputContextType, synthetics) -> {
          VirtualFile file = files.get(inputContextType);
          for (DexProgramClass synthetic : synthetics) {
            file.addClass(synthetic);
            file.commitTransaction();
          }
        });
    return virtualFiles;
  }
}
