// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipOutputStream;

public class ClassFileConsumerUtils {

  public static class ArchiveConsumerUtils {

    public static void writeFileNow(
        ClassFileConsumer.ArchiveConsumer consumer,
        byte[] bytes,
        String descriptor,
        DiagnosticsHandler handler) {
      consumer.writeFileNow(ByteDataView.of(bytes), descriptor, handler);
    }

    public static void writeResourcesForTesting(
        Path archive,
        List<ProgramResource> resources,
        Set<DataDirectoryResource> dataDirectoryResources,
        Set<DataEntryResource> dataEntryResources)
        throws IOException, ResourceException {
      OpenOption[] options =
          new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
      try (Closer closer = Closer.create()) {
        try (ZipOutputStream out =
            new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(archive, options)))) {
          ZipUtils.writeResourcesToZip(
              resources, dataDirectoryResources, dataEntryResources, closer, out);
        }
      }
    }
  }

  public static class DirectoryConsumerUtils {

    public static void writeResourcesForTesting(Path directory, List<ProgramResource> resources)
        throws IOException, ResourceException {
      try (Closer closer = Closer.create()) {
        for (ProgramResource resource : resources) {
          Path target = getTargetCfFile(directory, resource);
          writeFile(ByteStreams.toByteArray(closer.register(resource.getByteStream())), target);
        }
      }
    }

    private static Path getTargetCfFile(Path directory, ProgramResource resource) {
      assert resource.getClassDescriptors() != null;
      assert resource.getClassDescriptors().size() == 1;
      String descriptor = resource.getClassDescriptors().iterator().next();
      String internalName = DescriptorUtils.descriptorToInternalName(descriptor);
      return directory.resolve(internalName + ".class");
    }

    private static void writeFile(byte[] contents, Path target) throws IOException {
      Files.createDirectories(target.getParent());
      FileUtils.writeToFile(target, null, contents);
    }
  }
}
