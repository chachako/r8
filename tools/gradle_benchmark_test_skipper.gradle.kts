// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// Run :test:test without actually running any tests.
// This is achieved by filtering the tests with a non-existent name.
allprojects {
  tasks.withType<Test>().configureEach {
    filter {
      setIncludePatterns("ThisTestNameDoesNotAndWillNotExistsProbably")
      isFailOnNoMatchingTests = false
    }
  }
}
