// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.timing;

import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import androidx.tracing.CounterTrack;
import androidx.tracing.EventMetadataCloseable;
import androidx.tracing.ProcessTrack;
import androidx.tracing.PropagationUnsupportedToken;
import androidx.tracing.ThreadTrack;
import androidx.tracing.TraceContext;
import androidx.tracing.TraceDriver;
import androidx.tracing.wire.TraceDriverUtils;
import androidx.tracing.wire.TraceSink;
import androidx.tracing.wire.TraceSinkUtils;
import com.android.tools.r8.utils.InternalOptions;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import kotlinx.coroutines.Dispatchers;
import okio.BufferedSink;
import okio.Okio;

public class PerfettoTiming extends TimingImplBase {
  // The API now allows you to specify the category and in the future let you conditionally turn on
  // categories.
  private static final String TRACE_CATEGORY = "R8";
  private final TraceDriver traceDriver;
  private final ProcessTrack processTrack;
  private final ThreadTrack threadTrack;
  private int depth = 0;
  private CounterTrack memoryTrack;
  private Future<Void> memoryTracker;
  private volatile boolean memoryTrackerActive = true;

  public PerfettoTiming(String title, InternalOptions options, ExecutorService executorService) {
    int sequenceId = 1;
    TraceSink sink;
    if (options.perfettoTraceDumpFile != null) {
      BufferedSink bufferedSink;
      try {
        File file = new File(options.perfettoTraceDumpFile);
        bufferedSink = Okio.buffer(Okio.appendingSink(file));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      sink = new TraceSink(sequenceId, bufferedSink, Dispatchers.getIO());
    } else {
      File directory = new File(options.perfettoTraceDumpDirectory);
      sink = TraceSinkUtils.TraceSink(directory, sequenceId);
    }
    // Populates the process track correctly.
    traceDriver = TraceDriverUtils.TraceDriver(sink);
    TraceContext traceContext = traceDriver.getContext();
    processTrack = traceContext.getProcess();
    int mainThreadId = (int) Thread.currentThread().getId();
    threadTrack = processTrack.getOrCreateThreadTrack(mainThreadId, "Main thread");
    begin(title);
    // Memory tracking requires an executor service.
    if (executorService != null) {
      memoryTrack = processTrack.getOrCreateCounterTrack("Memory");
      memoryTracker =
          executorService.submit(
              () -> {
                while (true) {
                  // Check the memoryCounterActive flag every 250ms.
                  for (int i = 0; i < 4; i++) {
                    Thread.sleep(250);
                    if (!memoryTrackerActive) {
                      return null;
                    }
                  }
                  // Update the memory counter every 1s.
                  memoryTrack.setCounter(
                      Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
                }
              });
    }
  }

  @Override
  public Timing createThreadTiming(String title, InternalOptions options) {
    int threadId = (int) Thread.currentThread().getId();
    ThreadTrack threadTrack = processTrack.getOrCreateThreadTrack(threadId, "Worker");
    return new PerfettoThreadTiming(threadTrack).begin(title);
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
    end();
    assert depth == 0 : depth;
    awaitMemoryTracker();
    traceDriver.close();
  }

  @Override
  public Timing endAll() {
    for (int i = 1; i < depth; i++) {
      end();
    }
    return this;
  }

  private void awaitMemoryTracker() {
    if (memoryTracker != null) {
      // Signal to the memory tracker to stop.
      memoryTrackerActive = false;
      // Await the memory tracker.
      try {
        memoryTracker.get();
      } catch (InterruptedException e) {
        throw new RuntimeException("Interrupted while waiting for future.", e);
      } catch (ExecutionException e) {
        throw unwrapExecutionException(e);
      }
      memoryTrack = null;
      memoryTracker = null;
    }
  }
}
