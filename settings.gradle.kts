// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

rootProject.name = "d8-r8"

// third_party/dependencies and third_party/dependencies_plugin
// is downloaded and populated by running 'tools/gradle.py'.
pluginManagement {
  repositories {
    maven { url = uri("third_party/dependencies") }
    maven { url = uri("third_party/dependencies_plugin") }
  }
  includeBuild(rootProject.projectDir.resolve("d8_r8/commonBuildSrc"))
}

dependencyResolutionManagement {
  repositories {
    maven { url = uri("third_party/dependencies_plugin") }
    maven { url = uri("third_party/dependencies") }
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

includeBuild(rootProject.projectDir.resolve("d8_r8/dist"))

includeBuild(rootProject.projectDir.resolve("d8_r8/library_desugar"))

includeBuild(rootProject.projectDir.resolve("d8_r8/test_modules/testbase"))

includeBuild(rootProject.projectDir.resolve("d8_r8/test_modules/tests_bootstrap"))

include(":tests_java_8")

project(":tests_java_8").projectDir = file("d8_r8/test_modules/tests_java_8")

includeBuild(rootProject.projectDir.resolve("d8_r8/test_modules/tests_java_9"))

includeBuild(rootProject.projectDir.resolve("d8_r8/test_modules/tests_java_11"))

includeBuild(rootProject.projectDir.resolve("d8_r8/test_modules/tests_java_17"))

includeBuild(rootProject.projectDir.resolve("d8_r8/test_modules/tests_java_21"))

includeBuild(rootProject.projectDir.resolve("d8_r8/test_modules/tests_java_25"))

include(":test")

project(":test").projectDir = file("d8_r8/test")
