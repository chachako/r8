// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.isClassFile;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

// Internal provider used to support --input <dir> on the Relocator command line.
public class DirectoryProgramResourceProvider
    implements ProgramResourceProvider, DataResourceProvider {

  private final Path directory;

  /** Create resource provider from directory path. */
  public static DirectoryProgramResourceProvider fromDirectory(Path directory) {
    return new DirectoryProgramResourceProvider(directory.toAbsolutePath());
  }

  private DirectoryProgramResourceProvider(Path directory) {
    this.directory = directory;
  }

  @Override
  public Collection<ProgramResource> getProgramResources() throws ResourceException {
    List<ProgramResource> programResources = new ArrayList<>();
    getProgramResources(programResources::add);
    return programResources;
  }

  @Override
  public void getProgramResources(Consumer<ProgramResource> consumer) throws ResourceException {
    try {
      walk(directory, consumer, null);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void walk(
      Path currentDirectory,
      Consumer<ProgramResource> consumer,
      DataResourceProvider.Visitor visitor)
      throws IOException {
    File file = currentDirectory.toFile();
    if (Files.exists(currentDirectory)) {
      File[] files = file.listFiles();
      if (files != null) {
        for (File child : files) {
          Path childPath = child.toPath();
          if (child.isDirectory()) {
            walk(childPath, consumer, visitor);
          } else if (isClassFile(childPath)) {
            if (consumer != null) {
              consumer.accept(ProgramResource.fromFile(Kind.CF, childPath));
            }
          } else if (visitor != null) {
            Path relativeChildPath = directory.relativize(childPath);
            visitor.visit(DataEntryResource.fromFile(directory, relativeChildPath));
          }
        }
      }
    }
  }

  @Override
  public DataResourceProvider getDataResourceProvider() {
    return this;
  }

  @Override
  public void accept(DataResourceProvider.Visitor visitor) throws ResourceException {
    try {
      walk(directory, null, visitor);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
