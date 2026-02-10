// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.TestDataSourceSet;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.backports.BackportMethodsStub.AndroidOsBuildStub;
import com.android.tools.r8.ir.desugar.backports.BackportMethodsStub.AndroidOsBuildVersionStub;
import com.android.tools.r8.ir.desugar.backports.BackportMethodsStub.LongStub;
import com.android.tools.r8.ir.desugar.backports.BackportMethodsStub.UnsafeStub;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateBackportMethods extends MethodGenerationBase {

  private final DexType GENERATED_TYPE =
      factory.createType("Lcom/android/tools/r8/ir/desugar/backports/BackportedMethods;");
  private final List<Class<?>> METHOD_TEMPLATE_CLASSES =
      ImmutableList.of(
          AndroidOsBuildMethods.class,
          AndroidOsBuildVersionMethods.class,
          ArraysMethods.class,
          AssertionErrorMethods.class,
          AtomicReferenceArrayMethods.class,
          AtomicReferenceFieldUpdaterMethods.class,
          AtomicReferenceMethods.class,
          BigDecimalMethods.class,
          BooleanMethods.class,
          ByteMethods.class,
          CharSequenceMethods.class,
          CharacterMethods.class,
          CloseResourceMethod.class,
          CollectionMethods.class,
          CollectionsMethods.class,
          DoubleMethods.class,
          DurationMethods.class,
          ExecutorServiceMethods.class,
          FloatMethods.class,
          IntegerMethods.class,
          LongMethods.class,
          MathMethods.class,
          MethodMethods.class,
          ObjectsMethods.class,
          OptionalMethods.class,
          PredicateMethods.class,
          ShortMethods.class,
          StreamMethods.class,
          StringMethods.class,
          ThrowableMethods.class,
          UnsafeMethods.class);

  private Map<DexType, DexType> stubMap;

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenerateBackportMethods(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Override
  protected DexType getGeneratedType() {
    return GENERATED_TYPE;
  }

  @Override
  protected List<Class<?>> getMethodTemplateClasses() {
    return METHOD_TEMPLATE_CLASSES;
  }

  @Override
  protected int getYear() {
    return 2021;
  }

  @Override
  protected String generateMethods() throws IOException {
    stubMap =
        ImmutableMap.of(
            classToType(LongStub.class),
            factory.boxedLongType,
            classToType(UnsafeStub.class),
            factory.unsafeType,
            classToType(AndroidOsBuildStub.class),
            factory.androidOsBuildType,
            classToType(AndroidOsBuildVersionStub.class),
            factory.androidOsBuildVersionType);
    return super.generateMethods();
  }

  private DexType classToType(Class<?> clazz) {
    return factory.createType(DescriptorUtils.javaClassToDescriptor(clazz));
  }

  private CfInstruction rewrite(CfInstruction instruction) {
    if (instruction.isInvoke()
        && stubMap.containsKey(instruction.asInvoke().getMethod().getHolderType())) {
      CfInvoke invoke = instruction.asInvoke();
      return new CfInvoke(
          invoke.getOpcode(),
          factory.createMethod(
              stubMap.get(invoke.getMethod().getHolderType()),
              invoke.getMethod().getProto(),
              invoke.getMethod().getName()),
          invoke.isInterface());
    }
    if (instruction.isStaticFieldGet()
        && stubMap.containsKey(instruction.asFieldInstruction().getField().getHolderType())) {
      CfStaticFieldRead fieldGet = instruction.asStaticFieldGet();
      return new CfStaticFieldRead(
          factory.createField(
              stubMap.get(fieldGet.getField().getHolderType()),
              fieldGet.getField().getType(),
              fieldGet.getField().getName()));
    }
    if (instruction.isFrame()) {
      return instruction.asFrame().mapReferenceTypes(type -> stubMap.getOrDefault(type, type));
    }
    return instruction;
  }

  @Override
  protected CfCode getCode(String holderName, String methodName, CfCode code) {
    if (methodName.endsWith("Stub")) {
      // Don't include stubs targeted only for rewriting in the generated code.
      return null;
    }
    code.setInstructions(
        code.getInstructions().stream().map(this::rewrite).collect(Collectors.toList()));
    return code;
  }

  @Test
  public void testBackportsGenerated() throws Exception {
    ArrayList<Class<?>> sorted = new ArrayList<>(getMethodTemplateClasses());
    sorted.sort(Comparator.comparing(Class::getTypeName));
    assertEquals("Classes should be listed in sorted order", sorted, getMethodTemplateClasses());
    assertEquals(
        FileUtils.readTextFile(getGeneratedFile(), StandardCharsets.UTF_8), generateMethods());
  }

  public static void main(String[] args) throws Exception {
    setUpSystemPropertiesForMain(
        TestDataSourceSet.TESTS_JAVA_8, TestDataSourceSet.TESTBASE_DATA_LOCATION);
    new GenerateBackportMethods(null).generateMethodsAndWriteThemToFile();
  }
}
