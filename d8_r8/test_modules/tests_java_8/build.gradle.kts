// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
  `java-library`
  id("dependencies-plugin")
}

val root = getRoot()

java {
  sourceSets.test.configure {
    java {
      srcDir(root.resolveAll("src", "test", "java"))
      // Generated art tests
      srcDir(root.resolveAll("build", "generated", "test", "java"))
    }
  }
  // We are using a new JDK to compile to an older language version, as we don't have JDK-8 for
  // Windows in our repo.
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain { languageVersion = JavaLanguageVersion.of(11) }
}

kotlin { explicitApi() }

val testbaseCompileJavaTask = projectTask("testbase", "compileJava")
val testbaseDepsJarTask = projectTask("testbase", "depsJar")

// If we depend on keepanno by referencing the project source outputs we get an error regarding
// incompatible java class file version. By depending on the jar we circumvent that.
val assistantCompileTask = projectTask("assistant", "compileJava")
val blastRadiusCompileTask = projectTask("blastradius", "compileJava")
val distDepsFilesTask = projectTask("dist", "depsFiles")
val keepAnnoJarTask = projectTask("keepanno", "jar")
val keepAnnoCompileJavaTask = projectTask("keepanno", "compileJava")
val keepAnnoCompileKotlinTask = projectTask("keepanno", "compileKotlin")
val libraryAnalyzerCompileTask = projectTask("libanalyzer", "compileJava")
val mainCompileJavaTask = projectTask("main", "compileJava")
val mainProcessResourcesTask = projectTask("main", "processResources")
val mainTurboCompileJavaTask = projectTask("main", "compileTurboJava")
val resourceShrinkerCompileJavaTask = projectTask("resourceshrinker", "compileJava")
val resourceShrinkerCompileKotlinTask = projectTask("resourceshrinker", "compileKotlin")
val resourceShrinkerDepsJarTask = projectTask("resourceshrinker", "depsJar")
val sharedDownloadDepsTask = projectTask("shared", "downloadDeps")
val sharedDownloadDepsInternalTask = projectTask("shared", "downloadDepsInternal")

dependencies {
  implementation(assistantCompileTask.outputs.files)
  implementation(blastRadiusCompileTask.outputs.files)
  implementation(keepAnnoJarTask.outputs.files)
  implementation(libraryAnalyzerCompileTask.outputs.files)
  implementation(mainCompileJavaTask.outputs.files)
  implementation(mainProcessResourcesTask.outputs.files)
  implementation(mainTurboCompileJavaTask.outputs.files)
  implementation(resourceShrinkerCompileJavaTask.outputs.files)
  implementation(resourceShrinkerCompileKotlinTask.outputs.files)
  implementation(resourceShrinkerDepsJarTask.outputs.files)
  implementation(testbaseDepsJarTask.outputs.files)
  implementation(testbaseCompileJavaTask.outputs.files)
}

val sourceSetDependenciesTasks =
  arrayOf(projectTask("tests_java_9", getExampleJarsTaskName("examplesJava9")))

fun testDependencies(): FileCollection {
  return sourceSets.test.get().compileClasspath.filter {
    "$it".contains("third_party") &&
      !"$it".contains("errorprone") &&
      !"$it".contains("third_party/gradle")
  }
}

tasks {
  getByName<Delete>("clean") {
    // TODO(b/327315907): Don't generating into the root build dir.
    delete.add(
      getRoot()
        .resolveAll("build", "generated", "test", "java", "com", "android", "tools", "r8", "art")
    )
  }

  val createArtTests by
    registering(Exec::class) {
      dependsOn(sharedDownloadDepsTask)
      // TODO(b/327315907): Don't generating into the root build dir.
      val outputDir =
        getRoot()
          .resolveAll("build", "generated", "test", "java", "com", "android", "tools", "r8", "art")
      val createArtTestsScript = getRoot().resolveAll("tools", "create_art_tests.py")
      inputs.file(createArtTestsScript)
      inputs.dir(getRoot().resolveAll("tests", "2017-10-04"))
      outputs.dir(outputDir)
      workingDir(getRoot())
      commandLine("python3", createArtTestsScript)
    }
  "compileTestJava" {
    dependsOn(sharedDownloadDepsTask)
    dependsOn(testbaseCompileJavaTask)
  }
  withType<JavaCompile> {
    dependsOn(createArtTests)
    dependsOn(keepAnnoCompileJavaTask)
    dependsOn(mainCompileJavaTask)
    dependsOn(resourceShrinkerCompileJavaTask)
    dependsOn(sharedDownloadDepsTask)
    dependsOn(testbaseCompileJavaTask)
  }

  withType<JavaExec> {
    if (name.endsWith("main()")) {
      // IntelliJ pass the main execution through a stream which is
      // not compatible with gradle configuration cache.
      notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
    }
  }

  withType<KotlinCompile> { enabled = false }

  val sourceSetDependencyTask by registering { dependsOn(*sourceSetDependenciesTasks) }

  withType<Test> {
    TestingState.setUpTestingState(this)
    dependsOn(distDepsFilesTask)
    dependsOn(sharedDownloadDepsTask)
    if (!project.hasProperty("no_internal")) {
      dependsOn(sharedDownloadDepsInternalTask)
    }
    dependsOn(sourceSetDependencyTask)
    systemProperty(
      "TEST_DATA_LOCATION",
      layout.buildDirectory.dir("classes/java/test").get().toString(),
    )
    systemProperty(
      "TESTBASE_DATA_LOCATION",
      testbaseCompileJavaTask.outputs.files.asPath.split(File.pathSeparator)[0],
    )
    systemProperty(
      "BUILD_PROP_KEEPANNO_RUNTIME_PATH",
      extractClassesPaths(
        "keepanno" + File.separator,
        keepAnnoCompileJavaTask.outputs.files.asPath,
        keepAnnoCompileKotlinTask.outputs.files.asPath,
      ),
    )
    // This path is set when compiling examples jar task in DependenciesPlugin.
    val r8RuntimePath =
      mainCompileJavaTask.outputs.files.getAsPath().split(File.pathSeparator)[0] +
        File.pathSeparator +
        mainTurboCompileJavaTask.outputs.files.getAsPath().split(File.pathSeparator)[0] +
        File.pathSeparator +
        distDepsFilesTask.outputs.files.getAsPath() +
        File.pathSeparator +
        getRoot().resolveAll("src", "main", "resources") +
        File.pathSeparator +
        keepAnnoCompileJavaTask.outputs.files.getAsPath().split(File.pathSeparator)[0] +
        File.pathSeparator +
        assistantCompileTask.outputs.files.getAsPath().split(File.pathSeparator)[0] +
        File.pathSeparator +
        resourceShrinkerCompileJavaTask.outputs.files.getAsPath().split(File.pathSeparator)[0] +
        File.pathSeparator +
        resourceShrinkerCompileKotlinTask.outputs.files.getAsPath().split(File.pathSeparator)[1]
    systemProperty("BUILD_PROP_PROCESS_KEEP_RULES_RUNTIME_PATH", r8RuntimePath)
    systemProperty("BUILD_PROP_R8_RUNTIME_PATH", r8RuntimePath)
    systemProperty("R8_DEPS", distDepsFilesTask.outputs.files.getAsPath())
    systemProperty("com.android.tools.r8.artprofilerewritingcompletenesscheck", "true")
  }

  val testJar by
    registering(Jar::class) {
      from(sourceSets.test.get().output)
      // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
      // Renaming
      //  this from the default name (tests_java_8.jar) will allow IntelliJ to find the resources in
      //  the jar and not show red underlines. However, navigation to base classes will not work.
      archiveFileName.set("not_named_tests_java_8.jar")
    }
}
