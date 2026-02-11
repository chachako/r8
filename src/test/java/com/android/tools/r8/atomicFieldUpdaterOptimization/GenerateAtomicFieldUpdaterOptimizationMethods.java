// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.atomicFieldUpdaterOptimization;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.TestDataSourceSet;
import com.android.tools.r8.atomicFieldUpdaterOptimization.AtomicFieldUpdaterOptimizationMethods.UnsafeStub;
import com.android.tools.r8.cfmethodgeneration.InstructionTypeMapper;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenerateAtomicFieldUpdaterOptimizationMethods extends MethodGenerationBase {

  private final DexType GENERATED_TYPE =
      factory.createType(
          DescriptorUtils.javaClassToDescriptor(
              com.android.tools.r8.ir.synthetic.AtomicFieldUpdaterOptimizationMethods.class));

  private final List<Class<?>> METHOD_TEMPLATE_CLASSES =
      ImmutableList.of(AtomicFieldUpdaterOptimizationMethods.class);

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenerateAtomicFieldUpdaterOptimizationMethods(TestParameters parameters) {
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
    return 2026;
  }

  @Override
  protected DexEncodedField getField(DexEncodedField field) {
    if (field.getType().getTypeName().endsWith("UnsafeStub")) {
      return DexEncodedField.builder(field)
          .setField(factory.createField(field.getHolderType(), factory.unsafeType, field.getName()))
          .disableAndroidApiLevelCheck()
          .build();
    }
    return field;
  }

  @Override
  protected CfCode getCode(String holderName, String methodName, CfCode code) {
    // Rewrite UnsafeStub into sun.misc.Unsafe.
    InstructionTypeMapper instructionTypeMapper =
        new InstructionTypeMapper(
            factory,
            ImmutableMap.of(
                factory.createType(DescriptorUtils.javaClassToDescriptor(UnsafeStub.class)),
                factory.unsafeType),
            Function.identity());
    code.setInstructions(
        code.getInstructions().stream()
            .map(instructionTypeMapper::rewriteInstruction)
            .collect(Collectors.toList()));
    return code;
  }

  @Override
  protected DexEncodedMethod mapMethod(DexEncodedMethod method) {
    DexType holder = method.getHolderType();
    DexType unsafeStub =
        factory.createType(DescriptorUtils.javaClassToDescriptor(UnsafeStub.class));
    DexProto originalProto = method.getProto();
    DexType returnType;
    if (originalProto.getReturnType().isIdenticalTo(unsafeStub)) {
      returnType = factory.unsafeType;
    } else {
      returnType = originalProto.getReturnType();
    }
    DexTypeList paramTypes =
        originalProto.parameters.map(
            param -> {
              if (param.isIdenticalTo(unsafeStub)) {
                return factory.unsafeType;
              } else {
                return param;
              }
            });
    DexProto proto = factory.createProto(returnType, paramTypes);
    return DexEncodedMethod.syntheticBuilder()
        .setMethod(factory.createMethod(holder, proto, method.getName()))
        .build();
  }

  @Test
  public void testGenerated() throws Exception {
    ArrayList<Class<?>> sorted = new ArrayList<>(getMethodTemplateClasses());
    sorted.sort(Comparator.comparing(Class::getTypeName));
    assertEquals("Classes should be listed in sorted order", sorted, getMethodTemplateClasses());
    assertEquals(
        FileUtils.readTextFile(getGeneratedFile(), StandardCharsets.UTF_8), generateMethods());
  }

  public static void main(String[] args) throws Exception {
    setUpSystemPropertiesForMain(
        TestDataSourceSet.TESTS_JAVA_8, TestDataSourceSet.TESTBASE_DATA_LOCATION);
    new GenerateAtomicFieldUpdaterOptimizationMethods(null).generateMethodsAndWriteThemToFile();
  }
}
