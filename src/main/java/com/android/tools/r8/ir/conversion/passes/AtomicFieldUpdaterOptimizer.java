// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
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
    DexItemFactory factory = appView.dexItemFactory();
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

      // Check for updater.compareAndSet(holder, expect, update) call.
      if (!invoke
          .getInvokedMethod()
          .isIdenticalTo(factory.atomicFieldUpdaterMethods.referenceCompareAndSet)) {
        continue;
      }

      // Resolve updater.
      var updaterValue = invoke.getReceiver();
      var updaterMightBeNull = updaterValue.getType().isNullable();
      DexField updaterField;
      var updaterAbstractValue =
          updaterValue.getAbstractValue(appView, code.context()).removeNullOrAbstractValue();
      if (updaterAbstractValue.isSingleFieldValue()) {
        updaterField = updaterAbstractValue.asSingleFieldValue().getField();
      } else {
        reportFailure(
            next.getPosition(),
            "HERE.compareAndSet(..) is statically unclear or unhelpful: " + updaterAbstractValue);
        continue;
      }
      var updaterInfo = atomicUpdaterFields.get(updaterField);
      if (updaterInfo == null) {
        reportFailure(
            next.getPosition(),
            "HERE.compareAndSet(..) refers to an un-instrumented updater field");
        continue;
      }

      // Resolve holder.
      var holderValue = invoke.getFirstNonReceiverArgument();
      var expectedHolder = updaterInfo.holder;
      if (!holderValue
          .getType()
          .lessThanOrEqual(expectedHolder.toNonNullTypeElement(appView), appView)) {
        if (appView.testing().enableAtomicFieldUpdaterLogs) {
          if (holderValue
              .getType()
              .lessThanOrEqual(expectedHolder.toTypeElement(appView), appView)) {
            reportFailure(next.getPosition(), "_.compareAndSet(HERE, _, _) is nullable");
          } else {
            reportFailure(next.getPosition(), "_.compareAndSet(HERE, _, _) is of unexpected type");
          }
        }
        continue;
      }

      // Resolve expect.
      var expectValue = invoke.getSecondNonReceiverArgument();

      // Resolve update.
      var updateValue = invoke.getThirdNonReceiverArgument();
      var expectedType = updaterInfo.reflectedFieldType;
      if (!updateValue.getType().lessThanOrEqual(expectedType.toTypeElement(appView), appView)) {
        reportFailure(next.getPosition(), "_.compareAndSet(_, _, HERE) is of unexpected type");
        continue;
      }

      rewriteToOptimizedCall(
          code,
          it,
          next.getPosition(),
          updaterMightBeNull,
          info.getUnsafeInstanceField(),
          updaterValue,
          holderValue,
          updaterInfo.offsetField,
          expectValue,
          updateValue,
          next.outValue());
      changed = true;
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  /**
   * Rewrites a call to {@code updater.compareAndSet(holder, expect, update)} (assumed to be the
   * last instruction returned by {@code it.next}) into a call {@code
   * SyntheticUnsafeClass.unsafe.compareAndSwapObject(holder, this.offsetField, expect, update)} and
   * potentially a null-check on updater.
   */
  private void rewriteToOptimizedCall(
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
    var factory = appView.dexItemFactory();
    var instructions = new ArrayList<Instruction>(3);

    // Null-check for updater.
    if (updaterMightBeNull) {
      var nullCheck =
          new InvokeVirtual(
              factory.objectMembers.getClass,
              code.createValue(factory.classType.toTypeElement(appView)),
              ImmutableList.of(updaterValue));
      nullCheck.setPosition(position);
      instructions.add(nullCheck);
    }

    // Get unsafe instance.
    assert unsafeInstanceField.type.isIdenticalTo(factory.unsafeType);
    Instruction unsafeInstance =
        new StaticGet(
            code.createValue(factory.unsafeType.toTypeElement(appView)), unsafeInstanceField);
    unsafeInstance.setPosition(position);
    instructions.add(unsafeInstance);

    // Get offset field.
    assert offsetField.type.isIdenticalTo(factory.longType);
    Instruction offset =
        new StaticGet(code.createValue(factory.longType.toTypeElement(appView)), offsetField);
    offset.setPosition(position);
    instructions.add(offset);

    // Add instructions BEFORE the compareAndSet instruction.
    it.previous();
    // TODO(b/453628974): Test with a local exception handler.
    it.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(instructions, appView.options());
    it.next();

    // Call underlying unsafe method.
    DexMethod unsafeCompareAndSetMethod =
        factory.createMethod(
            factory.unsafeType,
            factory.createProto(
                factory.booleanType,
                factory.objectType,
                factory.longType,
                factory.objectType,
                factory.objectType),
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
                + position.getMethod().toString()
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
