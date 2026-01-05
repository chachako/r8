// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.rootset;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ImmediateAppSubtypingInfo;
import com.android.tools.r8.shaking.AnnotationMatchResult;
import com.android.tools.r8.shaking.Enqueuer;

public class ConsequentRootSetBuilder extends RootSetBuilder {

  private final Enqueuer enqueuer;

  ConsequentRootSetBuilder(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      ImmediateAppSubtypingInfo subtypingInfo) {
    super(appView, subtypingInfo, null);
    this.enqueuer = enqueuer;
  }

  @Override
  public void handleMatchedAnnotation(AnnotationMatchResult annotationMatchResult) {
    if (enqueuer.getMode().isInitialTreeShaking()
        && annotationMatchResult.isConcreteAnnotationMatchResult()) {
      enqueuer.retainAnnotationForFinalTreeShaking(
          annotationMatchResult.asConcreteAnnotationMatchResult().getMatchedAnnotations());
    }
  }

  @Override
  boolean isConsequentRootSetBuilder() {
    return true;
  }
}
