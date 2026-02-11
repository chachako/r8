// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See GenerateAtomicFieldUpdaterOptimizationMethods.java.
// ***********************************************************************************

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import java.util.ArrayDeque;
import java.util.Arrays;

public final class AtomicFieldUpdaterOptimizationMethods {

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    factory.createSynthesizedType("Ljava/lang/Exception;");
    factory.createSynthesizedType("Ljava/lang/NoSuchFieldException;");
    factory.createSynthesizedType("Ljava/lang/RuntimeException;");
    factory.createSynthesizedType("Ljava/lang/UnsupportedOperationException;");
    factory.createSynthesizedType("Ljava/lang/reflect/Field;");
    factory.createSynthesizedType("Ljava/lang/reflect/Modifier;");
    factory.createSynthesizedType("Lsun/misc/Unsafe;");
    factory.createSynthesizedType("[Ljava/lang/reflect/Field;");
  }

  public static CfCode AtomicFieldUpdaterOptimizationMethods_getAndSet(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    return new CfCode(
        method.holder,
        6,
        6,
        ImmutableList.of(
            label0,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Lsun/misc/Unsafe;")),
                      FrameType.initializedNonNullReference(factory.objectType),
                      FrameType.longType(),
                      FrameType.longHighType(),
                      FrameType.initializedNonNullReference(factory.objectType)
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.LONG, 2),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Lsun/misc/Unsafe;"),
                    factory.createProto(factory.objectType, factory.objectType, factory.longType),
                    factory.createString("getObjectVolatile")),
                false),
            new CfStore(ValueType.OBJECT, 5),
            label1,
            new CfLoad(ValueType.OBJECT, 0),
            new CfLoad(ValueType.OBJECT, 1),
            new CfLoad(ValueType.LONG, 2),
            new CfLoad(ValueType.OBJECT, 5),
            new CfLoad(ValueType.OBJECT, 4),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Lsun/misc/Unsafe;"),
                    factory.createProto(
                        factory.booleanType,
                        factory.objectType,
                        factory.longType,
                        factory.objectType,
                        factory.objectType),
                    factory.createString("compareAndSwapObject")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label0),
            label2,
            new CfLoad(ValueType.OBJECT, 5),
            new CfReturn(ValueType.OBJECT),
            label3),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static CfCode AtomicFieldUpdaterOptimizationMethods_getUnsafe(
      DexItemFactory factory, DexMethod method) {
    CfLabel label0 = new CfLabel();
    CfLabel label1 = new CfLabel();
    CfLabel label2 = new CfLabel();
    CfLabel label3 = new CfLabel();
    CfLabel label4 = new CfLabel();
    CfLabel label5 = new CfLabel();
    CfLabel label6 = new CfLabel();
    CfLabel label7 = new CfLabel();
    CfLabel label8 = new CfLabel();
    CfLabel label9 = new CfLabel();
    CfLabel label10 = new CfLabel();
    CfLabel label11 = new CfLabel();
    CfLabel label12 = new CfLabel();
    CfLabel label13 = new CfLabel();
    CfLabel label14 = new CfLabel();
    CfLabel label15 = new CfLabel();
    CfLabel label16 = new CfLabel();
    CfLabel label17 = new CfLabel();
    CfLabel label18 = new CfLabel();
    return new CfCode(
        method.holder,
        4,
        6,
        ImmutableList.of(
            label0,
            new CfConstNull(),
            new CfStore(ValueType.OBJECT, 0),
            label1,
            new CfConstClass(factory.createType("Lsun/misc/Unsafe;")),
            new CfConstString(factory.createString("theUnsafe")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(
                        factory.createType("Ljava/lang/reflect/Field;"), factory.stringType),
                    factory.createString("getDeclaredField")),
                false),
            new CfStore(ValueType.OBJECT, 0),
            label2,
            new CfGoto(label13),
            label3,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/Field;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/NoSuchFieldException;"))))),
            new CfStore(ValueType.OBJECT, 1),
            label4,
            new CfConstClass(factory.createType("Lsun/misc/Unsafe;")),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.createType("[Ljava/lang/reflect/Field;")),
                    factory.createString("getDeclaredFields")),
                false),
            new CfStore(ValueType.OBJECT, 2),
            new CfLoad(ValueType.OBJECT, 2),
            new CfArrayLength(),
            new CfStore(ValueType.INT, 3),
            new CfConstNumber(0, ValueType.INT),
            new CfStore(ValueType.INT, 4),
            label5,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/Field;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/NoSuchFieldException;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/reflect/Field;")),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfLoad(ValueType.INT, 4),
            new CfLoad(ValueType.INT, 3),
            new CfIfCmp(IfType.GE, ValueType.INT, label11),
            new CfLoad(ValueType.OBJECT, 2),
            new CfLoad(ValueType.INT, 4),
            new CfArrayLoad(MemberType.OBJECT),
            new CfStore(ValueType.OBJECT, 5),
            label6,
            new CfLoad(ValueType.OBJECT, 5),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Field;"),
                    factory.createProto(factory.intType),
                    factory.createString("getModifiers")),
                false),
            new CfInvoke(
                184,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Modifier;"),
                    factory.createProto(factory.booleanType, factory.intType),
                    factory.createString("isStatic")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label10),
            new CfConstClass(factory.createType("Lsun/misc/Unsafe;")),
            new CfLoad(ValueType.OBJECT, 5),
            label7,
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Field;"),
                    factory.createProto(factory.classType),
                    factory.createString("getType")),
                false),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.classType,
                    factory.createProto(factory.booleanType, factory.classType),
                    factory.createString("isAssignableFrom")),
                false),
            new CfIf(IfType.EQ, ValueType.INT, label10),
            label8,
            new CfLoad(ValueType.OBJECT, 5),
            new CfStore(ValueType.OBJECT, 0),
            label9,
            new CfGoto(label11),
            label10,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1, 2, 3, 4},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/Field;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/NoSuchFieldException;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("[Ljava/lang/reflect/Field;")),
                      FrameType.intType(),
                      FrameType.intType()
                    })),
            new CfIinc(4, 1),
            new CfGoto(label5),
            label11,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0, 1},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/Field;")),
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/NoSuchFieldException;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfIf(IfType.EQ, ValueType.OBJECT, label13),
            label12,
            new CfNew(factory.createType("Ljava/lang/UnsupportedOperationException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfConstString(factory.createString("Couldn't find the Unsafe")),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/UnsupportedOperationException;"),
                    factory.createProto(
                        factory.voidType, factory.stringType, factory.throwableType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label13,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/Field;"))
                    })),
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNumber(1, ValueType.INT),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Field;"),
                    factory.createProto(factory.voidType, factory.booleanType),
                    factory.createString("setAccessible")),
                false),
            label14,
            new CfLoad(ValueType.OBJECT, 0),
            new CfConstNull(),
            new CfInvoke(
                182,
                factory.createMethod(
                    factory.createType("Ljava/lang/reflect/Field;"),
                    factory.createProto(factory.objectType, factory.objectType),
                    factory.createString("get")),
                false),
            new CfCheckCast(factory.createType("Lsun/misc/Unsafe;")),
            label15,
            new CfReturn(ValueType.OBJECT),
            label16,
            new CfFrame(
                new Int2ObjectAVLTreeMap<>(
                    new int[] {0},
                    new FrameType[] {
                      FrameType.initializedNonNullReference(
                          factory.createType("Ljava/lang/reflect/Field;"))
                    }),
                new ArrayDeque<>(
                    Arrays.asList(
                        FrameType.initializedNonNullReference(
                            factory.createType("Ljava/lang/Exception;"))))),
            new CfStore(ValueType.OBJECT, 1),
            label17,
            new CfNew(factory.createType("Ljava/lang/RuntimeException;")),
            new CfStackInstruction(CfStackInstruction.Opcode.Dup),
            new CfLoad(ValueType.OBJECT, 1),
            new CfInvoke(
                183,
                factory.createMethod(
                    factory.createType("Ljava/lang/RuntimeException;"),
                    factory.createProto(factory.voidType, factory.throwableType),
                    factory.createString("<init>")),
                false),
            new CfThrow(),
            label18),
        ImmutableList.of(
            new CfTryCatch(
                label1,
                label2,
                ImmutableList.of(factory.createType("Ljava/lang/NoSuchFieldException;")),
                ImmutableList.of(label3)),
            new CfTryCatch(
                label14,
                label15,
                ImmutableList.of(factory.createType("Ljava/lang/Exception;")),
                ImmutableList.of(label16))),
        ImmutableList.of());
  }
}
