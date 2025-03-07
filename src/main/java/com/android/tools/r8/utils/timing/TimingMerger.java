// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import java.util.Collection;

public interface TimingMerger {

  void add(Collection<Timing> timings);

  void end();

  boolean isEmpty();

  TimingMerger disableSlowestReporting();
}
