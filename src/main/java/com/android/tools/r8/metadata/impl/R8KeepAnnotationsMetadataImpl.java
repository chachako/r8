// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8KeepAnnotationsMetadata;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8KeepAnnotationsMetadataImpl implements R8KeepAnnotationsMetadata {

  @Expose
  @SerializedName("numberOfKeepAnnotations")
  private final int numberOfKeepAnnotations;

  public R8KeepAnnotationsMetadataImpl(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.numberOfKeepAnnotations =
        appView.app().isDirect() ? appView.app().asDirect().getKeepDeclarations().size() : 0;
  }

  @Override
  public int getNumberOfKeepAnnotations() {
    return numberOfKeepAnnotations;
  }
}
