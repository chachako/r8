// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.proto
import com.google.protobuf.gradle.ProtobufExtension
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.ZipEntryCompression
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
  id("net.ltgt.errorprone") version "3.0.1"
}

// It seems like the use of a local maven repo does not allow adding the plugin with the id+version
// syntax. Also, for some reason the 'protobuf' extension object cannot be directly referenced.
// This configures the plugin "old style" and pulls out the extension object manually.
buildscript {
  dependencies {
    classpath("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
  }
}
apply(plugin = "com.google.protobuf")
var os = DefaultNativePlatform.getCurrentOperatingSystem()
var protobuf = project.extensions.getByName("protobuf") as ProtobufExtension
protobuf.protoc {
  if (os.isLinux) {
    path = getRoot().resolveAll("third_party", "protoc", "linux-x86_64", "bin", "protoc").path
  } else if (os.isMacOsX) {
    path = getRoot().resolveAll("third_party", "protoc", "osx-x86_64", "bin", "protoc").path
  } else {
    assert(os.isWindows)
    path = getRoot().resolveAll("third_party", "protoc", "win64", "bin", "protoc.exe").path
  }
}

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "libanalyzer", "java"))
    proto {
      srcDir(getRoot().resolveAll("src", "libanalyzer", "proto"))
    }
  }
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
  toolchain {
    languageVersion = JavaLanguageVersion.of(JvmCompatibility.release)
  }
  withSourcesJar()
}

kotlin {
  explicitApi()
}

dependencies {
  compileOnly(Deps.guava)
  compileOnly(Deps.protobuf)
  compileOnly(":keepanno")
  compileOnly(":r8")
  errorprone(Deps.errorprone)
}

tasks.named<Jar>("jar") {
  exclude("libraryanalysisresult.proto")
  archiveFileName.set("libanalyzer-exclude-deps.jar")
}

tasks.withType<JavaCompile> {
  options.errorprone.excludedPaths.set(".*/build/generated/source/proto/main/java/.*")
}

fun libanalyzerJarDependencies(): FileCollection {
  return configurations
    .compileClasspath
    .get()
    .filter {
      val path = "$it"
      path.contains("third_party")
        && path.contains("dependencies")
        && (path.contains("com/google/guava/guava")
        || path.contains("com/google/protobuf/protobuf-java"))
    }
}

val downloadDepsTask = gradle.includedBuild("shared").task(":downloadDeps")
val r8Task = projectTask("main", "swissArmyKnifeWithoutLicense")
val r8DepsTask = projectTask("main", "depsJar")

tasks {
  val depsJar by registering(Jar::class) {
    dependsOn(downloadDepsTask)
    from(libanalyzerJarDependencies().map(::zipTree))
    exclude("META-INF/LICENSE*")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
    exclude("com/google/thirdparty/publicsuffix/**")
    exclude("google/protobuf/**")
    entryCompression = ZipEntryCompression.STORED
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("libanalyzer-deps.jar")
  }

  val libanalyzerWithRelocatedDeps by registering(Exec::class) {
    dependsOn(r8Task, r8DepsTask, depsJar, named("jar"))
    val libanalyzerJar = named<Jar>("jar").get().outputs.files.singleFile
    val deps = depsJar.get().outputs.files.singleFile
    val r8Jar = r8Task.outputs.files.singleFile
    val r8DepsJar = r8DepsTask.outputs.files.singleFile
    inputs.files(listOf(libanalyzerJar, deps))
    val output = "build/libs/libanalyzer.jar"
    outputs.file(output)
    val pkg = "com.android.tools.r8.libanalyzer"
    commandLine = baseCompilerCommandLine(
      r8Jar,
      r8DepsJar,
      "relocator",
      listOf(
        "--input",
        "$libanalyzerJar",
        "--input",
        "$deps",
        "--output",
        "$output",
        "--map",
        "com.android.tools.r8.libanalyzer.**->${pkg}",
        "--map",
        "com.google.common.**->${pkg}.com.google.common",
        "--map",
        "com.google.protobuf.**->${pkg}.com.google.protobuf"
      )
    )
  }
}

configureErrorProneForJavaCompile()
