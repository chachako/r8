// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import androidx.tracing.EventMetadataCloseable;
import androidx.tracing.PropagationUnsupportedToken;
import androidx.tracing.ThreadTrack;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.InternalOptions;

class PerfettoThreadTiming extends TimingImplBase {
  // The API now allows you to specify the category and in the future let you conditionally turn on
  // categories.
  private static final String TRACE_CATEGORY = "R8";
  private final ThreadTrack threadTrack;

  private int depth = 0;

  PerfettoThreadTiming(ThreadTrack threadTrack) {
    this.threadTrack = threadTrack;
  }

  @Override
  public Timing createThreadTiming(String title, InternalOptions options) {
    int threadId = (int) Thread.currentThread().getId();
    ThreadTrack newThreadTrack =
        threadTrack.getProcess().getOrCreateThreadTrack(threadId, "Worker");
    return new PerfettoThreadTiming(newThreadTrack).begin(title);
  }

  @Override
  public void notifyThreadTimingFinished() {
    assert depth == 0;
  }

  @Override
  public Timing begin(String title) {
    assert threadTrack.getId() == Thread.currentThread().getId();
    // Actually dispatch events to trace sink after adding any additional event metadata if needed.
    // You can do so by using: eventMetadataCloseable.metadata.add* APIs.
    // R8 is using the low level track APIs (and Java) which is why the API looks the way it does.
    EventMetadataCloseable eventMetadataCloseable =
        threadTrack.beginSection$tracing(
            TRACE_CATEGORY, title, PropagationUnsupportedToken.INSTANCE);
    eventMetadataCloseable.metadata.dispatchToTraceSink();
    depth++;
    return this;
  }

  @Override
  public Timing end() {
    assert threadTrack.getId() == Thread.currentThread().getId();
    threadTrack.endSection();
    depth--;
    return this;
  }

  @Override
  public TimingMerger beginMerger(String title, int numberOfThreads) {
    return EmptyTimingMerger.get();
  }

  @Override
  public void report() {
    throw new Unreachable();
  }

  @Override
  public Timing endAll() {
    throw new Unreachable();
  }
}
