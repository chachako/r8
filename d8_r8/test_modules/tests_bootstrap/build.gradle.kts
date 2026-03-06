// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
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
  sourceSets.test.configure { java { srcDir(root.resolveAll("src", "test", "bootstrap")) } }
  // We are using a new JDK to compile to an older language version, which is not directly
  // compatible with java toolchains.
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
}

kotlin { explicitApi() }

val distR8WithRelocatedDeps = projectTask("dist", "r8WithRelocatedDeps")
val distSwissArmyKnife = projectTask("dist", "swissArmyKnife")
val keepAnnoCompileJavaTask = projectTask("keepanno", "compileJava")
val keepAnnoCompileKotlinTask = projectTask("keepanno", "compileKotlin")
val keepAnnoJarTask = projectTask("keepanno", "jar")
val mainJarTask = projectTask("main", "jar")
val resourceShrinkerCompileJavaTask = projectTask("resourceshrinker", "compileJava")
val resourceShrinkerCompileKotlinTask = projectTask("resourceshrinker", "compileKotlin")
val resourceShrinkerDepsJarTask = projectTask("resourceshrinker", "depsJar")
val sharedDownloadDepsTask = projectTask("shared", "downloadDeps")
val sharedDownloadDepsInternalTask = projectTask("shared", "downloadDepsInternal")
val testbaseCompileJavaTask = projectTask("testbase", "compileJava")
val testbaseDepsJarTask = projectTask("testbase", "depsJar")

dependencies {
  implementation(keepAnnoJarTask.outputs.files)
  implementation(mainJarTask.outputs.files)
  implementation(resourceShrinkerCompileJavaTask.outputs.files)
  implementation(resourceShrinkerCompileKotlinTask.outputs.files)
  implementation(resourceShrinkerDepsJarTask.outputs.files)
  implementation(testbaseDepsJarTask.outputs.files)
  implementation(testbaseCompileJavaTask.outputs.files)
}

fun testDependencies(): FileCollection {
  return sourceSets.test.get().compileClasspath.filter {
    "$it".contains("third_party") &&
      !"$it".contains("errorprone") &&
      !"$it".contains("third_party/gradle")
  }
}

tasks {
  withType<JavaCompile> { dependsOn(mainJarTask) }

  withType<KotlinCompile> { compilerOptions { enabled = false } }

  withType<Test> {
    TestingState.setUpTestingState(this)
    dependsOn(distR8WithRelocatedDeps, distSwissArmyKnife)
    systemProperty(
      "TEST_DATA_LOCATION",
      layout.buildDirectory.dir("classes/java/test").get().toString(),
    )
    systemProperty(
      "TESTBASE_DATA_LOCATION",
      project.provider {
        testbaseCompileJavaTask.outputs.files.getAsPath().split(File.pathSeparator)[0]
      },
    )
    systemProperty(
      "BUILD_PROP_KEEPANNO_RUNTIME_PATH",
      project.provider {
        extractClassesPaths(
          "keepanno" + File.separator,
          keepAnnoCompileJavaTask.outputs.files.asPath,
          keepAnnoCompileKotlinTask.outputs.files.asPath,
        )
      },
    )
    systemProperty("R8_SWISS_ARMY_KNIFE", distSwissArmyKnife.outputs.files.singleFile)
    systemProperty("R8_WITH_RELOCATED_DEPS", distR8WithRelocatedDeps.outputs.files.singleFile)
    systemProperty("BUILD_PROP_R8_RUNTIME_PATH", distR8WithRelocatedDeps.outputs.files.singleFile)
  }

  val testJar by
    registering(Jar::class) {
      from(sourceSets.test.get().output)
      // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
      archiveFileName.set("not_named_tests_bootstrap.jar")
    }

  val depsJar by
    registering(Jar::class) {
      dependsOn(keepAnnoJarTask)
      dependsOn(sharedDownloadDepsTask)
      if (!project.hasProperty("no_internal")) {
        dependsOn(sharedDownloadDepsInternalTask)
      }
      from(Callable { testDependencies().map(::zipTree) })
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      archiveFileName.set("deps.jar")
    }
}
