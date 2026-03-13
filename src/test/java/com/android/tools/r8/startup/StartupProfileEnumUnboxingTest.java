// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StartupProfileEnumUnboxingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    // Regression test for assertion error caused by rewriting MyEnum -> int in the startup profile.
    testForR8(Backend.DEX)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .apply(
            testBuilder ->
                StartupTestingUtils.addStartupProfile(
                    testBuilder,
                    ImmutableList.of(
                        ExternalStartupClass.builder()
                            .setClassReference(Reference.classFromClass(MyEnum.class))
                            .build())))
        .setMinApi(AndroidApiLevel.getDefault())
        .compile();
  }

  static class Main {

    public static void main(String[] args) {
      MyEnum e = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      System.out.println(e.ordinal());
    }
  }

  enum MyEnum {
    A,
    B
  }
}
