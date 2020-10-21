// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.TypeReference;

@Keep
public interface RetracedField extends RetracedClassMember {

  boolean isUnknown();

  boolean isKnown();

  KnownRetracedField asKnown();

  String getFieldName();

  @Keep
  interface KnownRetracedField extends RetracedField {

    TypeReference getFieldType();

    FieldReference getFieldReference();
  }
}
