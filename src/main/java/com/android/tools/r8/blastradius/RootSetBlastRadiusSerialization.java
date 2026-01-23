// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import com.android.tools.r8.blastradius.proto.BlastRadiusContainer;

public class RootSetBlastRadiusSerialization {

  // TODO(b/441055269): Unimplemented.
  public static BlastRadiusContainer serialize(RootSetBlastRadius blastRadius) {
    BlastRadiusContainer.Builder builder = BlastRadiusContainer.newBuilder();
    return builder.build();
  }
}
