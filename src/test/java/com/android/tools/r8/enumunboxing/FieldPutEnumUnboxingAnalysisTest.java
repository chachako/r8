// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldPutEnumUnboxingAnalysisTest extends EnumUnboxingTestBase {

  private static final Class<?>[] INPUTS =
      new Class<?>[] {InstanceFieldPut.class, StaticFieldPut.class};

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final boolean enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public FieldPutEnumUnboxingAnalysisTest(
      TestParameters parameters, boolean enumValueOptimization, boolean enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addInnerClasses(FieldPutEnumUnboxingAnalysisTest.class)
            .addKeepMainRules(INPUTS)
            .addKeepRules(enumKeepRules ? KEEP_ENUM : "")
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .allowDiagnosticInfoMessages()
            .enableInliningAnnotations()
            .setMinApi(parameters.getApiLevel())
            .noMinification()
            .compile()
            .inspect(
                i -> {
                  assertEquals(
                      1, i.clazz(InstanceFieldPut.class).getDexClass().instanceFields().size());
                  assertEquals(
                      1, i.clazz(StaticFieldPut.class).getDexClass().staticFields().size());
                });

    for (Class<?> input : INPUTS) {
      R8TestRunResult run =
          compile
              .inspectDiagnosticMessages(
                  m -> assertEnumIsUnboxed(input.getDeclaredClasses()[0], input.getSimpleName(), m))
              .run(parameters.getRuntime(), input)
              .assertSuccess();
      assertLines2By2Correct(run.getStdOut());
    }
  }

  static class InstanceFieldPut {

    enum MyEnum {
      A,
      B,
      C
    }

    MyEnum e;

    public static void main(String[] args) {
      InstanceFieldPut fieldPut = new InstanceFieldPut();
      fieldPut.setA();
      System.out.println(fieldPut.e.ordinal());
      System.out.println(0);
      fieldPut.setB();
      System.out.println(fieldPut.e.ordinal());
      System.out.println(1);
    }

    void setA() {
      e = MyEnum.A;
    }

    void setB() {
      e = MyEnum.B;
    }
  }

  static class StaticFieldPut {

    enum MyEnum {
      A,
      B,
      C
    }

    static MyEnum e;

    public static void main(String[] args) {
      setA();
      System.out.println(StaticFieldPut.e.ordinal());
      System.out.println(0);
      setB();
      System.out.println(StaticFieldPut.e.ordinal());
      System.out.println(1);
    }

    @NeverInline
    static void setA() {
      StaticFieldPut.e = MyEnum.A;
    }

    @NeverInline
    static void setB() {
      StaticFieldPut.e = MyEnum.B;
    }
  }
}
