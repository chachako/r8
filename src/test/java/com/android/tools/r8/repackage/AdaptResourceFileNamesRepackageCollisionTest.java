// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AdaptResourceFileNamesRepackageCollisionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .addDataResources(
            DataEntryResource.fromString("pkg1/build.properties", Origin.unknown(), ""),
            DataEntryResource.fromString("pkg2/build.properties", Origin.unknown(), ""))
        .addKeepMainRule(Main.class)
        .addKeepRules("-adaptresourcefilenames */build.properties")
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              // Class A should be repackaged to the default package "".
              ClassSubject aClass = inspector.clazz("pkg1.A");
              assertThat(aClass, isPresent());
              assertEquals("", aClass.getDexProgramClass().getType().getPackageName());

              // Due to the collision between pkg1/build.properties and pkg2/build.properties,
              // class B should not be repackaged to the default package "".
              // We fallback to -flattenpackagehierarchy and move it to package "a".
              ClassSubject bClass = inspector.clazz("pkg2.B");
              assertThat(bClass, isPresent());
              assertEquals("a", bClass.getDexProgramClass().getType().getPackageName());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private List<byte[]> getProgramClassFileData() {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(descriptor(A.class), "Lpkg1/A;")
            .replaceClassDescriptorInMethodInstructions(descriptor(B.class), "Lpkg2/B;")
            .transform(),
        transformer(A.class).setClassDescriptor("Lpkg1/A;").transform(),
        transformer(B.class).setClassDescriptor("Lpkg2/B;").transform());
  }

  static class Main {

    public static void main(String[] args) {
      A.hello();
      B.world();
    }
  }

  @NoHorizontalClassMerging
  public static class /*pkg1.*/ A {

    @NeverInline
    public static void hello() {
      System.out.print("Hello");
    }
  }

  @NoHorizontalClassMerging
  public static class /*pkg2.*/ B {

    @NeverInline
    public static void world() {
      System.out.println(", world!");
    }
  }
}
