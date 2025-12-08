// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.numberconversion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.constant.SparseConditionalConstantPropagation;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class NumberConversionTestBase extends TestBase implements Opcodes {

  /**
   * Tests that {@code computation} is reduced to a single constant of type {@code computationType}
   * represented by long value {@code expectedRawValue}.
   *
   * @param computation should leave a single value on the stack and not use more than 10 max stack
   *     size.
   * @param computationType the type of the value of {@code computation}.
   * @param expectedRawValue the expected constant after optimization.
   */
  protected void testConversion(
      Consumer<MethodVisitor> computation, NumericType computationType, long expectedRawValue)
      throws Exception {
    ValueType computationValueType = ValueType.fromNumericType(computationType);
    byte[] testClass = dump(computation, computationValueType);
    AndroidApp androidApp = AndroidApp.builder().addClassProgramData(testClass).build();
    AppView<?> appView = computeAppView(androidApp);
    DexItemFactory itemFactory = appView.dexItemFactory();
    DexMethod mainMethod =
        itemFactory.createMethod(
            itemFactory.createType(DescriptorUtils.javaTypeToDescriptor(CLASS_NAME)),
            itemFactory.createProto(itemFactory.voidType, itemFactory.stringArrayType),
            "main");
    IRCode ir =
        appView
            .definitionFor(mainMethod)
            .asProgramMethod()
            .buildIR(appView, MethodConversionOptions.nonConverting());
    assertTrue(ir.streamInstructions().anyMatch(Instruction::isNumberConversion));

    // Run optimization and dead code elimination.
    CodeRewriterResult result =
        new SparseConditionalConstantPropagation(appView).run(ir, null, null, Timing.empty());
    assertEquals(OptionalBool.TRUE, result.hasChanged());
    new DeadCodeRemover(appView).run(ir, Timing.empty());

    // Assertions.
    ImmutableList<ConstNumber> constNumbers =
        ImmutableList.copyOf(
            ir.streamInstructions()
                .filter(Instruction::isConstNumber)
                .map(Instruction::asConstNumber)
                .iterator());
    assertEquals(1, constNumbers.size());
    ConstNumber cst = constNumbers.get(0);
    assertEquals(computationValueType, cst.outType());
    assertEquals(expectedRawValue, cst.getRawValue());
  }

  private static final String CLASS_NAME = "TestClass";

  /**
   * Constructs the below method where {@code [..]} describes meta values.
   *
   * <pre>{@code
   * public class [CLASS_NAME] {
   *     public static void main(String[] args) {
   *         System.out.println([computation]);
   *     }
   * }
   * }</pre>
   *
   * @param computation should leave a single value on the stack and not use more than 10 max stack
   *     size.
   * @param computationType the type of the value returned by {@code computation}.
   */
  private static byte[] dump(Consumer<MethodVisitor> computation, ValueType computationType) {

    char computationTypeDescriptor;
    switch (computationType) {
      case INT:
        computationTypeDescriptor = 'I';
        break;
      case FLOAT:
        computationTypeDescriptor = 'F';
        break;
      case LONG:
        computationTypeDescriptor = 'J';
        break;
      case DOUBLE:
        computationTypeDescriptor = 'D';
        break;
      default:
        throw new IllegalArgumentException("No code gen for computationType " + computationType);
    }

    ClassWriter classWriter = new ClassWriter(0);

    classWriter.visit(V1_8, ACC_PUBLIC, CLASS_NAME, null, "java/lang/Object", null);
    MethodVisitor methodVisitor =
        classWriter.visitMethod(
            ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
    methodVisitor.visitCode();
    methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    computation.accept(methodVisitor);
    methodVisitor.visitMethodInsn(
        INVOKEVIRTUAL,
        "java/io/PrintStream",
        "println",
        "(" + computationTypeDescriptor + ")V",
        false);
    methodVisitor.visitInsn(RETURN);
    methodVisitor.visitMaxs(1 + 10, 1);
    methodVisitor.visitEnd();
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
