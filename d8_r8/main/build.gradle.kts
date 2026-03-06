// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.inject.Inject
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
  id("net.ltgt.errorprone") version "3.0.1"
}

// Properties that you can set in your ~/.gradle/gradle.properties:

// Use a separate sourceSet for files that have been modified when doing incremental builds.
// Speeds up compile times where the list of files isn't changed from 1-2 minutes -> 1-2 seconds.
//
// Modified files are determined using git, and the list of modified files never shrinks (since
// that would cause build errors). However, it is safe to fully reset the list of modified files,
// which you can do by deleting d8_r8/main/build/turbo-paths.txt.
//
// What's the catch?
// Unmodified sources that depend on modified ones will *not be rebuilt* when modified sources
// change. This is where the speed-up comes from, but can lead to runtime crashes if signatures
// change without references to them being updated.
// Be sure to fix problems reported by IntelliJ when using this mode.
val isGeminiCli = "1".equals(System.getenv("GEMINI_CLI"))
var enableTurboBuilds = project.hasProperty("enable_r8_turbo_builds") && !isGeminiCli

val MAIN_JAVA_PATH_PREFIX = "src/main/java/"

interface TurboPathsValueSourceParameters : ValueSourceParameters {
  val pathPrefix: Property<String>
  val turboPathsFile: Property<File>
  val extraGlobs: ListProperty<String>
  val mainOutputDir: Property<File>
}

enum class TurboReason {
  FIRST_BUILD,
  PATHS_CHANGED,
  PATHS_UNCHANGED,
  CORRUPT_FILE,
  TOO_MANY_PATHS,
}

data class TurboState(val paths: List<String>, val reason: TurboReason)

abstract class TurboPathsValueSource : ValueSource<TurboState, TurboPathsValueSourceParameters> {
  @get:Inject abstract val execOperations: ExecOperations

  fun isDirectoryEmpty(path: File): Boolean {
    if (!path.exists()) {
      return true
    }

    val files = path.listFiles()
    return files == null || files.isEmpty()
  }

  override fun obtain(): TurboState? {
    val prefix = parameters.pathPrefix.get()
    val turboPathsFile = parameters.turboPathsFile.get()
    val extraGlobs = parameters.extraGlobs.get()
    val mainOutputDir = parameters.mainOutputDir.get()

    // Check for first build (since the turbo sourceSet requires the main one
    // to have been built already).
    if (isDirectoryEmpty(mainOutputDir)) {
      return TurboState(listOf(), TurboReason.FIRST_BUILD)
    }

    var mergeBase = "origin/main"
    val pathSet: MutableSet<String> = mutableSetOf()

    if (turboPathsFile.exists()) {
      val lines = turboPathsFile.readLines()
      if (!lines.isEmpty() && lines[0].startsWith("mergebase=")) {
        mergeBase = lines[0].removePrefix("mergebase=")
        pathSet.addAll(lines.drop(1))
      } else {
        // Corrupt file.
        turboPathsFile.delete()
        return TurboState(listOf(), TurboReason.CORRUPT_FILE)
      }
    }

    val prevNumSource = pathSet.size
    val output = ByteArrayOutputStream()
    execOperations.exec {
      commandLine = listOf("git", "diff", "--name-only", "--merge-base", mergeBase)
      standardOutput = output
    }
    val result = String(output.toByteArray(), Charset.defaultCharset())
    val gitPaths =
      result
        .lines()
        .filter { it.startsWith(prefix) && it.endsWith(".java") }
        .map { it.trim().removePrefix(prefix) }
    pathSet.addAll(gitPaths)

    val ret = pathSet.toMutableList()
    ret.sort()
    // Allow users to specify extra globs.
    ret += extraGlobs

    if (mergeBase == "origin/main") {
      output.reset()
      execOperations.exec {
        commandLine = listOf("git", "rev-parse", "origin/main")
        standardOutput = output
      }
      mergeBase = String(output.toByteArray(), Charset.defaultCharset()).trim()
    }

    if (pathSet.size > 200 && gitPaths.size < 40) {
      // File has gotten too big. Start fresh.
      turboPathsFile.delete()
      return TurboState(listOf(), TurboReason.TOO_MANY_PATHS)
    }

    turboPathsFile.writeText("mergebase=$mergeBase\n" + ret.joinToString("\n"))
    val changed = prevNumSource != pathSet.size
    val reason =
      if (pathSet.isEmpty()) TurboReason.FIRST_BUILD
      else if (changed) TurboReason.PATHS_CHANGED else TurboReason.PATHS_UNCHANGED
    return TurboState(ret, reason)
  }
}

val turboPathsProvider: Provider<TurboState> =
  providers.of(TurboPathsValueSource::class.java) {
    parameters.pathPrefix.set(MAIN_JAVA_PATH_PREFIX)

    // Wipe this file to remove files from the active set.
    parameters.turboPathsFile.set(layout.buildDirectory.file("turbo-paths.txt").get().asFile)

    parameters.extraGlobs.set(
      project.findProperty("turbo_build_globs")?.toString()?.split(',') ?: emptyList()
    )

    parameters.mainOutputDir.set(sourceSets["main"].java.destinationDirectory.get().getAsFile())
  }

// Add all changed files to the "turbo" source set.
val turboState = if (enableTurboBuilds) turboPathsProvider.get() else null

if (turboState != null) {
  val numFiles = turboState.paths.size
  val msg =
    when (turboState.reason) {
      TurboReason.FIRST_BUILD -> "First build detected. Build will be slow."
      TurboReason.PATHS_CHANGED -> "Paths in active set have changed. Build will be slow."
      TurboReason.PATHS_UNCHANGED -> "Paths unchanged. Size=$numFiles. Build should be fast!"
      TurboReason.CORRUPT_FILE -> "turbo-paths.txt was invalid. Build will be slow."
      TurboReason.TOO_MANY_PATHS -> "Paths were compacted. Build will be slow."
    }
  logger.warn("Turbo: $msg")
} else if (isGeminiCli) {
  logger.warn("Turbo: enable_r8_turbo_builds=false (gemini-cli detected)")
} else {
  logger.warn("Turbo: enable_r8_turbo_builds=false")
}

java {
  sourceSets {
    val srcDir = getRoot().resolveAll("src", "main", "java")

    main {
      resources.srcDirs(getRoot().resolveAll("third_party", "api_database", "api_database"))
      java {
        srcDir(srcDir)
        if (turboState != null && !turboState.paths.isEmpty()) {
          exclude(turboState.paths)
        }
      }
    }

    // Must be created unconditionally so that other targets can depend on it.
    create("turbo") {
      java {
        srcDir(srcDir)
        if (turboState != null && !turboState.paths.isEmpty()) {
          include(turboState.paths)
        } else {
          exclude("*")
        }
      }
    }
  }

  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
  withSourcesJar()
}

kotlin { explicitApi() }

val assistantJarTask = projectTask("assistant", "jar")
val blastRadiusJarTask = projectTask("blastradius", "jar")
val blastRadiusProtoJarTask = projectTask("blastradius", "protoJar")
val keepAnnoJarTask = projectTask("keepanno", "jar")
val keepAnnoDepsJarExceptAsm = projectTask("keepanno", "depsJarExceptAsm")
val keepAnnoToolsJar = projectTask("keepanno", "toolsJar")
val resourceShrinkerJarTask = projectTask("resourceshrinker", "jar")
val resourceShrinkerDepsTask = projectTask("resourceshrinker", "depsJar")
val downloadDepsTask = projectTask("shared", "downloadDeps")

fun mainJarDependencies(): FileCollection {
  return sourceSets.main
    .get()
    .compileClasspath
    .filter({
      "$it".contains("third_party") &&
        "$it".contains("dependencies") &&
        !"$it".contains("errorprone")
    })
}

dependencies {
  implementation(assistantJarTask.outputs.files)
  implementation(blastRadiusJarTask.outputs.files)
  implementation(blastRadiusProtoJarTask.outputs.files)
  implementation(keepAnnoJarTask.outputs.files)
  implementation(resourceShrinkerJarTask.outputs.files)
  Deps.compilerDeps.forEach { compileOnly(it) }
  errorprone(Deps.errorprone)
}

if (enableTurboBuilds) {
  tasks.named("compileJava") {
    // Makes compileTurboJava run first, but does not cause compileJava to re-run if
    // compileTurboJava changes.
    dependsOn(tasks.named("compileTurboJava"))
  }

  // Does not include main's output directory, which must also be added when compilation avoidance
  // causes only a subset of sources to be recompiled.
  val mainClasspath = sourceSets["main"].compileClasspath.getAsPath()

  tasks.named<JavaCompile>("compileTurboJava") {
    // Add the main's classes to the classpath without letting gradle know about this dependency
    // (as it's a circular one).
    options.compilerArgs.add("-classpath")
    options.compilerArgs.add(
      "" +
        sourceSets["turbo"].java.destinationDirectory.get() +
        File.pathSeparator +
        mainClasspath +
        File.pathSeparator +
        sourceSets["main"].java.destinationDirectory.get()
    )
  }

  tasks.named<JavaCompile>("compileJava") {
    // Add the turbo's classes to the classpath without letting gradle know about this dependency
    // (or else it will cause it to rebuild whenever files in it change).
    options.compilerArgs.add("-classpath")
    options.compilerArgs.add(
      "" +
        sourceSets["main"].java.destinationDirectory.get() +
        File.pathSeparator +
        mainClasspath +
        File.pathSeparator +
        sourceSets["turbo"].java.destinationDirectory.get()
    )
  }
}

tasks {
  jar {
    from(sourceSets["turbo"].output)
    doLast {
      enforceUncompressedEntries(archiveFile.get().asFile, setOf("resources/new_api_database.ser"))
    }
  }

  withType<Exec> { doFirst { println("Executing command: ${commandLine.joinToString(" ")}") } }
}

tasks.withType<KotlinCompile> { enabled = false }

tasks.withType<JavaCompile> {
  dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
  println("NOTE: Running with JDK: " + org.gradle.internal.jvm.Jvm.current().javaHome)
}

tasks.withType<ProcessResources> { dependsOn(gradle.includedBuild("shared").task(":downloadDeps")) }

configureErrorProneForJavaCompile()
