// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `kotlin-dsl`
  `java-library`
  id("dependencies-plugin")
}

val root = getRoot()

java {
  sourceSets.test.configure { java.srcDir(root.resolveAll("src", "test", "java25")) }
  toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

kotlin { explicitApi() }

val mainCompileJavaTask = projectTask("main", "compileJava")
val mainProcessResourcesTask = projectTask("main", "processResources")
val mainTurboCompileJavaTask = projectTask("main", "compileTurboJava")
val sharedDownloadDepsTask = projectTask("shared", "downloadDeps")
val testbaseCompileJavaTask = projectTask("testbase", "compileJava")
val testbaseDepsJarTask = projectTask("testbase", "depsJar")

dependencies {
  implementation(mainCompileJavaTask.outputs.files)
  implementation(mainProcessResourcesTask.outputs.files)
  implementation(mainTurboCompileJavaTask.outputs.files)
  implementation(testbaseCompileJavaTask.outputs.files)
  implementation(testbaseDepsJarTask.outputs.files)
  testImplementation(Deps.junitJupiter)
  testRuntimeOnly(Deps.junitPlatform)
}

tasks {
  withType<JavaCompile> { dependsOn(sharedDownloadDepsTask) }

  withType<Test> {
    notCompatibleWithConfigurationCache(
      "Failure storing the configuration cache: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache"
    )
    TestingState.setUpTestingState(this)
    javaLauncher = getJavaLauncher(Jdk.JDK_25)
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
  }
}
