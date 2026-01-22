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
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
 *   volatile T dataField;
 *   static final AtomicReferenceFieldUpdater updater;
 *
 *   static {
 *     ..
 *     updater = AtomicReferenceFieldUpdater.newUpdater(Example.class, T.class, "dataField");
 *     ..
 *   }
 * }
 * </pre>
 *
 * </blockquote>
 *
 * (where a write like above is the only write to the field) and converts them to (in bytecode the
 * arguments are reused not recomputed):
 *
 * <blockquote>
 *
 * <pre>
 * class Example {
 *   volatile T dataField;
 *   static final AtomicReferenceFieldUpdater updater;
 *   static final long updater$offset;
 *
 *   static {
 *     ..
 *     updater = AtomicReferenceFieldUpdater.newUpdater(Example.class, T.class, "dataField");
 *     updater$offset = SyntheticUnsafeClass.unsafe.objectFieldOffset(Example.class.getDeclaredField("dataField"));
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
  private final ExecutorService service;

  private final DexItemFactory itemFactory;
  private final DexMethod objectFieldOffset;
  private final DebugLogs logs;
  private static final String unsafeFieldName = "unsafe";
  private static final String getUnsafeMethodName = "getUnsafe";

  public static void run(
      AppView<AppInfoWithLiveness> appView, ExecutorService service, Timing timing)
      throws ExecutionException {
    new AtomicFieldUpdaterInstrumentor(appView, service).runInternal(timing);
  }

  private AtomicFieldUpdaterInstrumentor(
      AppView<AppInfoWithLiveness> appView, ExecutorService service) {
    this.appView = appView;
    this.service = service;

    itemFactory = appView.dexItemFactory();

    logs = new DebugLogs(appView);

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
  private void runInternal(Timing timing) throws ExecutionException {
    timing.begin("AtomicFieldUpdaterInstrumentor");

    var classesWithAtomics = findClassesWithAtomics(timing);
    if (!classesWithAtomics.isEmpty()) {
      var profiling = ProfileCollectionAdditions.create(appView);
      var unsafeClass = synthesizeUnsafeClass(classesWithAtomics.keySet(), profiling);
      addOffsetFields(classesWithAtomics, unsafeClass, profiling, timing);
      profiling.commit(appView);
    }

    logs.outputLogs();

    timing.end();
  }

  private Multimap<DexProgramClass, UpdaterFieldInfo<Void>> findClassesWithAtomics(Timing timing)
      throws ExecutionException {
    var updaterClassesConcurrent =
        new ConcurrentHashMap<DexProgramClass, Collection<UpdaterFieldInfo<Void>>>();
    ThreadUtils.processItemsThatMatches(
        appView.appInfo().classes(),
        AtomicFieldUpdaterInstrumentor::mightHaveUpdaterFields,
        (clazz, threadTiming) -> findOffsetFields(clazz, updaterClassesConcurrent, threadTiming),
        appView.options(),
        service,
        timing,
        timing.beginMerger("AtomicFieldUpdaterInstrumentor", service));
    return MapUtils.convertMapWithCollectionValuesToMultimap(updaterClassesConcurrent);
  }

  private static boolean mightHaveUpdaterFields(DexProgramClass clazz) {
    // TODO(b/453628974): Check constant pool?
    return clazz.hasClassInitializer() && clazz.hasStaticFields();
  }

  private void findOffsetFields(
      DexProgramClass clazz,
      ConcurrentHashMap<DexProgramClass, Collection<UpdaterFieldInfo<Void>>> updaterClasses,
      Timing timing) {
    timing.begin("AtomicFieldUpdaterInstrumentor: " + clazz.getSimpleName());

    // Find relevant fields that are initialized in their clinit.
    var initialUpdaterFields = new HashSet<DexField>();
    clazz.forEachStaticFieldMatching(
        this::isStaticFinalFieldUpdaterField,
        field -> {
          // Check that fields are constructed with known information, i.e. a single write of a
          // direct
          // and valid call to
          // AtomicReferenceFieldUpdater.newUpdater(ThisClass.class, FieldType.class, "fieldName").
          var f = new ProgramField(clazz, field);
          if (!appView
              .appInfoWithLiveness()
              .isStaticFieldWrittenOnlyInEnclosingStaticInitializer(f)) {
            logs.reportFailure(field.getReference(), "written outside class initializer");
          } else {
            initialUpdaterFields.add(field.getReference());
          }
        });

    // Construct clinit IR.
    var classInitializer = clazz.getProgramClassInitializer();
    assert classInitializer != null;
    var genericCode = classInitializer.getDefinition().getCode();
    assert genericCode.isLirCode();
    var ir = genericCode.asLirCode().buildIR(classInitializer, appView);
    var it = ir.instructionListIterator();

    // Iterate instructions to find singular syntactically obvious writes.
    var fieldInfos = new HashMap<DexField, UpdaterFieldInfo<Void>>();
    while (it.hasNext()) {
      var next = it.next();
      if (!next.isStaticPut()) {
        continue;
      }
      var staticPut = next.asStaticPut();
      var modifiedField = staticPut.getField();
      if (!initialUpdaterFields.contains(modifiedField)) {
        continue;
      }
      if (fieldInfos.containsKey(modifiedField)) {
        logs.reportFailure(modifiedField, "multiple writes");
        fieldInfos.remove(modifiedField);
        initialUpdaterFields.remove(modifiedField);
        continue;
      }
      var updaterInfo = resolveNewUpdaterCall(clazz, modifiedField, staticPut.getFirstOperand());
      if (updaterInfo == null) {
        // Statically unknown call - give up (resolveNewUpdaterCall already reports the reason).
        fieldInfos.remove(modifiedField);
        initialUpdaterFields.remove(modifiedField);
        continue;
      }
      logs.reportSuccessful(modifiedField);
      fieldInfos.put(modifiedField, updaterInfo);
    }

    for (var field : initialUpdaterFields) {
      if (!fieldInfos.containsKey(field)) {
        logs.reportFailure(field, "found no field writes");
      }
    }

    // Store information in concurrent collection.
    if (!fieldInfos.isEmpty()) {
      // Assert might allow concurrent writes, but this is just for sanity checking.
      assert !updaterClasses.containsKey(clazz);
      updaterClasses.put(clazz, fieldInfos.values());
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

  /**
   * Returns program information if {@code updaterCall} is a direct call to {@code
   * newUpdater(ThisClass.class, FieldType.class, "fieldName")} of a valid field.
   */
  private UpdaterFieldInfo<Void> resolveNewUpdaterCall(
      DexProgramClass clazz, DexField updaterField, Value updaterCall) {
    if (updaterCall.isPhi()) {
      logs.reportFailure(updaterField, "initialized by phi function");
      return null;
    }
    Instruction input = updaterCall.definition;
    if (!input.isInvokeStatic()) {
      logs.reportFailure(updaterField, "not initialized by static call");
      return null;
    }
    InvokeStatic invokeStatic = input.asInvokeStatic();
    if (!invokeStatic
        .getInvokedMethod()
        .isIdenticalTo(itemFactory.atomicFieldUpdaterMethods.referenceUpdater)) {
      logs.reportFailure(updaterField, "not initialized by newUpdater call");
      return null;
    }
    assert invokeStatic.arguments().size() == 3;
    var holderValue = invokeStatic.getFirstArgument();
    if (holderValue.isPhi()) {
      logs.reportFailure(updaterField, "newUpdater(HERE, _, _) is defined by phi function");
      return null;
    }
    var holderIns = holderValue.definition;
    if (!holderIns.isConstClass()) {
      logs.reportFailure(updaterField, "newUpdater(HERE, _, _) is not a constant class");
      return null;
    }
    var holder = holderIns.asConstClass().getType();
    if (!holder.isIdenticalTo(clazz.getType())) {
      logs.reportFailure(updaterField, "newUpdater(HERE, _, _) is not the current class");
      return null;
    }
    var fieldTypeValue = invokeStatic.getSecondArgument();
    if (fieldTypeValue.isPhi()) {
      logs.reportFailure(updaterField, "newUpdater(_, HERE, _) is defined by phi function");
      return null;
    }
    var fieldTypeIns = fieldTypeValue.definition;
    if (!fieldTypeIns.isConstClass()) {
      logs.reportFailure(updaterField, "newUpdater(_, HERE, _) is not a constant class");
      return null;
    }
    var fieldType = fieldTypeIns.asConstClass().getType();
    var fieldNameValue = invokeStatic.getThirdArgument();
    if (fieldNameValue.isPhi()) {
      logs.reportFailure(updaterField, "newUpdater(_, _, HERE) is defined by phi function");
      return null;
    }
    var fieldNameIns = fieldNameValue.definition;
    // TODO(b/453628974): Add test with an invalid field name (this is then a const string
    // instruction).
    if (!fieldNameIns.isDexItemBasedConstString()) {
      logs.reportFailure(updaterField, "newUpdater(_, _, HERE) is not a dex-item-based string");
      return null;
    }
    var fieldNameReference = fieldNameIns.asDexItemBasedConstString().getItem();
    if (!fieldNameReference.isDexField()) {
      logs.reportFailure(updaterField, "newUpdater(_, _, HERE) is a dex reference to a non-field");
      return null;
    }
    var reflectedFieldReference = fieldNameReference.asDexField();
    if (!reflectedFieldReference.getHolderType().isIdenticalTo(clazz.getType())) {
      logs.reportFailure(
          updaterField, "newUpdater(_, _, HERE) is a dex reference to a field of another class.");
      return null;
    }
    if (!reflectedFieldReference.type.isIdenticalTo(fieldType)) {
      logs.reportFailure(updaterField, "newUpdater(_, TYPE, FIELD) FIELD's type and TYPE disagree");
      return null;
    }
    var reflectedField = clazz.lookupProgramField(reflectedFieldReference);
    if (reflectedField == null) {
      logs.reportFailure(updaterField, "newUpdater(..) does not validly refer to a field");
      return null;
    }
    if (!reflectedField.getAccessFlags().isVolatile()) {
      logs.reportFailure(updaterField, "reflected field is not volatile");
      return null;
    }
    // TODO(b/453628974): Assert that newUpdater has no side effects in this case.
    return UpdaterFieldInfo.create(
        updaterField, holderValue, fieldNameValue, invokeStatic.getPosition());
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

  private static DexProgramClass getDeterministicContext(
      Collection<DexProgramClass> classesWithAtomics) {
    assert !classesWithAtomics.isEmpty();
    return Collections.min(classesWithAtomics);
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

  private <T> void addOffsetFields(
      Multimap<DexProgramClass, UpdaterFieldInfo<T>> classesWithAtomics,
      UnsafeClassInfo unsafeClass,
      ProfileCollectionAdditions profiling,
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
        timing.beginMerger("AtomicFieldUpdaterInstrumentor", service));
    if (!profiling.isNop()) {
      profiling.addMethodIfContextIsInProfile(
          unsafeClass.getUnsafeMethod, unsafeClass.classInitializer);
    }
    var builder = FieldAccessInfoCollectionModifier.builder();
    offsetFields.values().forEach(builder::addField);
    builder.build().modify(appView);
  }

  private DexEncodedField createOffsetField(
      DexProgramClass clazz, UpdaterFieldInfo<?> updaterFieldInfo) {
    DexField offsetField =
        itemFactory.createFreshFieldNameWithoutHolder(
            clazz.getType(),
            itemFactory.longType,
            updaterFieldInfo.field.name.toString() + "$offset",
            field -> clazz.lookupField(field) == null);
    return DexEncodedField.syntheticBuilder()
        .setField(offsetField)
        .setAccessFlags(FieldAccessFlags.createPublicStaticFinalSynthetic())
        .setApiLevel(appView.computedMinApiLevel())
        .build();
  }

  private <T> void addOffsetFieldsToClass(
      DexProgramClass clazz,
      Collection<UpdaterFieldInfo<T>> updaterFields,
      ConcurrentHashMap<DexField, DexField> offsetFields,
      ProgramField unsafeInstanceField,
      ProfileCollectionAdditions profiling,
      Timing timing) {
    assert !updaterFields.isEmpty();
    var method = clazz.getProgramClassInitializer();
    assert method != null;

    var extendedUpdaterFields = new HashMap<DexField, UpdaterFieldInfo<DexField>>();
    var fieldsToAdd = new ArrayList<DexEncodedField>(updaterFields.size());
    for (var updaterFieldInfo : updaterFields) {
      var offsetField = createOffsetField(clazz, updaterFieldInfo);
      extendedUpdaterFields.put(
          updaterFieldInfo.field, updaterFieldInfo.copyWithOffsetField(offsetField.getReference()));
      offsetFields.put(updaterFieldInfo.field, offsetField.getReference());
      fieldsToAdd.add(offsetField);
    }
    Collections.sort(fieldsToAdd);
    clazz.appendStaticFields(fieldsToAdd);

    extendClassInitializer(unsafeInstanceField, method, extendedUpdaterFields, profiling, timing);
  }

  private void extendClassInitializer(
      ProgramField unsafeInstanceField,
      ProgramMethod classInitializer,
      Map<DexField, UpdaterFieldInfo<DexField>> updaterFields,
      ProfileCollectionAdditions profiling,
      Timing timing) {
    var code = classInitializer.getDefinition().getCode();
    assert code.isLirCode();
    var ir = code.asLirCode().buildIR(classInitializer, appView);
    var it = ir.instructionListIterator();
    while (it.hasNext()) {
      var next = it.next();
      if (!next.isStaticPut()) {
        continue;
      }
      var staticPut = next.asStaticPut();
      var updaterFieldInfo = updaterFields.get(staticPut.getField());
      if (updaterFieldInfo == null) {
        continue;
      }
      var newInstructions = addOffsetFieldWrite(ir, unsafeInstanceField, updaterFieldInfo);
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
      IRCode ir, ProgramField unsafeInstanceField, UpdaterFieldInfo<DexField> creationInfo) {
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

    StaticPut staticPut = new StaticPut(getOffset.outValue(), creationInfo.offsetField);
    staticPut.setPosition(creationInfo.position);
    newInstructions.add(staticPut);

    return newInstructions;
  }

  public static void registerSynthesizedCodeReferences(DexItemFactory factory) {
    // TODO(b/453628974): List all used (novel) types and avoid the full set of var handle
    //                    desugaring.
    VarHandleDesugaringMethods.registerSynthesizedCodeReferences(factory);
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

  // The OffsetField type parameter is used to track the nullness of offsetField statically
  // (Either Void or DexField).
  private static class UpdaterFieldInfo<OffsetField> {

    public final DexField field;

    public final Value holdingClass;
    public final Value fieldName;
    public final Position position;
    public final OffsetField offsetField;

    private UpdaterFieldInfo(
        DexField field,
        Value holdingClass,
        Value reflectedFieldName,
        Position position,
        OffsetField offsetField) {
      this.field = field;
      this.holdingClass = holdingClass;
      this.fieldName = reflectedFieldName;
      this.position = position;
      this.offsetField = offsetField;
    }

    public static UpdaterFieldInfo<Void> create(
        DexField field, Value holdingClass, Value reflectedFieldName, Position position) {
      return new UpdaterFieldInfo<>(field, holdingClass, reflectedFieldName, position, null);
    }

    public <T> UpdaterFieldInfo<T> copyWithOffsetField(T offsetField) {
      return new UpdaterFieldInfo<>(field, holdingClass, fieldName, position, offsetField);
    }
  }

  /** Thread-safe log collector. */
  private static class DebugLogs {

    private final ConcurrentHashMap<DexField, String> logs;
    private final AppView<?> appView;

    public DebugLogs(AppView<?> appView) {
      this.appView = appView;
      if (appView.options().getTestingOptions().enableAtomicFieldUpdaterInstrumentorDebugLogs) {
        logs = new ConcurrentHashMap<>();
      } else {
        logs = null;
      }
    }

    public void reportFailure(DexField field, String reason) {
      if (logs != null) {
        assert !logs.containsKey(field);
        logs.put(field, "Cannot instrument " + field + ": " + reason);
      }
    }

    public void reportSuccessful(DexField field) {
      if (logs != null) {
        assert !logs.containsKey(field);
        logs.put(field, "Can instrument    " + field);
      }
    }

    public void outputLogs() {
      if (logs != null && !logs.isEmpty()) {
        var reporter = appView.reporter();
        var sb = new StringBuilder();
        logs.forEach((field, reason) -> sb.append(reason).append(System.lineSeparator()));
        reporter.info(sb.toString());
      }
    }
  }
}
