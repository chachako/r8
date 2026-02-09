// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

pluginManagement {
  repositories {
    maven { url = uri("file:../../third_party/dependencies") }
    maven { url = uri("file:../../third_party/dependencies_plugin") }
  }
}

dependencyResolutionManagement {
  repositories {
    maven { url = uri("file:../../third_party/dependencies") }
    maven { url = uri("file:../../third_party/dependencies_plugin") }
  }
}

rootProject.name = "common-build-src"
