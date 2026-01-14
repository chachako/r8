// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRToLirFinalizer;
import com.android.tools.r8.ir.desugar.varhandle.VarHandleDesugaringMethods;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.objectweb.asm.Opcodes;

/**
 * Finds classes with:
 *
 * <blockquote>
 *
 * <pre>
 * class Example {
 *   static final AtomicReferenceFieldUpdater updater;
 *
 *   static {
 *     ..
 *     updater = AtomicReferenceFieldUpdater.newUpdater(SomeClass.class, SomeType.class, "someField");
 *     ..
 *   }
 * }
 * </pre>
 *
 * </blockquote>
 *
 * and converts them to (in bytecode the arguments are reused not recomputed):
 *
 * <blockquote>
 *
 * <pre>
 * class Example {
 *   static final AtomicReferenceFieldUpdater updater;
 *   static final long updater$offset;
 *
 *   static {
 *     ..
 *     updater = AtomicReferenceFieldUpdater.newUpdater(SomeClass.class, SomeType.class, "someField");
 *     updater$offset = SyntheticUnsafeClass.unsafe.objectFieldOffset(SomeClass.class.getDeclaredField("someField"));
 *
 *     ..
 *   }
 * }
 * </pre>
 *
 * </blockquote>
 *
 * Note that `newUpdater` is assumed to call objectFieldOffset internally so crashing behaviour is
 * consistent.
 *
 * <p>This additional field allows circumventing the updater based on static information:
 *
 * <blockquote>
 *
 * <pre>
 * updater.compareAndSet(instance, expect, update)
 * // Optimized into:
 * SyntheticUnsafeClass.unsafe.compareAndSet(instance, updater$offset, expect, update)
 * </pre>
 *
 * </blockquote>
 */
public class AtomicFieldUpdaterInstrumentor {

  // TODO(b/453628974): Revisit profiling to make sure its both sound AND precise, depending on the
  //                    actual optimizations triggered.

  private final AppView<AppInfoWithLiveness> appView;

  private final DexItemFactory itemFactory;
  private final DexMethod objectFieldOffset;
  private static final String unsafeFieldName = "unsafe";
  private static final String getUnsafeMethodName = "getUnsafe";

  public AtomicFieldUpdaterInstrumentor(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;

    itemFactory = appView.dexItemFactory();

    objectFieldOffset =
        itemFactory.createMethod(
            itemFactory.unsafeType,
            itemFactory.createProto(itemFactory.longType, itemFactory.fieldType),
            "objectFieldOffset");
  }

  // TODO(b/453628974): Make sure that all instrumentation is removed if the optimizations do not
  //                    trigger (fields, clinit writes, and synthetic classes).
  // TODO(b/453628974): Make sure that original AtomicUpdater fields are removed if all uses are
  //                    optimized away (fields and clinit writes).
  public void run(ExecutorService service, Timing timing) throws ExecutionException {
    timing.begin("AtomicUpdater field extender");

    var classesWithAtomics = findClassesWithAtomics(service, timing);
    if (!classesWithAtomics.isEmpty()) {
      var profiling = ProfileCollectionAdditions.create(appView);
      var unsafeClass = synthesizeUnsafeClass(classesWithAtomics.keySet(), profiling);
      addOffsetFields(classesWithAtomics, unsafeClass, profiling, service, timing);
      profiling.commit(appView);
    }
    timing.end();
  }

  private void addOffsetFields(
      Multimap<DexProgramClass, DexEncodedField> classesWithAtomics,
      UnsafeClassInfo unsafeClass,
      ProfileCollectionAdditions profiling,
      ExecutorService service,
      Timing timing)
      throws ExecutionException {
    ConcurrentHashMap<DexField, DexField> offsetFields = new ConcurrentHashMap<>();
    ThreadUtils.processItemsThatMatches(
        classesWithAtomics.keySet(),
        Predicates.alwaysTrue(),
        (clazz, threadTiming) ->
            addOffsetFieldsToClass(
                clazz,
                classesWithAtomics.get(clazz),
                offsetFields,
                unsafeClass.unsafeInstanceField,
                profiling,
                threadTiming),
        appView.options(),
        service,
        timing,
        timing.beginMerger("AtomicUpdater offset field writes", service));
    if (!profiling.isNop()) {
      profiling.addMethodIfContextIsInProfile(
          unsafeClass.getUnsafeMethod, unsafeClass.classInitializer);
    }
    var builder = FieldAccessInfoCollectionModifier.builder();
    offsetFields.values().forEach(builder::addField);
    builder.build().modify(appView);
  }

  private UnsafeClassInfo synthesizeUnsafeClass(
      Set<DexProgramClass> classesWithAtomics, ProfileCollectionAdditions profiling) {
    var context = getDeterministicContext(classesWithAtomics);
    var unsafeClass =
        appView
            .getSyntheticItems()
            .createFixedClass(
                kinds -> kinds.ATOMIC_FIELD_UPDATER_HELPER,
                context,
                appView,
                this::buildUnsafeClass);
    var classInitializer = unsafeClass.getProgramClassInitializer();
    var unsafeField =
        unsafeClass.lookupProgramField(
            itemFactory.createField(
                unsafeClass.getType(), itemFactory.unsafeType, unsafeFieldName));
    assert unsafeField != null;
    var getUnsafeMethod =
        unsafeClass.lookupProgramMethod(
            itemFactory.createMethod(
                unsafeClass.getType(),
                itemFactory.createProto(itemFactory.unsafeType),
                getUnsafeMethodName));
    assert getUnsafeMethod != null;
    if (!profiling.isNop()) {
      for (var clazz : classesWithAtomics) {
        // TODO(b/453628974): Break after first callback trigger.
        profiling.applyIfContextIsInProfile(
            clazz, builder -> builder.addClassRule(unsafeClass.getType()));
      }
    }
    appView.rebuildAppInfo();
    return new UnsafeClassInfo(classInitializer, unsafeField, getUnsafeMethod);
  }

  private static class UnsafeClassInfo {

    public final ProgramMethod classInitializer;
    public final ProgramField unsafeInstanceField;
    public final ProgramMethod getUnsafeMethod;

    private UnsafeClassInfo(
        ProgramMethod classInitializer,
        ProgramField unsafeInstanceField,
        ProgramMethod getUnsafeMethod) {
      this.classInitializer = classInitializer;
      this.unsafeInstanceField = unsafeInstanceField;
      this.getUnsafeMethod = getUnsafeMethod;
    }
  }

  private Multimap<DexProgramClass, DexEncodedField> findClassesWithAtomics(
      ExecutorService service, Timing timing) throws ExecutionException {
    var updaterClassesConcurrent = new ConcurrentHashMap<DexProgramClass, Set<DexEncodedField>>();
    ThreadUtils.processItemsThatMatches(
        appView.appInfo().classes(),
        AtomicFieldUpdaterInstrumentor::mightHaveUpdaterFields,
        (clazz, threadTiming) -> findOffsetFields(clazz, updaterClassesConcurrent, threadTiming),
        appView.options(),
        service,
        timing,
        timing.beginMerger("AtomicUpdater field extender", service));
    return convertConcurrentHashMap(updaterClassesConcurrent);
  }

  private static boolean mightHaveUpdaterFields(DexProgramClass clazz) {
    // TODO(b/453628974): Check constant pool?
    return clazz.hasClassInitializer();
  }

  private static <K, V> HashMultimap<K, V> convertConcurrentHashMap(
      ConcurrentHashMap<K, Set<V>> updaterClassesConcurrent) {
    HashMultimap<K, V> updaterClasses = HashMultimap.create();
    updaterClassesConcurrent.forEach(updaterClasses::putAll);
    return updaterClasses;
  }

  private void buildUnsafeClass(SyntheticProgramClassBuilder builder) {
    DexField unsafeField =
        itemFactory.createField(builder.getType(), itemFactory.unsafeType, unsafeFieldName);
    var field =
        DexEncodedField.syntheticBuilder()
            .setField(unsafeField)
            .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
            .setApiLevel(appView.computedMinApiLevel())
            .build();
    builder.setStaticFields(ImmutableList.of(field));
    var accessBuilder = FieldAccessInfoCollectionModifier.builder();
    accessBuilder.addField(unsafeField);
    accessBuilder.build().modify(appView);

    DexMethod getUnsafeMethod =
        itemFactory.createMethod(
            builder.getType(),
            itemFactory.createProto(itemFactory.unsafeType),
            getUnsafeMethodName);
    builder.addMethod(
        methodBuilder -> {
          methodBuilder
              .setName(getUnsafeMethod.name)
              .setProto(getUnsafeMethod.proto)
              .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
              .setApiLevelForDefinition(appView.computedMinApiLevel())
              .setApiLevelForCode(appView.computedMinApiLevel())
              .setCode(
                  method ->
                      VarHandleDesugaringMethods.DesugarVarHandle_getUnsafe(itemFactory, method));
          if (appView.options().isGeneratingClassFiles()) {
            methodBuilder.setClassFileVersion(
                appView.options().requiredCfVersionForConstClassInstructions());
          }
        });
    DexMethod clinit = itemFactory.createClassInitializer(builder.getType());
    builder.addMethod(
        methodBuilder ->
            methodBuilder
                .setName(clinit.name)
                .setProto(clinit.proto)
                .setAccessFlags(MethodAccessFlags.createForClassInitializer())
                .setApiLevelForDefinition(appView.computedMinApiLevel())
                .setApiLevelForCode(appView.computedMinApiLevel())
                .setCode(
                    method ->
                        new CfCode(
                            method.holder,
                            1,
                            0,
                            ImmutableList.of(
                                new CfInvoke(Opcodes.INVOKESTATIC, getUnsafeMethod, false),
                                new CfStaticFieldWrite(unsafeField),
                                new CfReturnVoid()))));
  }

  private static DexProgramClass getDeterministicContext(Set<DexProgramClass> classesWithAtomics) {
    assert !classesWithAtomics.isEmpty();
    return Collections.min(classesWithAtomics);
  }

  private void findOffsetFields(
      DexProgramClass clazz,
      ConcurrentHashMap<DexProgramClass, Set<DexEncodedField>> updaterClasses,
      Timing timing) {
    timing.begin("AtomicUpdater field extender: " + clazz.getSimpleName());
    // TODO(b/453628974): This is a non-final abstract class, we need to make sure it is created by
    //                    newUpdater to make sure that its the actual class we can assume code
    //                    about.
    var updaterFields =
        ImmutableSet.copyOf(clazz.staticFields(this::isStaticFinalFieldUpdaterField));
    if (!updaterFields.isEmpty()) {
      updaterClasses.put(clazz, updaterFields);
    }
    timing.end();
  }

  private boolean isStaticFinalFieldUpdaterField(DexEncodedField field) {
    return field.isStatic()
        && field.isFinal()
        && field
            .getType()
            .isIdenticalTo(itemFactory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater);
  }

  private DexEncodedField createOffsetField(ProgramField updaterField) {
    assert isStaticFinalFieldUpdaterField(updaterField.getDefinition());
    DexField offsetField =
        itemFactory.createFreshFieldNameWithoutHolder(
            updaterField.getHolderType(),
            itemFactory.longType,
            updaterField.getName().toString() + "$offset",
            field -> updaterField.getHolder().lookupField(field) == null);
    return DexEncodedField.syntheticBuilder()
        .setField(offsetField)
        .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
        .setApiLevel(appView.computedMinApiLevel())
        .build();
  }

  private void addOffsetFieldsToClass(
      DexProgramClass clazz,
      Collection<DexEncodedField> updaterFields,
      ConcurrentHashMap<DexField, DexField> offsetFields,
      ProgramField unsafeInstanceField,
      ProfileCollectionAdditions profiling,
      Timing timing) {
    assert !updaterFields.isEmpty();
    var method = clazz.getProgramClassInitializer();
    assert method != null;

    var updaterToOffsetFieldMap = new HashMap<DexField, DexEncodedField>();
    for (var field : updaterFields) {
      var offsetField = createOffsetField(new ProgramField(clazz, field));
      updaterToOffsetFieldMap.put(field.getReference(), offsetField);
      offsetFields.put(field.getReference(), offsetField.getReference());
    }
    clazz.appendStaticFields(ImmutableList.sortedCopyOf(updaterToOffsetFieldMap.values()));

    extendClassInitializer(unsafeInstanceField, method, updaterToOffsetFieldMap, profiling, timing);
  }

  private void extendClassInitializer(
      ProgramField unsafeInstanceField,
      ProgramMethod classInitializer,
      Map<DexField, DexEncodedField> updaterToOffsetFieldMap,
      ProfileCollectionAdditions profiling,
      Timing timing) {
    var code = classInitializer.getDefinition().getCode();
    assert code.isLirCode();
    var ir = code.asLirCode().buildIR(classInitializer, appView);
    var it = ir.instructionListIterator();
    while (it.hasNext()) {
      var next = it.next();
      UpdaterCreationInfo updaterInfo = resolveNewUpdaterCall(updaterToOffsetFieldMap, next);
      if (updaterInfo == null) {
        continue;
      }
      var newInstructions =
          addOffsetFieldWrite(ir, unsafeInstanceField, updaterInfo, updaterInfo.offsetField);
      // TODO(b/453628974): Add test with catch handler to verify this case.
      it.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(newInstructions, appView.options());
      profiling.addMethodIfContextIsInProfile(
          unsafeInstanceField.getHolder().getProgramClassInitializer(), ir.context());
    }
    LirCode<Integer> newLir =
        new IRToLirFinalizer(appView).finalizeCode(ir, BytecodeMetadataProvider.empty(), timing);
    classInitializer.setCode(newLir, appView);
  }

  private Collection<Instruction> addOffsetFieldWrite(
      IRCode ir,
      ProgramField unsafeInstanceField,
      UpdaterCreationInfo creationInfo,
      DexField offsetField) {
    var newInstructions = new ArrayList<Instruction>(4);
    Instruction unsafeInstance =
        new StaticGet(
            ir.createValue(unsafeInstanceField.getType().toTypeElement(appView)),
            unsafeInstanceField.getReference());
    unsafeInstance.setPosition(creationInfo.position);
    newInstructions.add(unsafeInstance);

    // TODO(b/453628974): Add shorthand to synthesized class to just have one static call.
    var reflectedField =
        new InvokeVirtual(
            itemFactory.classMethods.getDeclaredField,
            ir.createValue(
                TypeElement.fromDexType(itemFactory.fieldType, Nullability.maybeNull(), appView)),
            ImmutableList.of(creationInfo.holdingClass, creationInfo.fieldName));
    reflectedField.setPosition(creationInfo.position);
    newInstructions.add(reflectedField);

    var getOffset =
        new InvokeVirtual(
            objectFieldOffset,
            ir.createValue(
                TypeElement.fromDexType(itemFactory.longType, Nullability.maybeNull(), appView)),
            ImmutableList.of(unsafeInstance.outValue(), reflectedField.outValue()));
    getOffset.setPosition(creationInfo.position);
    newInstructions.add(getOffset);

    StaticPut staticPut = new StaticPut(getOffset.outValue(), offsetField);
    staticPut.setPosition(creationInfo.position);
    newInstructions.add(staticPut);

    return newInstructions;
  }

  /**
   * Returns program information if {@code next} is a {@code StaticPut} to an updater field of a
   * value from a direct call to {@code newUpdater(clazz, fieldType, fieldName)}.
   */
  private UpdaterCreationInfo resolveNewUpdaterCall(
      Map<DexField, DexEncodedField> updaterToOffsetFieldMap, Instruction next) {
    if (!next.isStaticPut()) {
      return null;
    }
    var nextPut = next.asStaticPut();
    DexField updaterField = nextPut.getField();
    if (!updaterToOffsetFieldMap.containsKey(updaterField)) {
      return null;
    }
    var offsetField = updaterToOffsetFieldMap.get(updaterField).getReference();
    Value putInput = nextPut.getFirstOperand();
    if (putInput.isPhi()) {
      return null;
    }
    Instruction input = putInput.definition;
    if (!input.isInvokeStatic()) {
      return null;
    }
    InvokeStatic invokeStatic = input.asInvokeStatic();
    if (!invokeStatic
        .getInvokedMethod()
        .isIdenticalTo(itemFactory.atomicFieldUpdaterMethods.referenceUpdater)) {
      return null;
    }
    assert invokeStatic.arguments().size() == 3;
    var holdingClass = invokeStatic.getFirstArgument();
    var fieldName = invokeStatic.getThirdArgument();
    return new UpdaterCreationInfo(
        holdingClass, offsetField, fieldName, invokeStatic.getPosition());
  }

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    // TODO(b/453628974): List all used (novel) types and avoid the full set of var handle
    //                    desugaring.
    VarHandleDesugaringMethods.registerSynthesizedCodeReferences(factory);
  }

  private static class UpdaterCreationInfo {

    public final Value holdingClass;
    public final DexField offsetField;
    public final Value fieldName;
    public final Position position;

    public UpdaterCreationInfo(
        Value holdingClass, DexField offsetField, Value reflectedFieldName, Position position) {
      this.holdingClass = holdingClass;
      this.offsetField = offsetField;
      this.fieldName = reflectedFieldName;
      this.position = position;
    }
  }
}
