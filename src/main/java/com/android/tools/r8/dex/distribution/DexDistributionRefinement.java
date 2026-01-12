// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class DexDistributionRefinement {

  private final AppView<?> appView;
  private final VirtualFileCycler cycler;
  private final boolean enableContainerDex;
  private final LinkedHashSet<VirtualFile> files;
  private final int numPasses;
  private final LensCodeRewriterUtils rewriter;

  // Must be concurrent since we collect concurrently.
  private final Map<String, DexString> shortyCache = new ConcurrentHashMap<>();

  // Mapping from each virtual file to the classes inside the virtual file that are candidates for
  // moving to other virtual files. A LinkedHashSet is used instead of a List to support efficient
  // removal.
  private final Map<VirtualFile, LinkedHashSet<DexProgramClass>>
      fileToClassesWithDeterministicOrder = new IdentityHashMap<>();

  private DexDistributionRefinement(
      AppView<?> appView,
      VirtualFileCycler cycler,
      List<VirtualFile> filesSubjectToRefinement,
      int numPasses) {
    this.appView = appView;
    this.cycler = cycler;
    this.enableContainerDex = appView.options().enableContainerDex();
    this.files = new LinkedHashSet<>(filesSubjectToRefinement);
    this.numPasses = numPasses;
    this.rewriter = new LensCodeRewriterUtils(appView, true);
    initialize();
  }

  public static void run(
      AppView<?> appView, VirtualFileCycler cycler, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    int numPasses = appView.testing().classToDexDistributionRefinementPasses;
    if (numPasses > 0) {
      List<VirtualFile> filesSubjectToRefinement =
          ListUtils.filter(cycler.getFilesForDistribution(), f -> !f.isEmpty() && !f.isStartup());
      if (filesSubjectToRefinement.size() > 1) {
        timing.begin("Dex distribution refinement");
        new DexDistributionRefinement(appView, cycler, filesSubjectToRefinement, numPasses)
            .internalRun(executorService, timing);
        timing.end();
      }
    }
  }

  private void initialize() {
    for (VirtualFile file : files) {
      fileToClassesWithDeterministicOrder.put(
          file,
          new LinkedHashSet<>(
              ListUtils.sort(file.classes(), Comparator.comparing(DexClass::getType))));
    }
  }

  private void internalRun(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    // Run refinement.
    boolean hasEmptyFiles = false;
    for (int i = 0; i < numPasses; i++) {
      boolean changed = false;
      timing.begin("Pass " + i);
      Iterator<VirtualFile> iterator = files.iterator();
      while (iterator.hasNext()) {
        VirtualFile file = iterator.next();
        if (refineFile(file, executorService, timing)) {
          changed = true;
        }
        // If the file became empty, then don't consider it for any further refinement.
        if (file.getIndexedItems().classes.isEmpty()) {
          iterator.remove();
          hasEmptyFiles = true;
        }
      }
      timing.end();
      if (!changed) {
        break;
      }
    }

    // Fixup empty files.
    if (hasEmptyFiles) {
      cycler.removeEmptyFilesAndRenumber();
    }
  }

  private boolean refineFile(VirtualFile file, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("Refine file " + file.getId());
    boolean changed = false;
    LinkedHashSet<DexProgramClass> classesWithDeterministicOrder =
        fileToClassesWithDeterministicOrder.get(file);

    // Concurrently compute which classes to move where.
    timing.begin("Compute target files");
    Map<DexProgramClass, PendingMoveTask> pendingMoveTasks = new ConcurrentHashMap<>();
    ThreadUtils.processItemsThatMatches(
        classesWithDeterministicOrder,
        alwaysTrue(),
        (clazz, threadTiming) -> {
          Set<DexItem> items = collectItems(clazz);
          PriorityQueue<Pair<VirtualFile, Integer>> targetFiles = findTargetFiles(file, items);
          if (!targetFiles.isEmpty()) {
            pendingMoveTasks.put(clazz, c -> moveClass(c, file, targetFiles, items));
          }
        },
        appView.options(),
        executorService,
        timing,
        timing.beginMerger("Fork", executorService),
        (index, clazz) -> clazz.getTypeName());
    timing.end();

    // Move all classes single threaded.
    timing.begin("Move classes to target files");
    Iterator<DexProgramClass> iterator = classesWithDeterministicOrder.iterator();
    while (iterator.hasNext()) {
      DexProgramClass clazz = iterator.next();
      PendingMoveTask pendingMoveTask = pendingMoveTasks.get(clazz);
      if (pendingMoveTask == null) {
        continue;
      }

      boolean moved = pendingMoveTask.tryMove(clazz);
      if (moved) {
        changed = true;

        // The current virtual file no longer contains the class, so remove it.
        // We concluded that moving the current class C from DEX file X to Y was a good idea.
        // Let's not consider moving class C from Y to another DEX file in the future, which is
        // achieved by not adding the class to the target's set of classes.
        iterator.remove();
      }
    }
    timing.end(); // Move classes to target files
    timing.end(); // Refine file
    return changed;
  }

  private Set<DexItem> collectItems(DexProgramClass clazz) {
    ItemCollector collector = new ItemCollector();
    clazz.collectIndexedItems(appView, collector, rewriter);
    return collector.getItems();
  }

  private PriorityQueue<Pair<VirtualFile, Integer>> findTargetFiles(
      VirtualFile sourceFile, Set<DexItem> items) {
    int estimatedSavingsFromRemovalInBytes =
        getNumberOfItemsWithReferenceCount(items, sourceFile, 1);

    // TODO(b/473427453): To improve build speed, consider if we can return null here if if the
    //  estimated savings are small compared to the number of items in the class (i.e., the class
    //  already fits well in the current dex file).

    PriorityQueue<Pair<VirtualFile, Integer>> targetFiles =
        new PriorityQueue<>(Comparator.comparingInt(Pair::getSecond));
    for (VirtualFile targetFile : files) {
      if (targetFile == sourceFile || cannotFit(targetFile, items)) {
        continue;
      }
      int estimatedCostInBytes = getNumberOfItemsWithReferenceCount(items, targetFile, 0);
      if (estimatedCostInBytes < estimatedSavingsFromRemovalInBytes) {
        targetFiles.add(new Pair<>(targetFile, estimatedCostInBytes));
      }
    }
    return targetFiles;
  }

  private int getNumberOfItemsWithReferenceCount(
      Set<DexItem> items, VirtualFile targetFile, int theReferenceCount) {
    int result = 0;
    for (DexItem item : items) {
      if (enableContainerDex && item instanceof DexString) {
        // The container has a single DEX file, so don't include the cost/savings from moving
        // strings from one dex file to another.
        continue;
      }
      if (getReferenceCount(item, targetFile) == theReferenceCount) {
        result++;
      }
    }
    return result;
  }

  private int getReferenceCount(DexItem item, VirtualFile file) {
    if (item instanceof DexCallSite) {
      return file.indexedItems.callSites.getInt(item);
    } else if (item instanceof DexField) {
      return file.indexedItems.fields.getInt(item);
    } else if (item instanceof DexMethod) {
      return file.indexedItems.methods.getInt(item);
    } else if (item instanceof DexMethodHandle) {
      return file.indexedItems.methodHandles.getInt(item);
    } else if (item instanceof DexProto) {
      return file.indexedItems.protos.getInt(item);
    } else if (item instanceof DexString) {
      return file.indexedItems.strings.getInt(item);
    } else if (item instanceof DexType) {
      return file.indexedItems.types.getInt(item);
    }
    assert false;
    return 0;
  }

  private boolean cannotFit(VirtualFile file, Set<DexItem> items) {
    int newFields = 0;
    int newMethods = 0;
    int newTypes = 0;
    for (DexItem item : items) {
      if (getReferenceCount(item, file) == 0) {
        if (item instanceof DexField) {
          newFields++;
        } else if (item instanceof DexMethod) {
          newMethods++;
        } else if (item instanceof DexType) {
          newTypes++;
        }
      }
    }
    return file.getTransaction().getNumberOfFields() + newFields > VirtualFile.MAX_ENTRIES
        || file.getTransaction().getNumberOfMethods() + newMethods > VirtualFile.MAX_ENTRIES
        || file.getTransaction().getNumberOfTypes() + newTypes > VirtualFile.MAX_ENTRIES;
  }

  private boolean moveClass(
      DexProgramClass clazz,
      VirtualFile sourceFile,
      PriorityQueue<Pair<VirtualFile, Integer>> targetFiles,
      Set<DexItem> items) {
    while (!targetFiles.isEmpty()) {
      VirtualFile targetFile = targetFiles.poll().getFirst();
      if (cannotFit(targetFile, items)) {
        continue;
      }
      sourceFile.indexedItems.classes.remove(clazz);
      targetFile.indexedItems.classes.add(clazz);
      for (DexItem item : items) {
        adjustReferenceCount(sourceFile, item, -1);
        adjustReferenceCount(targetFile, item, 1);
      }
      return true;
    }
    return false;
  }

  private void adjustReferenceCount(VirtualFile file, DexItem item, int change) {
    if (item instanceof DexCallSite) {
      adjustReferenceCount(file.indexedItems.callSites, (DexCallSite) item, change);
    } else if (item instanceof DexField) {
      adjustReferenceCount(file.indexedItems.fields, (DexField) item, change);
    } else if (item instanceof DexMethod) {
      adjustReferenceCount(file.indexedItems.methods, (DexMethod) item, change);
    } else if (item instanceof DexMethodHandle) {
      adjustReferenceCount(file.indexedItems.methodHandles, (DexMethodHandle) item, change);
    } else if (item instanceof DexProto) {
      adjustReferenceCount(file.indexedItems.protos, (DexProto) item, change);
    } else if (item instanceof DexString) {
      adjustReferenceCount(file.indexedItems.strings, (DexString) item, change);
    } else if (item instanceof DexType) {
      adjustReferenceCount(file.indexedItems.types, (DexType) item, change);
    } else {
      assert false;
    }
  }

  private <T> void adjustReferenceCount(Reference2IntMap<T> items, T item, int change) {
    int count = items.containsKey(item) ? items.getInt(item) : 0;
    if (count == IndexedItemTransaction.NO_REF_COUNT) {
      // Checksum or marker.
      return;
    }
    int newCount = count + change;
    assert newCount >= 0;
    if (newCount == 0) {
      items.removeInt(item);
    } else {
      items.put(item, newCount);
    }
  }

  private class ItemCollector implements IndexedItemCollection {

    private final Set<DexItem> items = SetUtils.newIdentityHashSet();

    @Override
    public boolean addClass(DexProgramClass clazz) {
      return true;
    }

    @Override
    public boolean addField(DexField field) {
      return items.add(field);
    }

    @Override
    public boolean addMethod(DexMethod method) {
      return items.add(method);
    }

    @Override
    public boolean addString(DexString string) {
      return items.add(string);
    }

    @Override
    public boolean addProto(DexProto proto) {
      if (items.add(proto)) {
        DexString shorty =
            shortyCache.computeIfAbsent(
                proto.createShortyString(), appView.dexItemFactory()::createString);
        addString(shorty);
        return true;
      }
      return false;
    }

    @Override
    public boolean addType(DexType type) {
      return items.add(type);
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      return items.add(callSite);
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      return items.add(methodHandle);
    }

    public Set<DexItem> getItems() {
      return items;
    }
  }

  private interface PendingMoveTask {

    boolean tryMove(DexProgramClass clazz);
  }
}
