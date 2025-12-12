// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.errors.Unimplemented;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;

public class ProgramResourceProviderUtils {
  private static class SingleProgramResourceProvider implements ProgramResourceProvider {

    private final ProgramResource programResource;

    SingleProgramResourceProvider(ProgramResource programResource) {
      this.programResource = programResource;
    }

    @Override
    public Collection<ProgramResource> getProgramResources() {
      return Collections.singletonList(programResource);
    }

    @Override
    public void getProgramResources(Consumer<ProgramResource> consumer) {
      consumer.accept(programResource);
    }
  }

  public static void forEachProgramResourceCompat(
      ProgramResourceProvider programResourceProvider, Consumer<ProgramResource> fn)
      throws ResourceException {
    forEachProgramResourceCompat(programResourceProvider, fn, emptyConsumer());
  }

  public static void forEachProgramResourceCompat(
      ProgramResourceProvider programResourceProvider,
      Consumer<ProgramResource> fn,
      Consumer<ProgramResourceProvider> unimplementedHandler)
      throws ResourceException {
    try {
      programResourceProvider.getProgramResources(fn);
    } catch (Unimplemented e) {
      unimplementedHandler.accept(programResourceProvider);
      programResourceProvider.getProgramResources().forEach(fn);
    }
  }

  public static ProgramResourceProvider createSingleResourceProvider(
      ProgramResource programResource) {
    return new SingleProgramResourceProvider(programResource);
  }
}
