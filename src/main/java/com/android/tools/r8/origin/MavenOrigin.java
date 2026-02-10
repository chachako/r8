// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.origin;

import androidx.annotation.keep.KeepForApi;

/**
 * An identifier for a component instance which is available as a module version. See also {@link
 * org.gradle.api.artifacts.component.ModuleComponentIdentifier}.
 */
@KeepForApi
public interface MavenOrigin {

  String getDisplayName();

  String getGroup();

  String getModule();

  String getVersion();
}
