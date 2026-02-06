// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeInstructionListIterator;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AtomicFieldUpdaterInstrumentor.AtomicFieldUpdaterInstrumentorInfo;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations;
import com.android.tools.r8.utils.AndroidApiLevel;
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
  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
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
            methodProcessor,
            methodProcessingContext,
            invoke,
            atomicUpdaterFields,
            info,
            next.outValue())) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(
          dexItemFactory.atomicFieldUpdaterMethods.referenceGet)) {
        if (visitGet(
            code,
            it,
            methodProcessor,
            methodProcessingContext,
            invoke,
            atomicUpdaterFields,
            info,
            next.outValue())) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(
          dexItemFactory.atomicFieldUpdaterMethods.referenceSet)) {
        if (visitSet(
            code,
            it,
            methodProcessor,
            methodProcessingContext,
            invoke,
            atomicUpdaterFields,
            info,
            next.outValue())) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(
          dexItemFactory.atomicFieldUpdaterMethods.referenceGetAndSet)) {
        if (visitGetAndSet(
            code,
            it,
            methodProcessor,
            methodProcessingContext,
            next.getPosition(),
            invoke,
            atomicUpdaterFields,
            info,
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
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      InvokeVirtual invoke,
      Map<DexField, AtomicFieldUpdaterInfo> atomicUpdaterFields,
      AtomicFieldUpdaterInstrumentorInfo info,
      Value outValue) {
    // Resolve updater.
    var updaterValue = invoke.getReceiver();
    var resolvedUpdater =
        resolveUpdater(
            code, invoke.getPosition(), atomicUpdaterFields, updaterValue, "compareAndSet");
    if (resolvedUpdater == null) {
      return false;
    }

    // Resolve holder.
    var holderValue = invoke.getFirstNonReceiverArgument();
    var expectedHolder = resolvedUpdater.updaterFieldInfo.holder;
    var resolvedHolder =
        resolveHolder(invoke.getPosition(), holderValue, expectedHolder, "compareAndSet");
    if (resolvedHolder == null) {
      return false;
    }

    // Resolve expect.
    var expectValue = invoke.getSecondNonReceiverArgument();

    // Resolve update.
    var updateValue = invoke.getThirdNonReceiverArgument();
    if (!isNewValueValid(invoke.getPosition(), resolvedUpdater, updateValue, "compareAndSet")) {
      return false;
    }

    reportSuccess(invoke.getPosition(), resolvedUpdater.isNullable);
    rewriteCompareAndSet(
        code,
        it,
        methodProcessor,
        methodProcessingContext,
        invoke.getPosition(),
        resolvedUpdater,
        resolvedHolder,
        info,
        updaterValue,
        holderValue,
        expectValue,
        updateValue,
        outValue);
    return true;
  }

  private boolean visitGet(
      IRCode code,
      IRCodeInstructionListIterator it,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      InvokeVirtual invoke,
      Map<DexField, AtomicFieldUpdaterInfo> atomicUpdaterFields,
      AtomicFieldUpdaterInstrumentorInfo info,
      Value outValue) {
    // Resolve updater.
    var updaterValue = invoke.getReceiver();
    var resolvedUpdater =
        resolveUpdater(code, invoke.getPosition(), atomicUpdaterFields, updaterValue, "get");
    if (resolvedUpdater == null) {
      return false;
    }

    // Resolve holder.
    var holderValue = invoke.getFirstNonReceiverArgument();
    var expectedHolder = resolvedUpdater.updaterFieldInfo.holder;
    var resolvedHolder = resolveHolder(invoke.getPosition(), holderValue, expectedHolder, "get");
    if (resolvedHolder == null) {
      return false;
    }

    reportSuccess(invoke.getPosition(), resolvedUpdater.isNullable);
    rewriteGet(
        code,
        it,
        methodProcessor,
        methodProcessingContext,
        invoke.getPosition(),
        resolvedUpdater,
        resolvedHolder,
        info,
        updaterValue,
        holderValue,
        outValue);
    return true;
  }

  private boolean visitSet(
      IRCode code,
      IRCodeInstructionListIterator it,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      InvokeVirtual invoke,
      Map<DexField, AtomicFieldUpdaterInfo> atomicUpdaterFields,
      AtomicFieldUpdaterInstrumentorInfo info,
      Value outValue) {
    // Resolve updater.
    var updaterValue = invoke.getReceiver();
    var resolvedUpdater =
        resolveUpdater(code, invoke.getPosition(), atomicUpdaterFields, updaterValue, "set");
    if (resolvedUpdater == null) {
      return false;
    }

    // Resolve holder.
    var holderValue = invoke.getFirstNonReceiverArgument();
    var expectedHolder = resolvedUpdater.updaterFieldInfo.holder;
    var resolvedHolder = resolveHolder(invoke.getPosition(), holderValue, expectedHolder, "set");
    if (resolvedHolder == null) {
      return false;
    }

    // Resolve newValue.
    var newValueValue = invoke.getSecondNonReceiverArgument();
    if (!isNewValueValid(invoke.getPosition(), resolvedUpdater, newValueValue, "set")) {
      return false;
    }

    reportSuccess(invoke.getPosition(), resolvedUpdater.isNullable);
    rewriteSet(
        code,
        it,
        methodProcessor,
        methodProcessingContext,
        invoke.getPosition(),
        resolvedUpdater,
        resolvedHolder,
        info,
        updaterValue,
        holderValue,
        newValueValue,
        outValue);
    return true;
  }

  private boolean visitGetAndSet(
      IRCode code,
      IRCodeInstructionListIterator it,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Position position,
      InvokeVirtual invoke,
      Map<DexField, AtomicFieldUpdaterInfo> atomicUpdaterFields,
      AtomicFieldUpdaterInstrumentorInfo info,
      Value outValue) {
    if (appView.options().isGeneratingDex()
        && appView.options().getMinApiLevel().isLessThan(AndroidApiLevel.N)) {
      reportFailure(position, "android api level < N");
      return false;
    }

    // Resolve updater.
    var updaterValue = invoke.getReceiver();
    var resolvedUpdater =
        resolveUpdater(code, position, atomicUpdaterFields, updaterValue, "getAndSet");
    if (resolvedUpdater == null) {
      return false;
    }

    // Resolve holder.
    var holderValue = invoke.getFirstNonReceiverArgument();
    var expectedHolder = resolvedUpdater.updaterFieldInfo.holder;
    var resolvedHolder =
        resolveHolder(invoke.getPosition(), holderValue, expectedHolder, "getAndSet");
    if (resolvedHolder == null) {
      return false;
    }

    // Resolve newValue.
    var newValueValue = invoke.getSecondNonReceiverArgument();
    if (!isNewValueValid(position, resolvedUpdater, newValueValue, "getAndSet")) {
      return false;
    }

    reportSuccess(position, resolvedUpdater.isNullable);
    rewriteGetAndSet(
        code,
        it,
        methodProcessor,
        methodProcessingContext,
        position,
        resolvedUpdater,
        resolvedHolder,
        info,
        updaterValue,
        holderValue,
        newValueValue,
        outValue);
    return true;
  }

  private ResolvedUpdater resolveUpdater(
      IRCode code,
      Position position,
      Map<DexField, AtomicFieldUpdaterInfo> atomicUpdaterFields,
      Value updaterValue,
      String methodNameForLogging) {
    var unusedUpdaterMightBeNull = updaterValue.getType().isNullable();
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
    // TODO(b/453628974): stop assuming non-null for all updaters.
    return new ResolvedUpdater(false, updaterInfo);
  }

  private static class ResolvedUpdater {

    final boolean isNullable;
    final AtomicFieldUpdaterInfo updaterFieldInfo;

    private ResolvedUpdater(boolean isNullable, AtomicFieldUpdaterInfo updaterFieldInfo) {
      this.isNullable = isNullable;
      this.updaterFieldInfo = updaterFieldInfo;
    }
  }

  private ResolvedHolder resolveHolder(
      Position position, Value holderValue, DexType expectedHolder, String methodNameForLogging) {
    TypeElement holderType = holderValue.getType();
    if (!holderType.lessThanOrEqual(expectedHolder.toTypeElement(appView), appView)) {
      reportFailure(position, "_." + methodNameForLogging + "(HERE, ..) is a wrong type");
      return null;
    }
    var isNullable = holderType.isNullable();
    return new ResolvedHolder(isNullable);
  }

  private static class ResolvedHolder {
    public final boolean isNullable;

    private ResolvedHolder(boolean isNullable) {
      this.isNullable = isNullable;
    }
  }

  private boolean isNewValueValid(
      Position position,
      ResolvedUpdater resolvedUpdater,
      Value newValueValue,
      String methodNameForLogging) {
    var expectedType = resolvedUpdater.updaterFieldInfo.reflectedFieldType;
    if (!newValueValue.getType().lessThanOrEqual(expectedType.toTypeElement(appView), appView)) {
      reportFailure(position, "_." + methodNameForLogging + "(_, HERE, ..) is of unexpected type");
      return false;
    }
    return true;
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
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Position position,
      ResolvedUpdater resolvedUpdater,
      ResolvedHolder resolvedHolder,
      AtomicFieldUpdaterInstrumentorInfo info,
      Value updaterValue,
      Value holderValue,
      Value expectValue,
      Value updateValue,
      Value outValue) {
    var instructions = new ArrayList<Instruction>(4);

    if (resolvedUpdater.isNullable) {
      instructions.add(createNullCheck(code, position, updaterValue));
    }

    if (resolvedHolder.isNullable) {
      instructions.add(
          createNullCheckWithClassCastException(
              methodProcessor, methodProcessingContext, position, holderValue));
    }

    Instruction unsafeInstance = createUnsafeGet(code, position, info.getUnsafeInstanceField());
    instructions.add(unsafeInstance);

    Instruction offset =
        createOffsetGet(code, position, resolvedUpdater.updaterFieldInfo.offsetField);
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
   * {@code it.next}) into a call {@code SyntheticUnsafeClass.unsafe.getReferenceVolatile(holder,
   * this.offset)} and potentially a null-check on updater.
   */
  private void rewriteGet(
      IRCode code,
      IRCodeInstructionListIterator it,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Position position,
      ResolvedUpdater resolvedUpdater,
      ResolvedHolder resolvedHolder,
      AtomicFieldUpdaterInstrumentorInfo info,
      Value updaterValue,
      Value holderValue,
      Value outValue) {
    var instructions = new ArrayList<Instruction>(4);

    // Null-check for updater.
    if (resolvedUpdater.isNullable) {
      instructions.add(createNullCheck(code, position, updaterValue));
    }

    if (resolvedHolder.isNullable) {
      instructions.add(
          createNullCheckWithClassCastException(
              methodProcessor, methodProcessingContext, position, holderValue));
    }

    // Get unsafe instance.
    Instruction unsafeInstance = createUnsafeGet(code, position, info.getUnsafeInstanceField());
    instructions.add(unsafeInstance);

    // Get offset field.
    Instruction offset =
        createOffsetGet(code, position, resolvedUpdater.updaterFieldInfo.offsetField);
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

  /**
   * Rewrites a call to {@code updater.set(holder, newValue)} (assumed to be the last instruction
   * returned by {@code it.next}) into a call {@code
   * SyntheticUnsafeClass.unsafe.putReferenceVolatile(holder, this.offset, newValue)} and
   * potentially a null-check on updater.
   */
  private void rewriteSet(
      IRCode code,
      IRCodeInstructionListIterator it,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Position position,
      ResolvedUpdater resolvedUpdater,
      ResolvedHolder resolvedHolder,
      AtomicFieldUpdaterInstrumentorInfo info,
      Value updaterValue,
      Value holderValue,
      Value newValueValue,
      Value outValue) {
    var instructions = new ArrayList<Instruction>(4);

    // Null-check for updater.
    if (resolvedUpdater.isNullable) {
      instructions.add(createNullCheck(code, position, updaterValue));
    }

    if (resolvedHolder.isNullable) {
      instructions.add(
          createNullCheckWithClassCastException(
              methodProcessor, methodProcessingContext, position, holderValue));
    }

    // Get unsafe instance.
    Instruction unsafeInstance = createUnsafeGet(code, position, info.getUnsafeInstanceField());
    instructions.add(unsafeInstance);

    // Get offset field.
    Instruction offset =
        createOffsetGet(code, position, resolvedUpdater.updaterFieldInfo.offsetField);
    instructions.add(offset);

    // Add instructions BEFORE the get instruction.
    insertInstructionsBeforeCurrentInstruction(it, instructions);

    // Call underlying unsafe method.
    DexMethod unsafeSetMethod =
        dexItemFactory.createMethod(
            dexItemFactory.unsafeType,
            dexItemFactory.createProto(
                dexItemFactory.voidType,
                dexItemFactory.objectType,
                dexItemFactory.longType,
                dexItemFactory.objectType),
            "putObjectVolatile");
    Instruction unsafeSet =
        new InvokeVirtual(
            unsafeSetMethod,
            outValue,
            ImmutableList.of(
                unsafeInstance.outValue(), holderValue, offset.outValue(), newValueValue));
    unsafeSet.setPosition(position);
    it.replaceCurrentInstruction(unsafeSet);
    // TODO(b/453628974): Does profiling need to be updated?
  }

  /**
   * Rewrites a call to {@code updater.getAndSet(holder, newValue)} (assumed to be the last
   * instruction returned by {@code it.next}) into a call {@code
   * SyntheticUnsafeClass.unsafe.getAndSetObject(holder, this.offset, newValue)} and potentially a
   * null-check on updater.
   */
  private void rewriteGetAndSet(
      IRCode code,
      IRCodeInstructionListIterator it,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Position position,
      ResolvedUpdater resolvedUpdater,
      ResolvedHolder resolvedHolder,
      AtomicFieldUpdaterInstrumentorInfo info,
      Value updaterValue,
      Value holderValue,
      Value newValueValue,
      Value outValue) {
    var instructions = new ArrayList<Instruction>(4);

    // Null-check for updater.
    if (resolvedUpdater.isNullable) {
      instructions.add(createNullCheck(code, position, updaterValue));
    }

    if (resolvedHolder.isNullable) {
      instructions.add(
          createNullCheckWithClassCastException(
              methodProcessor, methodProcessingContext, position, holderValue));
    }

    // Get unsafe instance.
    Instruction unsafeInstance = createUnsafeGet(code, position, info.getUnsafeInstanceField());
    instructions.add(unsafeInstance);

    // Get offset field.
    Instruction offset =
        createOffsetGet(code, position, resolvedUpdater.updaterFieldInfo.offsetField);
    instructions.add(offset);

    // Add instructions BEFORE the get instruction.
    insertInstructionsBeforeCurrentInstruction(it, instructions);

    // Call underlying unsafe method.
    DexMethod unsafeGetAndSetMethod =
        dexItemFactory.createMethod(
            dexItemFactory.unsafeType,
            dexItemFactory.createProto(
                dexItemFactory.objectType,
                dexItemFactory.objectType,
                dexItemFactory.longType,
                dexItemFactory.objectType),
            "getAndSetObject");
    Instruction unsafeGetAndSet =
        new InvokeVirtual(
            unsafeGetAndSetMethod,
            outValue,
            ImmutableList.of(
                unsafeInstance.outValue(), holderValue, offset.outValue(), newValueValue));
    unsafeGetAndSet.setPosition(position);
    it.replaceCurrentInstruction(unsafeGetAndSet);
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

  private InvokeVirtual createNullCheck(IRCode code, Position position, Value value) {
    var nullCheck =
        new InvokeVirtual(
            dexItemFactory.objectMembers.getClass,
            code.createValue(dexItemFactory.classType.toTypeElement(appView)),
            ImmutableList.of(value));
    nullCheck.setPosition(position);
    return nullCheck;
  }

  private InvokeStatic createNullCheckWithClassCastException(
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Position position,
      Value value) {
    var optimizations =
        UtilityMethodsForCodeOptimizations.synthesizeThrowClassCastExceptionIfNullMethod(
            appView, methodProcessor.getEventConsumer(), methodProcessingContext);
    optimizations.optimize(methodProcessor);
    InvokeStatic invokeStatic =
        new InvokeStatic(optimizations.getMethod().getReference(), null, ImmutableList.of(value));
    invokeStatic.setPosition(position);
    return invokeStatic;
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
