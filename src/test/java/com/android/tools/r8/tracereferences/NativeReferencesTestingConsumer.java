// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.MethodOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class NativeReferencesTestingConsumer implements TraceReferencesNativeReferencesConsumer {

  List<Pair<String, MethodOrigin>> loadLibraryKnown = new ArrayList<>();
  List<MethodOrigin> loadLibraryAny = new ArrayList<>();
  List<Pair<String, MethodOrigin>> loadKnown = new ArrayList<>();
  List<MethodOrigin> loadAny = new ArrayList<>();
  List<MethodReference> nativeMethods = new ArrayList<>();

  @Override
  public void acceptLoadLibrary(String name, MethodOrigin origin, DiagnosticsHandler handler) {
    loadLibraryKnown.add(new Pair<>(name, origin));
  }

  @Override
  public void acceptLoadLibraryAny(MethodOrigin origin, DiagnosticsHandler handler) {
    loadLibraryAny.add(origin);
  }

  @Override
  public void acceptLoad(String name, MethodOrigin origin, DiagnosticsHandler handler) {
    loadKnown.add(new Pair<>(name, origin));
  }

  @Override
  public void acceptLoadAny(MethodOrigin origin, DiagnosticsHandler handler) {
    loadAny.add(origin);
  }

  @Override
  public void acceptNativeMethod(MethodReference methodReference, DiagnosticsHandler handler) {
    nativeMethods.add(methodReference);
  }

  public NativeReferencesTestingConsumer expectLoadLibrary(String name, Origin origin) {
    for (int i = 0; i < loadLibraryKnown.size(); i++) {
      Pair<String, MethodOrigin> element = loadLibraryKnown.get(i);
      if (element.getFirst().equals(name) && element.getSecond().equals(origin)) {
        loadLibraryKnown.remove(i);
        return this;
      }
    }
    fail(
        "Expected to contain "
            + name
            + " with origin "
            + origin
            + ", but did not. Content was: "
            + loadLibraryKnown.stream()
                .map(o -> o.getFirst() + ": " + o.getSecond())
                .collect(Collectors.joining(", ")));
    return this;
  }

  public NativeReferencesTestingConsumer expectLoadLibraryAny(Origin origin) {
    for (int i = 0; i < loadLibraryAny.size(); i++) {
      if (loadLibraryAny.get(i).equals(origin)) {
        loadLibraryAny.remove(i);
        return this;
      }
    }
    fail("Expected to contain " + origin + ", but did not.");
    return this;
  }

  public NativeReferencesTestingConsumer expectLoad(String name, Origin origin) {
    for (int i = 0; i < loadKnown.size(); i++) {
      Pair<String, MethodOrigin> element = loadKnown.get(i);
      if (element.getFirst().equals(name) && element.getSecond().equals(origin)) {
        loadKnown.remove(i);
        return this;
      }
    }
    fail("Expected to contain " + name + " with origin " + origin + ", but did not.");
    return this;
  }

  public NativeReferencesTestingConsumer expectLoadAny(Origin origin) {
    for (int i = 0; i < loadAny.size(); i++) {
      if (loadAny.get(i).equals(origin)) {
        loadAny.remove(i);
        return this;
      }
    }
    fail("Expected to contain " + origin + ", but did not.");
    return this;
  }

  public NativeReferencesTestingConsumer expectNativeMethod(MethodReference methodReference) {
    for (int i = 0; i < nativeMethods.size(); i++) {
      if (nativeMethods.get(i).equals(methodReference)) {
        nativeMethods.remove(i);
        return this;
      }
    }
    fail("Expected to contain " + methodReference + ", but did not.");
    return this;
  }

  public NativeReferencesTestingConsumer thatsAll() {
    assertTrue(
        "Expected no more load library calls traced.",
        loadLibraryKnown.isEmpty()
            && loadLibraryAny.isEmpty()
            && loadKnown.isEmpty()
            && loadAny.isEmpty());
    return this;
  }
}
