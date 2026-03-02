// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.gson.Gson
import java.net.URI
import java.nio.file.Files.readString
import java.nio.file.Paths
import java.util.UUID
import org.gradle.api.tasks.bundling.Jar
import org.spdx.sbom.gradle.SpdxSbomTask
import org.spdx.sbom.gradle.extensions.DefaultSpdxSbomTaskExtension

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
  id("org.spdx.sbom") version "0.4.0"
}

if (project.hasProperty("spdxVersion")) {
  project.version = project.property("spdxVersion")!!
}

spdxSbom {
  targets {
    create("r8") {
      // Use of both compileClasspath and runtimeClasspath due to how the
      // dependencies jar is built and dependencies above therefore use
      // compileOnly for actual runtime dependencies.
      configurations.set(listOf("compileClasspath", "runtimeClasspath"))
      scm {
        uri.set("https://r8.googlesource.com/r8/")
        if (project.hasProperty("spdxRevision")) {
          revision.set(project.property("spdxRevision").toString())
        }
      }
      document {
        name.set("R8 Compiler Suite")
        // Generate version 5 UUID from fixed namespace UUID and name generated from revision
        // (git hash) and artifact name.
        if (project.hasProperty("spdxRevision")) {
          namespace.set(
            "https://spdx.google/" +
              uuid5(
                UUID.fromString("df17ea25-709b-4edc-8dc1-d3ca82c74e8e"),
                project.property("spdxRevision").toString() + "-r8",
              )
          )
        }
        creator.set("Organization: Google LLC")
        packageSupplier.set("Organization: Google LLC")
      }
    }
  }
}

val assistantJarTask = projectTask("assistant", "jar")
val blastRadiusJarTask = projectTask("blastradius", "jar")
val blastRadiusProtoJarTask = projectTask("blastradius", "protoJar")
val keepAnnoJarTask = projectTask("keepanno", "jar")
val keepAnnoDepsJarExceptAsm = projectTask("keepanno", "depsJarExceptAsm")
val keepAnnoToolsJar = projectTask("keepanno", "toolsJar")
val libraryAnalyzerJarTask = projectTask("libanalyzer", "jar")
val libraryAnalyzerProtoJarTask = projectTask("libanalyzer", "protoJar")
val resourceShrinkerJarTask = projectTask("resourceshrinker", "jar")
val resourceShrinkerDepsTask = projectTask("resourceshrinker", "depsJar")
val mainJarTask = projectTask("main", "jar")
val mainCompileJavaTask = projectTask("main", "compileJava")
val downloadDepsTask = projectTask("shared", "downloadDeps")
val downloadTestDepsTask = projectTask("shared", "downloadTestDeps")

fun mainJarDependencies(): FileCollection {
  return (mainCompileJavaTask as JavaCompile)
    .classpath
    .filter({
      "$it".contains("third_party") &&
        "$it".contains("dependencies") &&
        !"$it".contains("errorprone")
    })
}

fun relocateDepsExceptAsm(pkg: String): List<String> {
  return listOf(
    "--map",
    "android.aapt.**->${pkg}.android.aapt",
    "--map",
    "androidx.annotation.**->${pkg}.androidx.annotation",
    "--map",
    "androidx.collection.**->${pkg}.androidx.collection",
    "--map",
    "androidx.tracing.**->${pkg}.androidx.tracing",
    "--map",
    "com.android.**->${pkg}.com.android",
    "--map",
    "com.android.zipflinger.**->${pkg}.com.android.zipflinger",
    "--map",
    "com.google.common.**->${pkg}.com.google.common",
    "--map",
    "com.google.gson.**->${pkg}.com.google.gson",
    "--map",
    "com.google.thirdparty.**->${pkg}.com.google.thirdparty",
    "--map",
    "com.squareup.wire.**->${pkg}.com.squareup.wire",
    "--map",
    "it.unimi.dsi.fastutil.**->${pkg}.it.unimi.dsi.fastutil",
    "--map",
    "kotlin.**->${pkg}.jetbrains.kotlin",
    "--map",
    "kotlinx.**->${pkg}.jetbrains.kotlinx",
    "--map",
    "okio.**->${pkg}.okio",
    "--map",
    "org.jetbrains.**->${pkg}.org.jetbrains",
    "--map",
    "org.intellij.**->${pkg}.org.intellij",
    "--map",
    "org.checkerframework.**->${pkg}.org.checkerframework",
    "--map",
    "com.google.j2objc.**->${pkg}.com.google.j2objc",
    "--map",
    "com.google.protobuf.**->${pkg}.com.google.protobuf",
    "--map",
    "perfetto.protos.**->${pkg}.perfetto.protos",
    "--map",
    "org.jspecify.annotations.**->${pkg}.org.jspecify.annotations",
    "--map",
    "_COROUTINE.**->${pkg}._COROUTINE",
  )
}

tasks {
  withType<Exec> { doFirst { println("Executing command: ${commandLine.joinToString(" ")}") } }

  withType<SpdxSbomTask> {
    taskExtension.set(
      object : DefaultSpdxSbomTaskExtension() {
        override fun mapRepoUri(input: URI?, moduleId: ModuleVersionIdentifier): URI? {

          // Locate the file origin.json with URL for download location.
          fun getOriginJson(): java.nio.file.Path {
            var repositoryDir =
              moduleId.group.replace('.', '/') + "/" + moduleId.name + "/" + moduleId.version
            return Paths.get("third_party", "dependencies", repositoryDir, "origin.json")
          }

          // Simple data model of the content of origin.json generated by the tool to download
          // and create a local repository. E.g.:
          /*
              {
                "artifacts": [
                  {
                    "file": "org/ow2/asm/asm/9.5/asm-9.5.pom",
                    "repo": "https://repo1.maven.org/maven2/",
                    "artifact": "org.ow2.asm:asm:pom:9.5"
                  },
                  {
                    "file": "org/ow2/asm/asm/9.5/asm-9.5.jar",
                    "repo": "https://repo1.maven.org/maven2/",
                    "artifact": "org.ow2.asm:asm:jar:9.5"
                  }
                ]
              }
          */
          data class Artifact(val file: String, val repo: String, val artifact: String)
          data class Artifacts(val artifacts: List<Artifact>)

          // Read origin.json.
          val json = readString(getOriginJson())
          val artifacts = Gson().fromJson(json, Artifacts::class.java)
          return URI.create(artifacts.artifacts.get(0).repo)
        }
      }
    )
  }

  val consolidatedLicense by registering {
    dependsOn(downloadDepsTask)
    dependsOn(downloadTestDepsTask)
    val root = getRoot()
    val r8License = root.resolve("LICENSE")
    val libraryLicense = root.resolve("LIBRARY-LICENSE")
    val libraryLicenseFiles = fileTree(root.resolve("library-licensing"))
    inputs.files(
      listOf(r8License, libraryLicense),
      libraryLicenseFiles,
      mainJarDependencies().map(::zipTree),
    )

    val license = getRoot().resolveAll("build", "generatedLicense", "LICENSE")
    outputs.files(license)
    val dependencies = mutableListOf<String>()
    configurations
      .findByName("runtimeClasspath")!!
      .resolvedConfiguration
      .resolvedArtifacts
      .forEach {
        val identifier = it.id.componentIdentifier
        if (identifier is ModuleComponentIdentifier) {
          dependencies.add("${identifier.group}:${identifier.module}")
        }
      }

    doLast {
      val libraryLicenses = libraryLicense.readText()
      dependencies.forEach {
        if (!libraryLicenses.contains("- artifact: $it")) {
          throw GradleException("No license for $it in LIBRARY_LICENSE")
        }
      }
      license.getParentFile().mkdirs()
      license.createNewFile()
      license.writeText(
        buildString {
          append("This file lists all licenses for code distributed.\n")
          append("All non-library code has the following 3-Clause BSD license.\n")
          append("\n")
          append("\n")
          append(r8License.readText())
          append("\n")
          append("\n")
          append("Summary of distributed libraries:\n")
          append("\n")
          append(libraryLicense.readText())
          append("\n")
          append("\n")
          append("Licenses details:\n")
          libraryLicenseFiles.sorted().forEach { file ->
            append("\n\n")
            append(file.readText())
          }
        }
      )
    }
  }

  val swissArmyKnife by
    registering(Jar::class) {
      dependsOn(
        assistantJarTask,
        blastRadiusJarTask,
        keepAnnoJarTask,
        gradle.includedBuild("libanalyzer").task(":jar"),
        resourceShrinkerJarTask,
        mainJarTask,
      )
      dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
      from(mainJarTask.outputs.files.map(::zipTree))
      exclude("com/android/tools/r8/threading/providers/**")
      from(assistantJarTask.outputs.files.map(::zipTree))
      from(blastRadiusJarTask.outputs.files.map(::zipTree))
      from(keepAnnoJarTask.outputs.files.map(::zipTree))
      val libraryAnalyzerJar =
        getRoot()
          .resolveAll("d8_r8", "libanalyzer", "build", "libs", "libanalyzer-exclude-deps.jar")
      from(provider { zipTree(libraryAnalyzerJar) })
      from(resourceShrinkerJarTask.outputs.files.map(::zipTree))
      from(getRoot().resolve("LICENSE"))
      entryCompression = ZipEntryCompression.STORED
      manifest { attributes["Main-Class"] = "com.android.tools.r8.SwissArmyKnife" }
      exclude("META-INF/*.kotlin_module")
      exclude("**/*.kotlin_metadata")
      exclude("blastradius.proto")
      exclude("keepspec.proto")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("r8-full-exclude-deps.jar")
    }

  val threadingModuleBlockingJar by
    registering(Zip::class) {
      dependsOn(mainJarTask)
      from(mainJarTask.outputs.files.map(::zipTree))
      include("com/android/tools/r8/threading/providers/blocking/**")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("threading-module-blocking.jar")
    }

  val threadingModuleSingleThreadedJar by
    registering(Zip::class) {
      dependsOn(mainJarTask)
      from(mainJarTask.outputs.files.map(::zipTree))
      include("com/android/tools/r8/threading/providers/singlethreaded/**")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("threading-module-single-threaded.jar")
    }

  // Jar containing all 3p deps, plus R8 threading modules.
  val depsJar by
    registering(Zip::class) {
      dependsOn(downloadDepsTask)
      dependsOn(resourceShrinkerDepsTask)
      dependsOn(threadingModuleBlockingJar)
      dependsOn(threadingModuleSingleThreadedJar)
      from(threadingModuleBlockingJar.get().outputs.getFiles().map(::zipTree))
      from(threadingModuleSingleThreadedJar.get().outputs.getFiles().map(::zipTree))
      from(mainJarDependencies().map(::zipTree))
      from(resourceShrinkerDepsTask.outputs.files.map(::zipTree))
      from(consolidatedLicense)
      exclude("**/module-info.class")
      exclude("**/*.kotlin_metadata")
      exclude("META-INF/*.kotlin_module")
      exclude("META-INF/com.android.tools/**")
      exclude("META-INF/LICENSE*")
      exclude("META-INF/MANIFEST.MF")
      exclude("META-INF/kotlinx_coroutines_core.version")
      exclude("META-INF/androidx/**/LICENSE.txt")
      exclude("META-INF/maven/**")
      exclude("META-INF/proguard/**")
      exclude("META-INF/versions/**")
      exclude("META-INF/services/kotlin.reflect.**")
      exclude("**/*.xml")
      exclude("com/android/version.properties")
      exclude("NOTICE")
      exclude("README.md")
      exclude("javax/annotation/**")
      exclude("wireless/**")
      exclude("google/protobuf/**")
      exclude("DebugProbesKt.bin")

      // Disabling compression makes this step go from 4s -> 2s as of Nov 2025,
      // as measured by "gradle --profile".
      entryCompression = ZipEntryCompression.STORED

      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      archiveFileName.set("deps.jar")
    }

  val protoJar by
    registering(Zip::class) {
      dependsOn(blastRadiusProtoJarTask, libraryAnalyzerProtoJarTask)
      from(blastRadiusProtoJarTask.outputs.files.map(::zipTree))
      from(libraryAnalyzerProtoJarTask.outputs.files.map(::zipTree))
      exclude("META-INF/MANIFEST.MF")
      archiveFileName.set("proto.jar")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    }

  val swissArmyKnifeWithoutLicense by
    registering(Zip::class) {
      dependsOn(swissArmyKnife)
      from(swissArmyKnife.get().outputs.files.map(::zipTree))
      exclude("LICENSE")
      exclude("androidx/")
      exclude("androidx/annotation/")
      exclude("androidx/annotation/keep/**")
      archiveFileName.set("swiss-army-no-license.jar")
    }

  val r8WithRelocatedDeps by
    registering(Exec::class) {
      dependsOn(depsJar)
      dependsOn(protoJar)
      dependsOn(swissArmyKnifeWithoutLicense)
      val swissArmy = swissArmyKnifeWithoutLicense.get().outputs.files.singleFile
      val deps = depsJar.get().outputs.files.singleFile
      val proto = protoJar.getSingleOutputFile()
      inputs.files(listOf(swissArmy, proto, deps))
      val output = getRoot().resolveAll("build", "libs", "r8.jar")
      outputs.file(output)
      val pkg = "com.android.tools.r8"
      commandLine =
        baseCompilerCommandLine(
          swissArmy,
          deps,
          "relocator",
          listOf(
            "--input",
            "$swissArmy",
            "--input",
            "$proto",
            "--input",
            "$deps",
            "--output",
            "$output",
            "--map",
            "com.android.tools.r8.**->${pkg}",
            "--map",
            "com.android.tools.r8.keepanno.annotations.**->${pkg}.keepanno.annotations",
            "--map",
            "com.android.tools.r8.keepanno.**->${pkg}.relocated.keepanno",
            "--map",
            "org.objectweb.asm.**->${pkg}.org.objectweb.asm",
          ) + relocateDepsExceptAsm(pkg),
        )
    }

  val keepAnnoToolsWithRelocatedDeps by
    registering(Exec::class) {
      dependsOn(depsJar)
      dependsOn(swissArmyKnifeWithoutLicense)
      dependsOn(keepAnnoDepsJarExceptAsm)
      dependsOn(keepAnnoToolsJar)
      val swissArmy = swissArmyKnifeWithoutLicense.get().outputs.files.singleFile
      val deps = depsJar.get().outputs.files.singleFile
      val keepAnnoDeps = keepAnnoDepsJarExceptAsm.outputs.files.singleFile
      val tools = keepAnnoToolsJar.outputs.files.singleFile
      inputs.files(listOf(tools, keepAnnoDeps))
      val output = getRoot().resolveAll("build", "libs", "keepanno-tools.jar")
      outputs.file(output)
      val pkg = "com.android.tools.r8.keepanno"
      commandLine =
        baseCompilerCommandLine(
          swissArmy,
          deps,
          "relocator",
          listOf(
            "--input",
            "$tools",
            "--input",
            "$keepAnnoDeps",
            "--output",
            "$output",
            "--map",
            "com.android.tools.r8.keepanno.**->${pkg}",
          ) + relocateDepsExceptAsm(pkg),
        )
    }

  val processKeepRulesLibWithRelocatedDeps by
    registering(Exec::class) {
      dependsOn(r8WithRelocatedDeps)
      val createR8LibFile = getRoot().resolveAll("tools", "create_r8lib.py")
      val keepRulesFile = getRoot().resolveAll("src", "main", "keep_processkeeprules.txt")
      val r8WithRelocatedDepsJar = r8WithRelocatedDeps.get().outputs.files.singleFile
      inputs.files(listOf(createR8LibFile, keepRulesFile, r8WithRelocatedDepsJar))
      val outputJar = getRoot().resolveAll("build", "libs", "processkeepruleslib.jar")
      outputs.file(outputJar)
      commandLine =
        createR8LibCommandLine(
          r8WithRelocatedDepsJar,
          r8WithRelocatedDepsJar,
          outputJar,
          listOf(keepRulesFile),
          excludingDepsVariant = false,
          debugVariant = false,
          classpath = listOf(),
          enableKeepAnnotations = false,
        )
    }
}

fun Task.getSingleOutputFile(): File = getOutputs().getSingleOutputFile()

fun TaskOutputs.getSingleOutputFile(): File = getFiles().getSingleFile()

fun TaskProvider<*>.getSingleOutputFile(): File = get().getSingleOutputFile()
