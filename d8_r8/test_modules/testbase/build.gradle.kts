// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.util.concurrent.Callable
import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
  `java-library`
  id("dependencies-plugin")
}

val root = getRoot()

java {
  sourceSets.main.configure { java { srcDir(root.resolveAll("src", "test", "testbase", "java")) } }

  // We are using a new JDK to compile to an older language version, which is not directly
  // compatible with java toolchains.
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
}

kotlin { explicitApi() }

// If we depend on keepanno by referencing the project source outputs we get an error regarding
// incompatible java class file version. By depending on the jar we circumvent that.
val keepAnnoJarTask = projectTask("keepanno", "jar")
val keepAnnoCompileJavaTask = projectTask("keepanno", "compileJava")
val libraryAnalyzerCompileJavaTask = projectTask("libanalyzer", "compileJava")
val mainCompileJavaTask = projectTask("main", "compileJava")
val mainProcessResourcesTask = projectTask("main", "processResources")
val mainTurboCompileJavaTask = projectTask("main", "compileTurboJava")
val resourceShrinkerCompileJavaTask = projectTask("resourceshrinker", "compileJava")
val resourceShrinkerCompileKotlinTask = projectTask("resourceshrinker", "compileKotlin")
val resourceShrinkerDepsJarTask = projectTask("resourceshrinker", "depsJar")
val sharedDownloadDepsTask = projectTask("shared", "downloadDeps")
val sharedDownloadTestDepsTask = projectTask("shared", "downloadTestDeps")

dependencies {
  implementation(keepAnnoJarTask.outputs.files)
  implementation(libraryAnalyzerCompileJavaTask.outputs.files)
  implementation(mainCompileJavaTask.outputs.files)
  implementation(mainProcessResourcesTask.outputs.files)
  implementation(mainTurboCompileJavaTask.outputs.files)
  implementation(resourceShrinkerCompileJavaTask.outputs.files)
  implementation(resourceShrinkerCompileKotlinTask.outputs.files)
  implementation(resourceShrinkerDepsJarTask.outputs.files)
  implementation(Deps.androidxCollection)
  implementation(Deps.androidxTracingDriver)
  implementation(Deps.androidxTracingDriverWire)
  implementation(Deps.asm)
  implementation(Deps.asmCommons)
  implementation(Deps.asmUtil)
  implementation(Deps.gson)
  implementation(Deps.guava)
  implementation(Deps.javassist)
  implementation(Deps.junitJupiter)
  implementation(Deps.junitVintageEngine)
  implementation(Deps.kotlinStdLib)
  implementation(Deps.kotlinReflect)
  implementation(Deps.kotlinMetadata)
  implementation(resolve(ThirdPartyDeps.ddmLib, "ddmlib.jar"))
  implementation(resolve(ThirdPartyDeps.jasmin, "jasmin-2.4.jar"))
  implementation(resolve(ThirdPartyDeps.jdwpTests, "apache-harmony-jdwp-tests-host.jar"))
  implementation(Deps.fastUtil)
  implementation(Deps.smali)
  implementation(Deps.smaliUtil)
  runtimeOnly(Deps.junitPlatform)
}

fun testDependencies(): FileCollection {
  return sourceSets.test.get().compileClasspath.filter {
    "$it".contains("third_party") &&
      !"$it".contains("errorprone") &&
      !"$it".contains("third_party/gradle")
  }
}

tasks {
  withType<JavaCompile> {
    dependsOn(keepAnnoCompileJavaTask)
    dependsOn(mainCompileJavaTask)
    dependsOn(mainProcessResourcesTask)
    dependsOn(mainTurboCompileJavaTask)
    dependsOn(resourceShrinkerCompileJavaTask)
    dependsOn(sharedDownloadDepsTask)
    dependsOn(sharedDownloadTestDepsTask)
  }

  withType<JavaExec> {
    if (name.endsWith("main()")) {
      // IntelliJ pass the main execution through a stream which is
      // not compatible with gradle configuration cache.
      notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
    }
  }

  withType<KotlinCompile> { enabled = false }

  val assembleTestJar by
    registering(Jar::class) {
      from(sourceSets.main.get().output)
      // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
      // Renaming
      //  this from the default name (testbase.jar) will allow IntelliJ to find the resources in
      //  the jar and not show red underlines. However, navigation to base classes will not work.
      archiveFileName.set("not_named_testbase.jar")
    }

  val assembleDepsJar by
    registering(Jar::class) {
      dependsOn(keepAnnoJarTask)
      dependsOn(resourceShrinkerDepsJarTask)
      dependsOn(sharedDownloadDepsTask)
      dependsOn(sharedDownloadTestDepsTask)
      from(Callable { testDependencies().map(::zipTree) })
      from(Callable { keepAnnoJarTask.outputs.getFiles().map(::zipTree) })
      from(Callable { resourceShrinkerDepsJarTask.outputs.getFiles().map(::zipTree) })
      exclude("com/android/tools/r8/keepanno/annotations/**")
      exclude("androidx/annotation/keep/**")
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      archiveFileName.set("deps.jar")
    }
}

val testJar by configurations.consumable("testJar")

val depsJar by configurations.consumable("depsJar")

artifacts {
  add(testJar.name, tasks.named("assembleTestJar"))
  add(depsJar.name, tasks.named("assembleDepsJar"))
}
