// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8DexFileMetadata;
import com.android.tools.r8.metadata.R8FeatureSplitMetadata;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.List;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8FeatureSplitMetadataImpl implements R8FeatureSplitMetadata {

  @Expose
  @SerializedName("dexFiles")
  private final List<R8DexFileMetadata> dexFilesMetadata;

  public R8FeatureSplitMetadataImpl(List<R8DexFileMetadata> dexFilesMetadata) {
    this.dexFilesMetadata = dexFilesMetadata;
  }

  @Override
  public List<R8DexFileMetadata> getDexFilesMetadata() {
    return dexFilesMetadata;
  }
}
