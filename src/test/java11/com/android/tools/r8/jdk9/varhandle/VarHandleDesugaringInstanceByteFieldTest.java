// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk9.varhandle;

import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class VarHandleDesugaringInstanceByteFieldTest
    extends varhandle.VarHandleDesugaringTestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("testGet", "0", "1", "2", "testCompareAndSet", "0", "1", "2", "3", "4");

  private static final String MAIN_CLASS = InstanceByteField.class.getTypeName();

  @Override
  protected String getMainClass() {
    return MAIN_CLASS;
  }

  @Override
  protected List<String> getKeepRules() {
    return ImmutableList.of("-keep class " + getMainClass() + "{ <fields>; }");
  }

  @Override
  protected List<Class<?>> getProgramClasses() {
    return ImmutableList.of(InstanceByteField.class);
  }

  @Override
  protected String getExpectedOutputForReferenceImplementation() {
    return EXPECTED_OUTPUT;
  }

  @Override
  protected String getExpectedOutputForDesugaringImplementation() {
    return StringUtils.lines("Got UnsupportedOperationException");
  }
}
