// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import com.android.tools.r8.DataDirectoryResource;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceConsumer;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.DataResourceProvider.Visitor;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.naming.KotlinModuleSynthesizer;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class DataResourceWriter {

  public static void adaptAndPassDataResources(
      InternalOptions options,
      DataResourceConsumer dataResourceConsumer,
      Collection<DataResourceProvider> dataResourceProviders,
      ResourceAdapter resourceAdapter) {
    Set<String> generatedResourceNames = new HashSet<>();

    for (DataResourceProvider dataResourceProvider : dataResourceProviders) {
      try {
        dataResourceProvider.accept(
            new Visitor() {
              @Override
              public void visit(DataDirectoryResource directory) {
                if (shouldDropDataDirectoryResource(directory, options)) {
                  return;
                }
                DataDirectoryResource adapted = resourceAdapter.adaptIfNeeded(directory);
                if (adapted != null) {
                  dataResourceConsumer.accept(adapted, options.reporter);
                  options.reporter.failIfPendingErrors();
                }
              }

              @Override
              public void visit(DataEntryResource file) {
                if (shouldDropDataEntryResource(file, options)) {
                  return;
                }
                DataEntryResource adapted = resourceAdapter.adaptIfNeeded(file);
                if (generatedResourceNames.add(adapted.getName())) {
                  dataResourceConsumer.accept(adapted, options.reporter);
                } else {
                  options.reporter.warning(
                      new StringDiagnostic("Resource '" + file.getName() + "' already exists."));
                }
                options.reporter.failIfPendingErrors();
              }
            });
      } catch (ResourceException e) {
        throw new CompilationError(e.getMessage(), e);
      }
    }
  }

  public static boolean shouldDropDataDirectoryResource(
      DataDirectoryResource directory, InternalOptions options) {
    if (options.getProguardConfiguration() == null) {
      assert options.testing.enableD8MetaInfServicesPassThrough;
      return true;
    }
    return !options.getProguardConfiguration().getKeepDirectories().matches(directory.getName());
  }

  public static boolean shouldDropDataEntryResource(
      DataEntryResource file, InternalOptions options) {
    if ("META-INF/MANIFEST.MF".equals(file.getName())) {
      // Many android library input .jar files contain a MANIFEST.MF. It does not make
      // sense to propagate them since they are manifests of the input libraries.
      if (options.isGeneratingDex() || options.getTestingOptions().forcePruneMetaInfManifestMf) {
        return true;
      }
    }
    if (file.getName().startsWith(AppServices.SERVICE_DIRECTORY_NAME)) {
      // META-INF/services resources are handled separately.
      return true;
    }
    if (KotlinModuleSynthesizer.isKotlinModuleFile(file)) {
      // .kotlin_module files are synthesized.
      return true;
    }
    return false;
  }
}
