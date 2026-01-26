// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ThrowNullCode;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineOutlineWithAssumeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep class " + Main.class.getTypeName() + " { void sink(...); }")
        .addOptionsModification(
            options -> {
              options.outline.threshold = 2;
              options.outline.minSize = 2;
              options.getTestingOptions().enqueuerInspector =
                  (appView, mode) -> {
                    // Replace the body of otherOutlineCallSite() by `throw null` after the second
                    // round of tree shaking so that the outline gets single call inlined in the
                    // backed.
                    if (mode.isFinalTreeShaking()) {
                      DexItemFactory factory = appView.dexItemFactory();
                      DexProgramClass mainClass =
                          appView
                              .definitionFor(factory.createType(descriptor(Main.class)))
                              .asProgramClass();
                      DexEncodedMethod otherOutlineCallSiteMethod =
                          mainClass.lookupMethod(
                              m -> m.getName().isEqualTo("otherOutlineCallSite"));
                      otherOutlineCallSiteMethod.setCode(ThrowNullCode.get());
                    }
                  };
            })
        .enableInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      outlineCallSite();
      try {
        otherOutlineCallSite();
      } catch (NullPointerException e) {
        System.out.println("!");
      }
    }

    @NeverInline
    static void outlineCallSite() {
      String hello = System.currentTimeMillis() > 0 ? "Hello" : null;
      // Perform a null check so that we won't materialize an AssumeNotNull instruction after the
      // call to hello().
      if (hello != null) {
        Greeter.hello(hello);
        // <-- No AssumeNotNull instruction.
        Greeter.world();
      }
    }

    @NeverInline
    static void otherOutlineCallSite() {
      String hello = System.currentTimeMillis() > 0 ? "Hello" : null;
      Greeter.hello(hello);
      // <-- AssumeNotNull instruction, since the normal return of hello() ensures hello != null.
      Greeter.world();
    }
  }

  static class Greeter {

    @NeverInline
    static void hello(String hello) {
      Objects.requireNonNull(hello);
      System.out.print(hello);
    }

    @NeverInline
    static void world() {
      System.out.print(", world");
    }
  }
}
