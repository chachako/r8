// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import static com.android.tools.r8.errors.StartupClassesNonStartupFractionDiagnostic.Factory.createStartupClassesNonStartupFractionDiagnostic;
import static com.android.tools.r8.errors.StartupClassesOverflowDiagnostic.Factory.createStartupClassesOverflowDiagnostic;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.profile.startup.distribution.MultiStartupDexDistributor;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.timing.Timing;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

/**
 * Distributes the given classes over the files in package order.
 *
 * <p>The populator avoids package splits. Big packages are split into subpackages if their size
 * exceeds 20% of the dex file. This populator also avoids filling files completely to cater for
 * future growth.
 *
 * <p>The populator cycles through the files until all classes have been successfully placed and
 * adds new files to the passed in map if it can't fit in the existing files.
 */
public class PackageSplitPopulator {

  static class PackageSplitClassPartioning {

    // The set of startup classes, sorted by original names so that classes in the same package
    // are adjacent. This is empty if no startup configuration is given.
    private final List<DexProgramClass> startupClasses;

    // The remaining set of classes that must be written, sorted by original names so that classes
    // in the same package are adjacent.
    private final List<DexProgramClass> nonStartupClasses;

    private PackageSplitClassPartioning(
        List<DexProgramClass> startupClasses, List<DexProgramClass> nonStartupClasses) {
      this.startupClasses = startupClasses;
      this.nonStartupClasses = nonStartupClasses;
    }

    public static PackageSplitClassPartioning create(
        Collection<DexProgramClass> classes,
        Map<DexProgramClass, String> originalNames,
        StartupProfile startupProfile) {
      return create(
          classes,
          getClassesByPackageComparator(originalNames),
          getStartupClassPredicate(startupProfile));
    }

    private static PackageSplitClassPartioning create(
        Collection<DexProgramClass> classes,
        Comparator<DexProgramClass> comparator,
        Predicate<DexProgramClass> startupClassPredicate) {
      List<DexProgramClass> startupClasses = new ArrayList<>();
      List<DexProgramClass> nonStartupClasses = new ArrayList<>(classes.size());
      for (DexProgramClass clazz : classes) {
        if (startupClassPredicate.test(clazz)) {
          startupClasses.add(clazz);
        } else {
          nonStartupClasses.add(clazz);
        }
      }
      startupClasses.sort(comparator);
      nonStartupClasses.sort(comparator);
      return new PackageSplitClassPartioning(startupClasses, nonStartupClasses);
    }

    private static Comparator<DexProgramClass> getClassesByPackageComparator(
        Map<DexProgramClass, String> originalNames) {
      return (a, b) -> {
        String originalA = originalNames.get(a);
        String originalB = originalNames.get(b);
        int indexA = originalA.lastIndexOf('.');
        int indexB = originalB.lastIndexOf('.');
        if (indexA == -1 && indexB == -1) {
          // Empty package, compare the class names.
          return originalA.compareTo(originalB);
        }
        if (indexA == -1) {
          // Empty package name comes first.
          return -1;
        }
        if (indexB == -1) {
          // Empty package name comes first.
          return 1;
        }
        String prefixA = originalA.substring(0, indexA);
        String prefixB = originalB.substring(0, indexB);
        int result = prefixA.compareTo(prefixB);
        if (result != 0) {
          return result;
        }
        return originalA.compareTo(originalB);
      };
    }

    private static Predicate<DexProgramClass> getStartupClassPredicate(
        StartupProfile startupProfile) {
      return clazz -> startupProfile.isStartupClass(clazz.getType());
    }

    public List<DexProgramClass> getStartupClasses() {
      return startupClasses;
    }

    public List<DexProgramClass> getNonStartupClasses() {
      return nonStartupClasses;
    }
  }

  /**
   * Android suggests com.company.product for package names, so the components will be at level 4
   */
  private static final int MINIMUM_PREFIX_LENGTH = 4;

  private static final int MAXIMUM_PREFIX_LENGTH = 7;

  /**
   * We allow 1/MIN_FILL_FACTOR of a file to remain empty when moving to the next file, i.e., a
   * rollback with less than 1/MAX_FILL_FACTOR of the total classes in a file will move to the next
   * file.
   */
  private static final int MIN_FILL_FACTOR = 5;

  private final AppView<?> appView;
  private final PackageSplitClassPartioning classPartioning;
  private final Map<DexProgramClass, String> originalNames;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;
  private final VirtualFileCycler cycler;
  private final StartupProfile startupProfile;

  public PackageSplitPopulator(
      List<VirtualFile> files,
      List<VirtualFile> filesForDistribution,
      AppView<?> appView,
      Collection<DexProgramClass> classes,
      Map<DexProgramClass, String> originalNames,
      StartupProfile startupProfile,
      IntBox nextFileId) {
    this.appView = appView;
    this.classPartioning =
        PackageSplitClassPartioning.create(classes, originalNames, startupProfile);
    this.originalNames = originalNames;
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
    this.cycler = new VirtualFileCycler(files, filesForDistribution, appView, nextFileId);
    this.startupProfile = startupProfile;
  }

  static boolean coveredByPrefix(String originalName, String currentPrefix) {
    if (currentPrefix == null) {
      return false;
    }
    if (currentPrefix.endsWith(".*")) {
      return originalName.startsWith(currentPrefix.substring(0, currentPrefix.length() - 2));
    } else {
      return originalName.startsWith(currentPrefix)
          && originalName.lastIndexOf('.') == currentPrefix.length();
    }
  }

  private String getOriginalName(DexProgramClass clazz) {
    return originalNames.get(clazz);
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    addStartupClasses();
    distributeClasses(classPartioning.getNonStartupClasses());
    DexDistributionRefinement.run(appView, cycler, executorService, timing);
  }

  private void addStartupClasses() {
    List<DexProgramClass> startupClasses = classPartioning.getStartupClasses();
    if (startupClasses.isEmpty()) {
      return;
    }

    assert options.getStartupOptions().hasStartupProfileProviders();

    // In practice, all startup classes should fit in a single dex file, so optimistically try to
    // commit the startup classes using a single transaction.
    VirtualFile virtualFile = cycler.next();
    for (DexProgramClass startupClass : classPartioning.getStartupClasses()) {
      virtualFile.addClass(startupClass);
    }

    boolean isSingleStartupDexFile = hasSpaceForTransaction(virtualFile, options);
    if (isSingleStartupDexFile) {
      virtualFile.commitTransaction();
      virtualFile.setStartup();
    } else {
      virtualFile.abortTransaction();

      // If the above failed, then apply the selected multi startup dex distribution strategy.
      MultiStartupDexDistributor distributor =
          MultiStartupDexDistributor.get(options, startupProfile);
      distributor.distribute(classPartioning.getStartupClasses(), this, virtualFile, cycler);
      cycler.getFilesForDistribution().forEach(VirtualFile::setStartup);

      options.reporter.warning(
          createStartupClassesOverflowDiagnostic(cycler.getFilesForDistribution().size()));
    }

    options.reporter.info(
        createStartupClassesNonStartupFractionDiagnostic(
            classPartioning.getStartupClasses(), startupProfile));

    if (options.getStartupOptions().isMinimalStartupDexEnabled()) {
      // Minimal startup dex only applies to the case where there is a single startup DEX file.
      // When there are multiple startup DEX files, we allow filling up the last startup DEX file
      // with non-startup classes.
      VirtualFile lastFileForDistribution = ListUtils.last(cycler.getFilesForDistribution());
      cycler.clearFilesForDistribution();
      if (!isSingleStartupDexFile) {
        cycler.addFileForDistribution(lastFileForDistribution);
      }
    } else {
      cycler.restart();
    }
  }

  public void distributeClasses(List<DexProgramClass> classes) {
    List<DexProgramClass> nonPackageClasses = addPackageClasses(classes);
    addNonPackageClasses(cycler, nonPackageClasses);
  }

  @SuppressWarnings("ReferenceEquality")
  private List<DexProgramClass> addPackageClasses(List<DexProgramClass> classes) {
    int prefixLength = MINIMUM_PREFIX_LENGTH;
    int transactionStartIndex = 0;
    String currentPrefix = null;
    Object2IntMap<String> packageAssignments = new Object2IntOpenHashMap<>();
    VirtualFile current = cycler.ensureFile().next();
    List<DexProgramClass> nonPackageClasses = new ArrayList<>();
    for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
      DexProgramClass clazz = classes.get(classIndex);
      String originalName = getOriginalName(clazz);
      if (!coveredByPrefix(originalName, currentPrefix)) {
        if (currentPrefix != null) {
          current.commitTransaction();
          assert verifyPackageToVirtualFileAssignment(packageAssignments, currentPrefix, current);
          // Reset the cycler to again iterate over all files, starting with the current one.
          cycler.restart();
          // Try to reduce the prefix length if possible. Only do this on a successful commit.
          prefixLength = MINIMUM_PREFIX_LENGTH - 1;
        }
        String newPrefix;
        // Also, we need to avoid new prefixes that are a prefix of previously used prefixes, as
        // otherwise we might generate an overlap that will trigger problems when reusing the
        // package mapping generated here. For example, if an existing map contained
        //   com.android.foo.*
        // but we now try to place some new subpackage
        //   com.android.bar.*,
        // we locally could use
        //   com.android.*.
        // However, when writing out the final package map, we get overlapping patterns
        // com.android.* and com.android.foo.*.
        do {
          newPrefix = extractPrefixToken(++prefixLength, originalName, false);
        } while (currentPrefix != null && currentPrefix.startsWith(newPrefix));
        // Don't set the current prefix if we did not extract one.
        if (!newPrefix.isEmpty()) {
          currentPrefix = extractPrefixToken(prefixLength, originalName, true);
        }
        transactionStartIndex = classIndex;
      }

      if (currentPrefix == null) {
        assert clazz.superType != null;
        // We don't have a package, add this to a list of classes that we will add last.
        assert current.getTransaction().isEmpty();
        nonPackageClasses.add(clazz);
        continue;
      }

      assert clazz.superType != null || clazz.type == dexItemFactory.objectType;
      current.addClass(clazz);

      if (hasSpaceForTransaction(current, options)) {
        continue;
      }

      int numberOfClassesInTransaction = classIndex - transactionStartIndex + 1;
      int numberOfClassesInVirtualFileWithTransaction = current.getNumberOfClasses();

      current.abortTransaction();

      // We allow for a final rollback that has at most 20% of classes in it.
      // This is a somewhat random number that was empirically chosen.
      if (numberOfClassesInTransaction
              > numberOfClassesInVirtualFileWithTransaction / MIN_FILL_FACTOR
          && prefixLength < MAXIMUM_PREFIX_LENGTH) {
        // Go back to previous start index.
        classIndex = transactionStartIndex - 1;
        currentPrefix = null;
        prefixLength++;
        continue;
      }

      // Reset the state to after the last commit and cycle through files.
      // The idea is that we do not increase the number of files, so it has to fit somewhere.
      if (!cycler.hasNext()) {
        // Special case where we simply will never be able to fit the current package into
        // one dex file. This is currently the case for Strings in jumbo tests, see b/33227518.
        if (current.isEmpty()) {
          for (int j = transactionStartIndex; j <= classIndex; j++) {
            nonPackageClasses.add(classes.get(j));
          }
          transactionStartIndex = classIndex + 1;
        }
        // All files are filled up to the 20% mark.
        cycler.addFile();
      }

      // Go back to previous start index.
      classIndex = transactionStartIndex - 1;
      current = cycler.next();
      currentPrefix = null;
      prefixLength = MINIMUM_PREFIX_LENGTH;
    }

    current.commitTransaction();
    assert currentPrefix == null
        || verifyPackageToVirtualFileAssignment(packageAssignments, currentPrefix, current);

    return nonPackageClasses;
  }

  private static String extractPrefixToken(int prefixLength, String className, boolean addStar) {
    int index = 0;
    int lastIndex = 0;
    int segmentCount = 0;
    while (lastIndex != -1 && segmentCount++ < prefixLength) {
      index = lastIndex;
      lastIndex = className.indexOf('.', index + 1);
    }
    String prefix = className.substring(0, index);
    if (addStar && segmentCount >= prefixLength) {
      // Full match, add a * to also match sub-packages.
      prefix += ".*";
    }
    return prefix;
  }

  private boolean verifyPackageToVirtualFileAssignment(
      Object2IntMap<String> packageAssignments, String packageName, VirtualFile virtualFile) {
    assert !packageAssignments.containsKey(packageName);
    packageAssignments.put(packageName, virtualFile.getId());
    return true;
  }

  private boolean hasSpaceForTransaction(VirtualFile current, InternalOptions options) {
    return !isFullEnough(current, options);
  }

  private boolean isFullEnough(VirtualFile current, InternalOptions options) {
    if (options.testing.limitNumberOfClassesPerDex > 0
        && current.getNumberOfClasses() > options.testing.limitNumberOfClassesPerDex) {
      return true;
    }
    int maxEntries = VirtualFile.MAX_ENTRIES;
    if (options.testing.classToDexDistributionRefinementPasses > 0) {
      // Leave a bit of room for the refinement to be more effective.
      maxEntries -=
          maxEntries * (options.testing.classToDexDistributionRefinementLegRoomPercentage / 100);
    }
    return current.isFull(maxEntries);
  }

  private void addNonPackageClasses(
      VirtualFileCycler cycler, List<DexProgramClass> nonPackageClasses) {
    if (nonPackageClasses.isEmpty()) {
      return;
    }
    cycler.restart();
    VirtualFile current;
    current = cycler.next();
    for (DexProgramClass clazz : nonPackageClasses) {
      if (current.isFull()) {
        current = getVirtualFile(cycler);
      }
      current.addClass(clazz);
      while (current.isFull()) {
        // This only happens if we have a huge class, that takes up more than 20% of a dex file.
        current.abortTransaction();
        current = getVirtualFile(cycler);
        boolean wasEmpty = current.isEmpty();
        current.addClass(clazz);
        if (wasEmpty && current.isFull()) {
          throw new InternalCompilerError(
              "Class " + clazz.toString() + " does not fit into a single dex file.");
        }
      }
      current.commitTransaction();
    }
  }

  private VirtualFile getVirtualFile(VirtualFileCycler cycler) {
    VirtualFile current = null;
    while (cycler.hasNext() && isFullEnough(current = cycler.next(), options)) {}
    if (current == null || isFullEnough(current, options)) {
      current = cycler.addFile();
    }
    return current;
  }
}
