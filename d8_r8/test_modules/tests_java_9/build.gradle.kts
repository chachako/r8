// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.JavaVersion

plugins {
  `kotlin-dsl`
  `java-library`
  id("dependencies-plugin")
}

val root = getRoot()

java {
  sourceSets.test.configure { java.srcDir(root.resolveAll("src", "test", "examplesJava9")) }
  sourceCompatibility = JavaVersion.VERSION_1_9
  targetCompatibility = JavaVersion.VERSION_1_9
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
}

kotlin { explicitApi() }

evaluationDependsOn(":testbase")

val mainCompileJavaTask = projectTask("main", "compileJava")
val mainProcessResourcesTask = projectTask("main", "processResources")
val mainTurboCompileJavaTask = projectTask("main", "compileTurboJava")
val sharedDownloadDepsTask = projectTask("shared", "downloadDeps")

dependencies {
  implementation(mainCompileJavaTask.outputs.files)
  implementation(mainProcessResourcesTask.outputs.files)
  implementation(mainTurboCompileJavaTask.outputs.files)
  implementation(project(":testbase"))
  implementation(files(project.project(":testbase").tasks.named("depsJar")))
}

// We just need to register the examples jars for it to be referenced by other modules.
val buildExampleJars = buildExampleJars("examplesJava9")

tasks {
  withType<JavaCompile> { dependsOn(sharedDownloadDepsTask) }

  withType<Test> {
    notCompatibleWithConfigurationCache(
      "Failure storing the configuration cache: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache"
    )
    TestingState.setUpTestingState(this)
    systemProperty(
      "TEST_DATA_LOCATION",
      // This should be
      //   layout.buildDirectory.dir("classes/java/test").get().toString()
      // once the use of 'buildExampleJars' above is removed.
      getRoot().resolveAll("build", "test", "examplesJava9", "classes"),
    )
    systemProperty(
      "TESTBASE_DATA_LOCATION",
      project(":testbase")
        .tasks
        .named<JavaCompile>("compileJava")
        .get()
        .outputs
        .files
        .asPath
        .split(File.pathSeparator)[0],
    )
  }

  val testJar by
    registering(Jar::class) {
      from(sourceSets.test.get().output)
      // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
      // Renaming
      //  this from the default name (tests_java_8.jar) will allow IntelliJ to find the resources in
      //  the jar and not show red underlines. However, navigation to base classes will not work.
      archiveFileName.set("not_named_tests_java_9.jar")
    }
}
