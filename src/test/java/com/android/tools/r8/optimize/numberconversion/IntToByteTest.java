// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.numberconversion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.constant.SparseConditionalConstantPropagation;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class IntToByteTest extends TestBase {

  @Parameterized.Parameter(0)
  public int input;

  @Parameterized.Parameters(name = "{0}")
  public static Integer[] data() {
    return new Integer[] {
      128, 65408, -65408, 42, -32, 0,
    };
  }

  @Test
  public void test() throws Exception {
    byte[] testClass = TestDump.dump(input, (byte) input);
    AndroidApp androidApp = AndroidApp.builder().addClassProgramData(testClass).build();
    AppView<AppInfoWithClassHierarchy> appView = computeAppViewWithClassHierarchy(androidApp);
    IRCode ir = new CodeInspector(androidApp).clazz(TestDump.CLASS_NAME).mainMethod().buildIR();
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
    assertEquals(ValueType.INT, cst.outType());
    assertEquals(0, cst.getRawValue());
  }

  public static class TestDump implements Opcodes {

    public static final String CLASS_NAME = "TestClass";

    /**
     * Constructs the below method where {@code [..]} describes meta values.
     *
     * <pre>{@code
     * public class [CLASS_NAME] {
     *     public static void main(String[] args) {
     *         System.out.println(((byte) [input]) - [expected]);
     *     }
     * }
     * }</pre>
     *
     * Subtraction is used to verify the internal state of constants rather than just the output
     * instructions. This avoids the situation where the input is not truncated during optimization,
     * but is still truncated in the final instructions because of typed extraction.
     */
    public static byte[] dump(int input, byte expected) throws Exception {

      ClassWriter classWriter = new ClassWriter(0);

      classWriter.visit(V1_8, ACC_PUBLIC, CLASS_NAME, null, "java/lang/Object", null);
      MethodVisitor methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V", null, null);
      methodVisitor.visitCode();
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitLdcInsn(input);
      methodVisitor.visitInsn(I2B);
      methodVisitor.visitIntInsn(BIPUSH, expected);
      methodVisitor.visitInsn(ISUB);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 1);
      methodVisitor.visitEnd();
      classWriter.visitEnd();

      return classWriter.toByteArray();
    }
  }
}
