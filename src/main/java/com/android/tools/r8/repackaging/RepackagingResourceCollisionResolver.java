// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.repackaging;

import static com.android.tools.r8.dex.DataResourceWriter.shouldDropDataDirectoryResource;
import static com.android.tools.r8.dex.DataResourceWriter.shouldDropDataEntryResource;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.DataResourceProvider.Visitor;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.dex.ResourceAdapter;
import com.android.tools.r8.features.FeatureSplitConfiguration;
import com.android.tools.r8.features.FeatureSplitConfiguration.DataResourceProvidersAndConsumer;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramPackage;
import com.android.tools.r8.graph.SortedProgramPackageCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.PackageObfuscationMode;
import com.android.tools.r8.utils.MapUtils;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class RepackagingResourceCollisionResolver {

  // Map from each package to the set of packages it collides with.
  private final Map<ProgramPackage, Set<ProgramPackage>> collisions = new HashMap<>();

  private final Set<ProgramPackage> blocked = Sets.newIdentityHashSet();

  private RepackagingResourceCollisionResolver() {}

  private RepackagingResourceCollisionResolver(Collection<RepackagingCollision> collisions) {
    for (RepackagingCollision collision : collisions) {
      if (collision.size() > 1) {
        for (ProgramPackage pkg : collision.packages) {
          this.collisions
              .computeIfAbsent(pkg, ignoreKey(Sets::newIdentityHashSet))
              .addAll(collision.packages);
        }
      }
    }
  }

  public static RepackagingResourceCollisionResolver create(
      AppView<AppInfoWithLiveness> appView,
      SortedProgramPackageCollection packages,
      PackageObfuscationMode packageObfuscationMode) {
    // If there is no resource consumer then collisions don't matter.
    if (appView.options().dataResourceConsumer == null) {
      return empty();
    }

    // If we are not repackaging then there are no collisions.
    if (!packageObfuscationMode.isRepackageClasses()) {
      return empty();
    }

    // Visit all data resources meanwhile repackaging all packages to the same target package and
    // collecting the collisions.
    InterceptingResourceAdapter adapter = new InterceptingResourceAdapter(appView, packages);
    Map<String, RepackagingCollision> collisions = new HashMap<>();
    InternalOptions options = appView.options();
    forEachDataResourceProvider(
        appView,
        provider -> {
          try {
            provider.accept(
                new Visitor() {
                  @Override
                  public void visit(DataDirectoryResource directory) {
                    if (shouldDropDataDirectoryResource(directory, options)) {
                      return;
                    }
                    collisions
                        .computeIfAbsent(
                            adapter.adaptDirectoryName(directory),
                            ignoreKey(RepackagingCollision::new))
                        .addInterceptedPackagesFrom(adapter);
                  }

                  @Override
                  public void visit(DataEntryResource file) {
                    if (shouldDropDataEntryResource(file, options)) {
                      return;
                    }
                    collisions
                        .computeIfAbsent(
                            adapter.adaptFileNameIfNeeded(file),
                            ignoreKey(RepackagingCollision::new))
                        .addInterceptedPackagesFrom(adapter);
                  }
                });
          } catch (ResourceException e) {
            appView.reporter().error(new ExceptionDiagnostic(e));
          }
        });
    return new RepackagingResourceCollisionResolver(collisions.values());
  }

  private static void forEachDataResourceProvider(
      AppView<AppInfoWithLiveness> appView, Consumer<DataResourceProvider> consumer) {
    appView.app().dataResourceProviders.forEach(consumer);
    if (appView.options().hasFeatureSplitConfiguration()) {
      FeatureSplitConfiguration featureSplitConfiguration =
          appView.options().getFeatureSplitConfiguration();
      for (DataResourceProvidersAndConsumer entry :
          featureSplitConfiguration.getDataResourceProvidersAndConsumers()) {
        entry.providers.forEach(consumer);
      }
    }
  }

  private static RepackagingResourceCollisionResolver empty() {
    return new RepackagingResourceCollisionResolver();
  }

  // Called when a package is repackaged to the target package (e.g., the default package "").
  // When this happens we prohibit repackaging of all packages that collide with the current one,
  // by adding them to the `blocked` set. These packages will be subject to -flattenpackagehierarchy
  // instead of -repackageclasses.
  void acceptRepackagedPackage(ProgramPackage pkg) {
    Set<ProgramPackage> pkgCollisions =
        MapUtils.removeOrDefault(collisions, pkg, Collections.emptySet());
    for (ProgramPackage pkgBlocked : pkgCollisions) {
      if (pkgBlocked != pkg) {
        blocked.add(pkgBlocked);
        collisions.remove(pkgBlocked);
      }
    }
  }

  boolean isBlocked(ProgramPackage pkg) {
    return blocked.contains(pkg);
  }

  // A resource adapter implementation that stores the set of packages that are being queried during
  // calls to adaptDirectoryName() or adaptFileNameIfNeeded().
  //
  // If two resources are mapped to the same file name, then we conservatively treat the packages
  // that were queried during the renaming as colliding.
  //
  // This only intercepts calls to adaptPackage() (and not also adaptType()), since repackaging does
  // not cause any collisions among types.
  private static class InterceptingResourceAdapter extends ResourceAdapter {

    private final SortedProgramPackageCollection packages;

    private final Set<ProgramPackage> interceptedPackages = Sets.newIdentityHashSet();

    InterceptingResourceAdapter(
        AppView<AppInfoWithLiveness> appView, SortedProgramPackageCollection packages) {
      super(appView);
      this.packages = packages;
    }

    @Override
    public String adaptDirectoryName(DataDirectoryResource directory) {
      assert interceptedPackages.isEmpty();
      return super.adaptDirectoryName(directory);
    }

    @Override
    public String adaptFileNameIfNeeded(DataEntryResource file) {
      assert interceptedPackages.isEmpty();
      return super.adaptFileNameIfNeeded(file);
    }

    @Override
    protected String adaptPackage(String packageDescriptor) {
      ProgramPackage pkg = packages.getPackage(packageDescriptor);
      if (pkg != null) {
        interceptedPackages.add(pkg);
        return "";
      }
      return packageDescriptor;
    }
  }

  private static class RepackagingCollision {

    private final Set<ProgramPackage> packages = Sets.newIdentityHashSet();

    void addInterceptedPackagesFrom(InterceptingResourceAdapter adapter) {
      packages.addAll(adapter.interceptedPackages);
      adapter.interceptedPackages.clear();
    }

    int size() {
      return packages.size();
    }
  }
}
