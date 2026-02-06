// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import org.gradle.internal.os.OperatingSystem

rootProject.name = "d8-r8"

// Bootstrap building by downloading dependencies.
val dependencies_bucket = "r8-deps"

fun downloadFromGoogleStorage(outputDir : File) {
  val targz = File(outputDir.toString() + ".tar.gz")
  val sha1File = File(targz.toString() + ".sha1")
  if (outputDir.exists()
      && outputDir.isDirectory
      && targz.exists()
      && sha1File.lastModified() <= targz.lastModified()) {
      // We already downloaded, no need to recheck the hash
      return
  }

  var downloadScript = "download_from_google_storage.py"
  if (OperatingSystem.current().isWindows()) {
    downloadScript = "download_from_google_storage.bat"
  }
  val cmd = listOf(
    downloadScript,
    "--extract",
    "--bucket",
    dependencies_bucket,
    "--sha1_file",
    "${sha1File}"
  )

  println("Executing command: ${cmd.joinToString(" ")}")
  val process = ProcessBuilder().command(cmd).start()
  process.waitFor()
  if (process.exitValue() != 0) {
    throw GradleException(
      "Bootstrapping ${outputDir} download failed:\n"
        + "${String(process.getErrorStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)}\n"
        + String(process.getInputStream().readAllBytes(),
                 java.nio.charset.StandardCharsets.UTF_8))
  } else {
    // Ensure that the gz file is more recent than the .sha1 file
    // People that upload a new version will generally have an older .sha1 file
    println("Updating timestamp on " + targz)
    val now = FileTime.fromMillis(System.currentTimeMillis())
    Files.setLastModifiedTime(targz.toPath(), now)
  }
}

val thirdParty = rootProject.projectDir.resolve("third_party")
downloadFromGoogleStorage(thirdParty.resolve("dependencies"))
downloadFromGoogleStorage(thirdParty.resolve("dependencies_plugin"))

pluginManagement {
  repositories {
    maven {
      url = uri("file:third_party/dependencies")
    }
    maven {
      url = uri("file:third_party/dependencies_plugin")
    }
  }
  includeBuild(rootProject.projectDir.resolve("d8_r8/commonBuildSrc"))
}

dependencyResolutionManagement {
  repositories {
    maven {
      url = uri("file:third_party/dependencies_plugin")
    }
    maven {
      url = uri("file:third_party/dependencies")
    }
  }
}

includeBuild(rootProject.projectDir.resolve("d8_r8/shared"))
includeBuild(rootProject.projectDir.resolve("d8_r8/assistant"))
includeBuild(rootProject.projectDir.resolve("d8_r8/blastradius"))
includeBuild(rootProject.projectDir.resolve("d8_r8/keepanno"))
includeBuild(rootProject.projectDir.resolve("d8_r8/libanalyzer"))
includeBuild(rootProject.projectDir.resolve("d8_r8/resourceshrinker"))

// We need to include src/main as a composite-build otherwise our test-modules
// will compete with the test to compile the source files.
includeBuild(rootProject.projectDir.resolve("d8_r8/main"))
includeBuild(rootProject.projectDir.resolve("d8_r8/library_desugar"))
includeBuild(rootProject.projectDir.resolve("d8_r8/test"))
