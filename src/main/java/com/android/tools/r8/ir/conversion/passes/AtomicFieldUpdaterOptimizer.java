// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeInstructionListIterator;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AtomicFieldUpdaterInstrumentor.AtomicFieldUpdaterInstrumentorInfo;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Map;

/**
 * This pass uses the information and instrumentation of {@code AtomicFieldUpdaterInstrumentor} to
 * find calls to AtomicFieldUpdater and replace them by their underlying unsafe operation.
 *
 * <BlockQuote>
 *
 * <pre>
 *  class Example {
 *    volatile SomeType someField;
 *    static final AtomicReferenceFieldUpdater updater;
 *    // Synthetic field produced by AtomicFieldUpdaterInstrumentor.
 *    public static final long updater$offset;
 *
 *    static {
 *      .. initializes both updater and updater$offset ..
 *    }
 *
 *    ReturnType exampleFunction(..) {
 *      ..
 *      updater.compareAndSet(instance, expect, update);
 *      // If instance is known to be non-null and be a subtype of Example,
 *      // and update is known to have type SomeType or be null,
 *      // then replace the call with
 *      SyntheticUnsafeClass.unsafe.compareAndSet(instance, updater$offset, expect, update)
 *      // If updater can be null, a null-check is inserted before the new call.
 *      ..
 *    }
 *  }
 * </pre>
 *
 * </BlockQuote>
 */
public class AtomicFieldUpdaterOptimizer extends CodeRewriterPass<AppInfoWithClassHierarchy> {

  protected AtomicFieldUpdaterOptimizer(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "AtomicFieldUpdaterOptimizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    // TODO(b/453628974): Test with compare calls in class initializer.
    return !isDebugMode(code.context())
        && methodProcessor.isPrimaryMethodProcessor()
        // TODO(b/453628974): Consider running in second pass (must maintain appView data).
        && appView.getAtomicFieldUpdaterInstrumentorInfo().hasUnsafe()
        && appView
            .getAtomicFieldUpdaterInstrumentorInfo()
            .getInstrumentations()
            .containsKey(code.context().getHolderType())
        && code.metadata().mayHaveInvokeMethodWithReceiver();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    AtomicFieldUpdaterInstrumentorInfo info = appView.getAtomicFieldUpdaterInstrumentorInfo();
    var atomicUpdaterFields = info.getInstrumentations().get(code.context().getHolderType());
    assert atomicUpdaterFields != null;

    // This code is the assumed implementation of AtomicReferenceFieldUpdater.compareAndSet.
    //
    // boolean compareAndSet(T obj, V expect, V update) {
    //   if (!this.cclass.isInstance(obj))
    //     throwAccessCheckException(obj);
    //   if (update != null && !(vclass.isInstance(update)))
    //     throwCCE();
    //   return U.compareAndSetReference(obj, offset, expect, update);
    // }
    var it = code.instructionListIterator();
    var changed = false;
    while (it.hasNext()) {
      var next = it.nextUntil(Instruction::isInvokeVirtual);
      if (next == null) {
        continue;
      }
      var invoke = next.asInvokeVirtual();
      assert invoke != null;
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (!invokedMethod.holder.isIdenticalTo(
          dexItemFactory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater)) {
        continue;
      }

      // Check for updater.compareAndSet(holder, expect, update) call.
      if (invokedMethod.isIdenticalTo(
          dexItemFactory.atomicFieldUpdaterMethods.referenceCompareAndSet)) {
        if (visitCompareAndSet(
            code,
            it,
            next.getPosition(),
            invoke,
            atomicUpdaterFields,
            info.getUnsafeInstanceField(),
            next.outValue())) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(
          dexItemFactory.atomicFieldUpdaterMethods.referenceGet)) {
        if (visitGet(
            code,
            it,
            next.getPosition(),
            invoke,
            atomicUpdaterFields,
            info.getUnsafeInstanceField(),
            next.outValue())) {
          changed = true;
        }
      } else {
        reportFailure(
            next.getPosition(), "not implemented: " + invokedMethod.name.toSourceString());
      }
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  private boolean visitCompareAndSet(
      IRCode code,
      IRCodeInstructionListIterator it,
      Position position,
      InvokeVirtual invoke,
      Map<DexField, AtomicFieldUpdaterInfo> atomicUpdaterFields,
      DexField unsafeInstanceField,
      Value outValue) {
    // Resolve updater.
    var updaterValue = invoke.getReceiver();
    var resolvedUpdater =
        resolveUpdater(code, position, atomicUpdaterFields, updaterValue, "compareAndSet");
    if (resolvedUpdater == null) {
      return false;
    }

    // Resolve holder.
    var holderValue = invoke.getFirstNonReceiverArgument();
    var expectedHolder = resolvedUpdater.updaterFieldInfo.holder;
    if (!isHolderValid(position, holderValue, expectedHolder, "compareAndSet")) {
      return false;
    }

    // Resolve expect.
    var expectValue = invoke.getSecondNonReceiverArgument();

    // Resolve update.
    var updateValue = invoke.getThirdNonReceiverArgument();
    var expectedType = resolvedUpdater.updaterFieldInfo.reflectedFieldType;
    if (!updateValue.getType().lessThanOrEqual(expectedType.toTypeElement(appView), appView)) {
      reportFailure(position, "_.compareAndSet(_, _, HERE) is of unexpected type");
      return false;
    }

    reportSuccess(position, resolvedUpdater.isNullable);
    rewriteCompareAndSet(
        code,
        it,
        position,
        resolvedUpdater.isNullable,
        unsafeInstanceField,
        updaterValue,
        holderValue,
        resolvedUpdater.updaterFieldInfo.offsetField,
        expectValue,
        updateValue,
        outValue);
    return true;
  }

  private boolean visitGet(
      IRCode code,
      IRCodeInstructionListIterator it,
      Position position,
      InvokeVirtual invoke,
      Map<DexField, AtomicFieldUpdaterInfo> atomicUpdaterFields,
      DexField unsafeInstanceField,
      Value outValue) {
    // Resolve updater.
    var updaterValue = invoke.getReceiver();
    var resolvedUpdater = resolveUpdater(code, position, atomicUpdaterFields, updaterValue, "get");
    if (resolvedUpdater == null) {
      return false;
    }

    // Resolve holder.
    var holderValue = invoke.getFirstNonReceiverArgument();
    var expectedHolder = resolvedUpdater.updaterFieldInfo.holder;
    if (!isHolderValid(position, holderValue, expectedHolder, "get")) {
      return false;
    }

    reportSuccess(position, resolvedUpdater.isNullable);
    rewriteGet(
        code,
        it,
        position,
        resolvedUpdater.isNullable,
        unsafeInstanceField,
        updaterValue,
        holderValue,
        resolvedUpdater.updaterFieldInfo.offsetField,
        outValue);
    return true;
  }

  private ResolvedUpdater resolveUpdater(
      IRCode code,
      Position position,
      Map<DexField, AtomicFieldUpdaterInfo> atomicUpdaterFields,
      Value updaterValue,
      String methodNameForLogging) {
    var updaterMightBeNull = updaterValue.getType().isNullable();
    DexField updaterField;
    var updaterAbstractValue =
        updaterValue.getAbstractValue(appView, code.context()).removeNullOrAbstractValue();
    if (updaterAbstractValue.isSingleFieldValue()) {
      updaterField = updaterAbstractValue.asSingleFieldValue().getField();
    } else {
      reportFailure(
          position,
          "HERE."
              + methodNameForLogging
              + "(..) is statically unclear or unhelpful: "
              + updaterAbstractValue);
      return null;
    }
    var updaterInfo = atomicUpdaterFields.get(updaterField);
    if (updaterInfo == null) {
      reportFailure(
          position,
          "HERE." + methodNameForLogging + "(..) refers to an un-instrumented updater field");
      return null;
    }
    return new ResolvedUpdater(updaterMightBeNull, updaterInfo);
  }

  private static class ResolvedUpdater {

    final boolean isNullable;
    final AtomicFieldUpdaterInfo updaterFieldInfo;

    private ResolvedUpdater(boolean isNullable, AtomicFieldUpdaterInfo updaterFieldInfo) {
      this.isNullable = isNullable;
      this.updaterFieldInfo = updaterFieldInfo;
    }
  }

  private boolean isHolderValid(
      Position position, Value holderValue, DexType expectedHolder, String methodNameForLogging) {
    if (holderValue
        .getType()
        .lessThanOrEqual(expectedHolder.toNonNullTypeElement(appView), appView)) {
      return true;
    }
    if (appView.testing().enableAtomicFieldUpdaterLogs) {
      if (holderValue.getType().lessThanOrEqual(expectedHolder.toTypeElement(appView), appView)) {
        reportFailure(position, "_." + methodNameForLogging + "(HERE, ..) is nullable");
      } else {
        reportFailure(position, "_." + methodNameForLogging + "(HERE, ..) is of unexpected type");
      }
    }
    return false;
  }

  /**
   * Rewrites a call to {@code updater.compareAndSet(holder, expect, update)} (assumed to be the
   * last instruction returned by {@code it.next}) into a call {@code
   * SyntheticUnsafeClass.unsafe.compareAndSwapObject(holder, this.offsetField, expect, update)} and
   * potentially a null-check on updater.
   */
  private void rewriteCompareAndSet(
      IRCode code,
      IRCodeInstructionListIterator it,
      Position position,
      boolean updaterMightBeNull,
      DexField unsafeInstanceField,
      Value updaterValue,
      Value holderValue,
      DexField offsetField,
      Value expectValue,
      Value updateValue,
      Value outValue) {
    var instructions = new ArrayList<Instruction>(3);

    if (updaterMightBeNull) {
      instructions.add(createNullCheck(code, position, updaterValue));
    }

    Instruction unsafeInstance = createUnsafeGet(code, position, unsafeInstanceField);
    instructions.add(unsafeInstance);

    Instruction offset = createOffsetGet(code, position, offsetField);
    instructions.add(offset);

    // Add instructions BEFORE the compareAndSet instruction.
    insertInstructionsBeforeCurrentInstruction(it, instructions);

    // Call underlying unsafe method.
    DexMethod unsafeCompareAndSetMethod =
        dexItemFactory.createMethod(
            dexItemFactory.unsafeType,
            dexItemFactory.createProto(
                dexItemFactory.booleanType,
                dexItemFactory.objectType,
                dexItemFactory.longType,
                dexItemFactory.objectType,
                dexItemFactory.objectType),
            "compareAndSwapObject");
    Instruction unsafeCompareAndSet =
        new InvokeVirtual(
            unsafeCompareAndSetMethod,
            outValue,
            ImmutableList.of(
                unsafeInstance.outValue(),
                holderValue,
                offset.outValue(),
                expectValue,
                updateValue));
    unsafeCompareAndSet.setPosition(position);
    it.replaceCurrentInstruction(unsafeCompareAndSet);
    // TODO(b/453628974): Does profiling need to be updated?
  }

  /**
   * Rewrites a call to {@code updater.get(holder)} (assumed to be the last instruction returned by
   * {@code it.next}) into a call {@code SyntheticUnsafeClass.unsafe.getReferenceVolatile(holder)}
   * and potentially a null-check on updater.
   */
  private void rewriteGet(
      IRCode code,
      IRCodeInstructionListIterator it,
      Position position,
      boolean updaterMightBeNull,
      DexField unsafeInstanceField,
      Value updaterValue,
      Value holderValue,
      DexField offsetField,
      Value outValue) {
    var instructions = new ArrayList<Instruction>(3);

    // Null-check for updater.
    if (updaterMightBeNull) {
      instructions.add(createNullCheck(code, position, updaterValue));
    }

    // Get unsafe instance.
    Instruction unsafeInstance = createUnsafeGet(code, position, unsafeInstanceField);
    instructions.add(unsafeInstance);

    // Get offset field.
    Instruction offset = createOffsetGet(code, position, offsetField);
    instructions.add(offset);

    // Add instructions BEFORE the get instruction.
    insertInstructionsBeforeCurrentInstruction(it, instructions);

    // Call underlying unsafe method.
    DexMethod unsafeGetMethod =
        dexItemFactory.createMethod(
            dexItemFactory.unsafeType,
            dexItemFactory.createProto(
                dexItemFactory.objectType, dexItemFactory.objectType, dexItemFactory.longType),
            "getObjectVolatile");
    Instruction unsafeGet =
        new InvokeVirtual(
            unsafeGetMethod,
            outValue,
            ImmutableList.of(unsafeInstance.outValue(), holderValue, offset.outValue()));
    unsafeGet.setPosition(position);
    it.replaceCurrentInstruction(unsafeGet);
    // TODO(b/453628974): Does profiling need to be updated?
  }

  private Instruction createOffsetGet(IRCode code, Position position, DexField offsetField) {
    assert offsetField.type.isIdenticalTo(dexItemFactory.longType);
    Instruction offset =
        new StaticGet(
            code.createValue(dexItemFactory.longType.toTypeElement(appView)), offsetField);
    offset.setPosition(position);
    return offset;
  }

  private InvokeVirtual createNullCheck(IRCode code, Position position, Value updaterValue) {
    var nullCheck =
        new InvokeVirtual(
            dexItemFactory.objectMembers.getClass,
            code.createValue(dexItemFactory.classType.toTypeElement(appView)),
            ImmutableList.of(updaterValue));
    nullCheck.setPosition(position);
    return nullCheck;
  }

  private Instruction createUnsafeGet(
      IRCode code, Position position, DexField unsafeInstanceField) {
    assert unsafeInstanceField.type.isIdenticalTo(dexItemFactory.unsafeType);
    Instruction unsafeInstance =
        new StaticGet(
            code.createValue(dexItemFactory.unsafeType.toTypeElement(appView)),
            unsafeInstanceField);
    unsafeInstance.setPosition(position);
    return unsafeInstance;
  }

  private void insertInstructionsBeforeCurrentInstruction(
      IRCodeInstructionListIterator it, ArrayList<Instruction> instructions) {
    it.previous();
    // TODO(b/453628974): Test with a local exception handler.
    it.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(instructions, appView.options());
    it.next();
  }

  private void reportFailure(Position position, String reason) {
    if (!appView.testing().enableAtomicFieldUpdaterLogs) {
      return;
    }
    appView
        .reporter()
        .info(
            "Cannot optimize AtomicFieldUpdater use: "
                + reason
                + " ("
                + position.getMethod().toSourceString()
                + ")");
  }

  private void reportSuccess(Position position, boolean mightBeNull) {
    if (!appView.testing().enableAtomicFieldUpdaterLogs) {
      return;
    }
    appView
        .reporter()
        .info(
            "Can optimize AtomicFieldUpdater use:    "
                + (mightBeNull ? "with   " : "without")
                + " null-check"
                + " ("
                + position.getMethod().toSourceString()
                + ")");
  }

  /**
   * Stores static creation information around a atomic field updater.
   *
   * <blockquote>
   *
   * <pre>
   * class holder {
   *   volatile reflectedFieldType someField;
   *   static final AtomicReferenceFieldUpdater updater = AtomicReferenceFieldUpdater.newUpdater(holder.class, reflectedFieldType, "someField");
   *   public static final long offsetField = ..
   * }
   * </pre>
   *
   * </blockquote>
   */
  public static class AtomicFieldUpdaterInfo {

    public final DexType holder;
    public final DexType reflectedFieldType;
    public final DexField offsetField;

    public AtomicFieldUpdaterInfo(
        DexType holder, DexType reflectedFieldType, DexField offsetField) {
      this.holder = holder;
      this.reflectedFieldType = reflectedFieldType;
      this.offsetField = offsetField;
    }
  }
}
