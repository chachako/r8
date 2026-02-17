// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility.Reporter.reportInfo;

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
import com.android.tools.r8.graph.DexType;
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
import com.android.tools.r8.ir.conversion.passes.AtomicFieldUpdaterOptimizer;
import com.android.tools.r8.ir.conversion.passes.AtomicFieldUpdaterOptimizer.AtomicFieldUpdaterInfo;
import com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility.Event;
import com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility.Reason;
import com.android.tools.r8.ir.synthetic.AtomicFieldUpdaterOptimizationMethods;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.synthesis.SyntheticProgramClassBuilder;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
 * SyntheticUnsafeClass.unsafe.compareAndSwapObject(instance, updater$offset, expect, update)
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
  private static final String unsafeFieldName = "unsafe";
  private static final String getUnsafeMethodName = "getUnsafe";
  private static final String getAndSetMethodName = "getAndSet";

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
      var unsafeInstanceField = unsafeClass.unsafeInstanceField.getReference();
      var getAndSetMethod = unsafeClass.getAndSetMethod.getReference();
      var instrumentations = addOffsetFields(classesWithAtomics, unsafeClass, profiling, timing);

      profiling.commit(appView);
      appView.setAtomicFieldUpdaterInstrumentorInfo(
          new AtomicFieldUpdaterInstrumentorInfo(
              instrumentations, unsafeInstanceField, getAndSetMethod));
    }

    timing.end();
  }

  private Map<DexProgramClass, ClassWithAtomicsInfo> findClassesWithAtomics(Timing timing)
      throws ExecutionException {
    var updaterClassesConcurrent = new ConcurrentHashMap<DexProgramClass, ClassWithAtomicsInfo>();
    ThreadUtils.processItemsThatMatches(
        appView.appInfo().classes(),
        AtomicFieldUpdaterInstrumentor::mightHaveUpdaterFields,
        (clazz, threadTiming) -> findUpdaterFields(clazz, updaterClassesConcurrent, threadTiming),
        appView.options(),
        service,
        timing,
        timing.beginMerger("AtomicFieldUpdaterInstrumentor", service));
    return ImmutableMap.copyOf(updaterClassesConcurrent);
  }

  private static boolean mightHaveUpdaterFields(DexProgramClass clazz) {
    // TODO(b/453628974): Check constant pool?
    return clazz.hasClassInitializer() && clazz.hasStaticFields();
  }

  private void findUpdaterFields(
      DexProgramClass clazz,
      ConcurrentHashMap<DexProgramClass, ClassWithAtomicsInfo> updaterClasses,
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
            reportInfo(
                appView,
                new Event.CannotInstrument(field.getReference()),
                Reason.WRITTEN_OUTSIDE_CLASS_INITIALIZER);
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
        reportInfo(appView, new Event.CannotInstrument(modifiedField), Reason.MULTIPLE_WRITES);
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
      reportInfo(appView, new Event.CanInstrument(modifiedField));
      fieldInfos.put(modifiedField, updaterInfo);
    }

    // All fields should be invalid (removed from initialUpdaterFields) or valid (in fieldInfos)
    assert fieldInfos.keySet().containsAll(initialUpdaterFields);

    // Store information in concurrent collection.
    if (!fieldInfos.isEmpty()) {
      // Assert might allow concurrent writes, but this is just for sanity checking.
      assert !updaterClasses.containsKey(clazz);
      updaterClasses.put(clazz, new ClassWithAtomicsInfo(ir, fieldInfos.values()));
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
      reportInfo(
          appView, new Event.CannotInstrument(updaterField), Reason.UPDATER_INITIALIZED_BY_PHI);
      return null;
    }
    Instruction input = updaterCall.definition;
    if (!input.isInvokeStatic()) {
      reportInfo(
          appView,
          new Event.CannotInstrument(updaterField),
          Reason.UPDATER_NOT_INITIALIZED_BY_INVOKE_STATIC);
      return null;
    }
    InvokeStatic invokeStatic = input.asInvokeStatic();
    if (!invokeStatic
        .getInvokedMethod()
        .isIdenticalTo(itemFactory.atomicFieldUpdaterMethods.referenceUpdater)) {
      reportInfo(
          appView,
          new Event.CannotInstrument(updaterField),
          Reason.UPDATER_NOT_INITIALIZED_BY_NEW_UPDATER);
      return null;
    }
    assert invokeStatic.arguments().size() == 3;
    var holderValue = invokeStatic.getFirstArgument();
    if (holderValue.isPhi()) {
      reportInfo(
          appView,
          new Event.CannotInstrument(updaterField),
          Reason.UPDATER_HOLDER_INITIALIZED_BY_PHI);
      return null;
    }
    var holderIns = holderValue.definition;
    if (!holderIns.isConstClass()) {
      reportInfo(
          appView,
          new Event.CannotInstrument(updaterField),
          Reason.UPDATER_HOLDER_NOT_CONSTANT_CLASS);
      return null;
    }
    var holder = holderIns.asConstClass().getType();
    if (!holder.isIdenticalTo(clazz.getType())) {
      reportInfo(
          appView,
          new Event.CannotInstrument(updaterField),
          Reason.UPDATER_HOLDER_IS_OUTSIDE_CLASS);
      return null;
    }
    var fieldTypeValue = invokeStatic.getSecondArgument();
    if (fieldTypeValue.isPhi()) {
      reportInfo(
          appView,
          new Event.CannotInstrument(updaterField),
          Reason.UPDATER_FIELD_TYPE_INITIALIZED_BY_PHI);
      return null;
    }
    var fieldTypeIns = fieldTypeValue.definition;
    if (!fieldTypeIns.isConstClass()) {
      reportInfo(
          appView,
          new Event.CannotInstrument(updaterField),
          Reason.UPDATER_FIELD_TYPE_NOT_CONSTANT_CLASS);
      return null;
    }
    var fieldType = fieldTypeIns.asConstClass().getType();
    var fieldNameValue = invokeStatic.getThirdArgument();
    if (fieldNameValue.isPhi()) {
      reportInfo(
          appView,
          new Event.CannotInstrument(updaterField),
          Reason.UPDATER_VALUE_INITIALIZED_BY_PHI);
      return null;
    }
    var fieldNameIns = fieldNameValue.definition;
    ProgramField reflectedField;
    if (fieldNameIns.isDexItemBasedConstString()) {
      var fieldNameReference = fieldNameIns.asDexItemBasedConstString().getItem();
      assert fieldNameReference.isDexField();
      var reflectedFieldReference = fieldNameReference.asDexField();
      assert reflectedFieldReference.getHolderType().isIdenticalTo(clazz.getType());
      assert reflectedFieldReference.type.isIdenticalTo(fieldType);
      reflectedField = clazz.lookupProgramField(reflectedFieldReference);
    } else if (fieldNameIns.isConstString()) {
      var fieldNameString = fieldNameIns.asConstString().getValue();
      reflectedField =
          clazz.lookupProgramField(
              itemFactory.createField(clazz.getType(), fieldType, fieldNameString));
    } else {
      reportInfo(
          appView,
          new Event.CannotInstrument(updaterField),
          Reason.UPDATER_FIELD_NOT_CONSTANT_STRING);
      return null;
    }
    if (reflectedField == null) {
      reportInfo(
          appView, new Event.CannotInstrument(updaterField), Reason.NEW_UPDATER_INVALID_FIELD);
      return null;
    }
    if (!reflectedField.getAccessFlags().isVolatile()) {
      reportInfo(
          appView,
          new Event.CannotInstrument(updaterField),
          Reason.UPDATER_REFLECTS_NON_VOLATILE_FIELD);
      return null;
    }
    // TODO(b/453628974): Assert that newUpdater has no side effects in this case.
    return UpdaterFieldInfo.create(
        updaterField, fieldType, holderValue, fieldNameValue, invokeStatic.getPosition());
  }

  private UnsafeClassInfo synthesizeUnsafeClass(
      Set<DexProgramClass> classesWithAtomics, ProfileCollectionAdditions profiling) {
    // TODO(b/453628974): This code breaks for dex 4.0.4.
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
    var getAndSetMethod =
        unsafeClass.lookupProgramMethod(
            itemFactory.createMethod(
                unsafeClass.getType(),
                itemFactory.createProto(
                    itemFactory.objectType,
                    itemFactory.unsafeType,
                    itemFactory.objectType,
                    itemFactory.longType,
                    itemFactory.objectType),
                getAndSetMethodName));
    assert getAndSetMethod != null;
    if (!profiling.isNop()) {
      for (var clazz : classesWithAtomics) {
        // TODO(b/453628974): Break after first callback trigger.
        profiling.applyIfContextIsInProfile(
            clazz, builder -> builder.addClassRule(unsafeClass.getType()));
      }
    }
    appView.rebuildAppInfo();
    return new UnsafeClassInfo(classInitializer, unsafeField, getUnsafeMethod, getAndSetMethod);
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
            // Avoid superfluous assert on API in the build call when API modeling is disabled.
            .disableAndroidApiLevelCheckIf(
                !appView.options().apiModelingOptions().isApiModelingEnabled())
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
                      AtomicFieldUpdaterOptimizationMethods
                          .AtomicFieldUpdaterOptimizationMethods_getUnsafe(itemFactory, method));
          if (appView.options().isGeneratingClassFiles()) {
            methodBuilder.setClassFileVersion(
                appView.options().requiredCfVersionForConstClassInstructions());
          }
        });
    DexMethod getAndSetMethod =
        itemFactory.createMethod(
            builder.getType(),
            itemFactory.createProto(
                itemFactory.objectType,
                itemFactory.unsafeType,
                itemFactory.objectType,
                itemFactory.longType,
                itemFactory.objectType),
            getAndSetMethodName);
    builder.addMethod(
        methodBuilder ->
            methodBuilder
                .setName(getAndSetMethod.name)
                .setProto(getAndSetMethod.proto)
                .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                .setApiLevelForDefinition(appView.computedMinApiLevel())
                .setApiLevelForCode(appView.computedMinApiLevel())
                .setCode(
                    method ->
                        AtomicFieldUpdaterOptimizationMethods
                            .AtomicFieldUpdaterOptimizationMethods_getAndSet(itemFactory, method)));
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

  private Map<DexType, Map<DexField, AtomicFieldUpdaterOptimizer.AtomicFieldUpdaterInfo>>
      addOffsetFields(
          Map<DexProgramClass, ClassWithAtomicsInfo> classesWithAtomics,
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

    Map<DexType, Map<DexField, AtomicFieldUpdaterOptimizer.AtomicFieldUpdaterInfo>>
        instrumentations = new HashMap<>(classesWithAtomics.size());
    classesWithAtomics.forEach(
        (clazz, classInfo) -> {
          var infos = classInfo.fields;
          var fields = new HashMap<DexField, AtomicFieldUpdaterInfo>(infos.size());
          for (var info : infos) {
            fields.put(
                info.field,
                new AtomicFieldUpdaterInfo(
                    info.field.holder, info.reflectedFieldType, offsetFields.get(info.field)));
          }
          instrumentations.put(clazz.getType(), fields);
        });
    return instrumentations;
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
        // Avoid superfluous API assert in build when API modeling is disabled.
        .disableAndroidApiLevelCheckIf(
            !appView.options().apiModelingOptions().isApiModelingEnabled())
        .build();
  }

  private void addOffsetFieldsToClass(
      DexProgramClass clazz,
      ClassWithAtomicsInfo classInfo,
      ConcurrentHashMap<DexField, DexField> offsetFields,
      ProgramField unsafeInstanceField,
      ProfileCollectionAdditions profiling,
      Timing timing) {
    var updaterFields = classInfo.fields;
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

    extendClassInitializer(
        unsafeInstanceField, method, classInfo, extendedUpdaterFields, profiling, timing);
  }

  private void extendClassInitializer(
      ProgramField unsafeInstanceField,
      ProgramMethod classInitializer,
      ClassWithAtomicsInfo classInfo,
      Map<DexField, UpdaterFieldInfo<DexField>> updaterFields,
      ProfileCollectionAdditions profiling,
      Timing timing) {
    var code = classInitializer.getDefinition().getCode();
    assert code.isLirCode();
    var ir = classInfo.code;
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
            ImmutableList.of(creationInfo.holderValue, creationInfo.fieldName));
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
    AtomicFieldUpdaterOptimizationMethods.registerSynthesizedCodeReferences(factory);
  }

  private static class UnsafeClassInfo {

    public final ProgramMethod classInitializer;
    public final ProgramField unsafeInstanceField;
    public final ProgramMethod getUnsafeMethod;
    public final ProgramMethod getAndSetMethod;

    private UnsafeClassInfo(
        ProgramMethod classInitializer,
        ProgramField unsafeInstanceField,
        ProgramMethod getUnsafeMethod,
        ProgramMethod getAndSetMethod) {
      this.classInitializer = classInitializer;
      this.unsafeInstanceField = unsafeInstanceField;
      this.getUnsafeMethod = getUnsafeMethod;
      this.getAndSetMethod = getAndSetMethod;
    }
  }

  // The OffsetField type parameter is used to track the nullness of offsetField statically
  // (Either Void or DexField).
  private static class UpdaterFieldInfo<OffsetField> {

    public final DexField field;
    public final DexType reflectedFieldType;
    public final Value holderValue;
    public final Value fieldName;
    public final Position position;
    public final OffsetField offsetField;

    private UpdaterFieldInfo(
        DexField field,
        DexType reflectedFieldType,
        Value holderValue,
        Value reflectedFieldName,
        Position position,
        OffsetField offsetField) {
      this.field = field;
      this.reflectedFieldType = reflectedFieldType;
      this.holderValue = holderValue;
      this.fieldName = reflectedFieldName;
      this.position = position;
      this.offsetField = offsetField;
    }

    public static UpdaterFieldInfo<Void> create(
        DexField field,
        DexType reflectedFieldType,
        Value holdingClass,
        Value reflectedFieldName,
        Position position) {
      return new UpdaterFieldInfo<>(
          field, reflectedFieldType, holdingClass, reflectedFieldName, position, null);
    }

    public <T> UpdaterFieldInfo<T> copyWithOffsetField(T offsetField) {
      return new UpdaterFieldInfo<>(
          field, reflectedFieldType, holderValue, fieldName, position, offsetField);
    }
  }

  private static class ClassWithAtomicsInfo {
    public final IRCode code;
    public final Collection<UpdaterFieldInfo<Void>> fields;

    private ClassWithAtomicsInfo(IRCode code, Collection<UpdaterFieldInfo<Void>> fields) {
      this.code = code;
      this.fields = fields;
    }
  }

  public static class AtomicFieldUpdaterInstrumentorInfo {

    private final Map<DexType, Map<DexField, AtomicFieldUpdaterInfo>> instrumentations;
    private final DexField unsafeInstanceField;
    private final DexMethod getAndSetMethod;

    public AtomicFieldUpdaterInstrumentorInfo(
        Map<DexType, Map<DexField, AtomicFieldUpdaterInfo>> instrumentations,
        DexField unsafeInstanceField,
        DexMethod getAndSetMethod) {
      assert instrumentations != null;
      assert unsafeInstanceField != null;
      assert getAndSetMethod != null;
      this.instrumentations = instrumentations;
      this.unsafeInstanceField = unsafeInstanceField;
      this.getAndSetMethod = getAndSetMethod;
    }

    public Map<DexType, Map<DexField, AtomicFieldUpdaterInfo>> getInstrumentations() {
      return instrumentations;
    }

    public DexField getUnsafeInstanceField() {
      return unsafeInstanceField;
    }

    public DexMethod getGetAndSetMethod() {
      return getAndSetMethod;
    }
  }
}
