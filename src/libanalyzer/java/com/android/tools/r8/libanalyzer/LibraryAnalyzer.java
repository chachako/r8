// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

// TODO(b/479726064): Extend SanityCheck test to verify that the libanalyzer jar does not contain
//  any entries outside com/android/tools/r8/libanalyzer/.
// TODO(b/479726064): Add support for writing LibraryAnalyzer tests.
// TODO(b/479726064): If this ends up not being bundled into r8.jar, do we need this to have its own
//  Version.java or a --version?
@KeepForApi
public class LibraryAnalyzer {

  public static void main(String[] args) {
    // TBD
  }
}
