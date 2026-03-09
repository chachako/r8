// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk9.varhandle;

import com.android.tools.r8.jdk9.varhandle.util.WithPrivateFields;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MethodHandlesPrivateLookupInTest extends varhandle.VarHandleDesugaringTestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("0", "0", "null");
  private static final String MAIN_CLASS = MethodHandlesPrivateLookupIn.class.getTypeName();

  @Override
  protected String getMainClass() {
    return MAIN_CLASS;
  }

  @Override
  protected List<String> getKeepRules() {
    return ImmutableList.of(
        "-keep class " + getMainClass() + "{ <fields>; }",
        "-keep class " + getClass().getPackageName() + ".util.WithPrivateFields { <fields>; }");
  }

  @Override
  protected List<Class<?>> getProgramClasses() {
    return ImmutableList.of(MethodHandlesPrivateLookupIn.class, WithPrivateFields.class);
  }

  @Override
  protected String getExpectedOutputForReferenceImplementation() {
    return EXPECTED_OUTPUT;
  }

  @Override
  protected String getExpectedOutputForDesugaringImplementation() {
    // TODO(b/247076137): Desugar implementation allows VarHandle on private fields.
    return StringUtils.lines(
        "Unexpected success", "0", "Unexpected success", "0", "Unexpected success", "null");
  }
}
