// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.GradleException

/**
 * Re-packages a JAR file to ensure specific entries are stored uncompressed (STORED).
 *
 * @param jarFile The target JAR file to modify in-place.
 * @param uncompressedEntries A set of file paths to store uncompressed.
 */
public fun enforceUncompressedEntries(jarFile: File, uncompressedEntries: Set<String>) {
  if (!jarFile.exists()) return
  val remainingUncompressedEntries = uncompressedEntries.toMutableSet()
  val tempJarFile = jarFile.resolveSibling(jarFile.name + ".tmp")
  ZipFile(jarFile).use { zip ->
    ZipOutputStream(FileOutputStream(tempJarFile)).use { zos ->
      val entries = zip.entries()
      while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        val newEntry = ZipEntry(entry.name)

        if (uncompressedEntries.contains(entry.name)) {
          remainingUncompressedEntries.remove(entry.name)
          // Read data into memory to calculate CRC and size required for STORED method.
          val bytes = zip.getInputStream(entry).readAllBytes()
          newEntry.method = ZipEntry.STORED
          newEntry.size = bytes.size.toLong()
          newEntry.compressedSize = bytes.size.toLong()
          newEntry.crc = CRC32().apply { update(bytes) }.value
          zos.putNextEntry(newEntry)
          zos.write(bytes)
        } else {
          // Copy metadata and stream content directly.
          newEntry.method = entry.method
          if (newEntry.method == ZipEntry.STORED) {
            newEntry.size = entry.size
            newEntry.compressedSize = entry.compressedSize
            newEntry.crc = entry.crc
          }
          zos.putNextEntry(newEntry)
          zip.getInputStream(entry).copyTo(zos)
        }
        zos.closeEntry()
      }
    }
  }

  if (remainingUncompressedEntries.isNotEmpty()) {
    throw GradleException(
      "Expected to uncompress the following entries in $jarFile, but they were not found: " +
        remainingUncompressedEntries.joinToString(", ")
    )
  }

  // Overwrite the original jar.
  Files.move(tempJarFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
}
