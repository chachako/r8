// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.FieldCollection.FieldCollectionFactory;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.MethodCollection.MethodCollectionFactory;
import com.android.tools.r8.kotlin.KotlinClassLevelInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DexClasspathClass extends DexClass
    implements ClasspathClass, Supplier<DexClasspathClass>, StructuralItem<DexClasspathClass> {

  public DexClasspathClass(
      DexType type,
      ProgramResource.Kind kind,
      Origin origin,
      ClassAccessFlags accessFlags,
      DexType superType,
      DexTypeList interfaces,
      DexString sourceFile,
      NestHostClassAttribute nestHost,
      List<NestMemberClassAttribute> nestMembers,
      List<PermittedSubclassAttribute> permittedSubclasses,
      List<RecordComponentInfo> recordComponents,
      EnclosingMethodAttribute enclosingMember,
      List<InnerClassAttribute> innerClasses,
      ClassSignature classSignature,
      DexAnnotationSet annotations,
      FieldCollectionFactory fieldCollectionFactory,
      MethodCollectionFactory methodCollectionFactory,
      boolean skipNameValidationForTesting) {
    super(
        sourceFile,
        interfaces,
        accessFlags,
        superType,
        type,
        fieldCollectionFactory,
        methodCollectionFactory,
        nestHost,
        nestMembers,
        permittedSubclasses,
        recordComponents,
        enclosingMember,
        innerClasses,
        classSignature,
        annotations,
        origin,
        skipNameValidationForTesting);
    // Allow moving synthetic classes to the classpath (R8 partial only).
    assert kind == null || kind == Kind.CF
        : "Invalid kind " + kind + " for class-path class " + type;
  }

  public static DexClasspathClass toClasspathClass(DexProgramClass programClass) {
    return new DexClasspathClass(
        programClass.getType(),
        programClass.getOriginKind(),
        programClass.getOrigin(),
        programClass.getAccessFlags(),
        programClass.getSuperType(),
        programClass.getInterfaces(),
        programClass.getSourceFile(),
        programClass.getNestHostClassAttribute(),
        programClass.getNestMembersClassAttributes(),
        programClass.getPermittedSubclassAttributes(),
        programClass.getRecordComponents(),
        programClass.getEnclosingMethodAttribute(),
        programClass.getInnerClasses(),
        programClass.getClassSignature(),
        programClass.annotations(),
        FieldCollectionFactory.fromFieldCollection(programClass.getFieldCollection()),
        MethodCollectionFactory.fromMethodCollection(programClass.getMethodCollection()),
        false);
  }

  public static Map<DexType, DexClasspathClass> toClasspathClasses(
      Collection<DexProgramClass> programClasses) {
    Map<DexType, DexClasspathClass> classpathClasses = new IdentityHashMap<>(programClasses.size());
    for (DexProgramClass programClass : programClasses) {
      classpathClasses.put(programClass.getType(), toClasspathClass(programClass));
    }
    return classpathClasses;
  }

  @Override
  public void accept(
      Consumer<DexProgramClass> programClassConsumer,
      Consumer<DexClasspathClass> classpathClassConsumer,
      Consumer<DexLibraryClass> libraryClassConsumer) {
    classpathClassConsumer.accept(this);
  }

  public void forEachClasspathMethod(Consumer<? super ClasspathMethod> consumer) {
    forEachClasspathMethodMatching(alwaysTrue(), consumer);
  }

  public void forEachClasspathMethodMatching(
      Predicate<DexEncodedMethod> predicate, Consumer<? super ClasspathMethod> consumer) {
    methodCollection.forEachMethodMatching(
        predicate, method -> consumer.accept(new ClasspathMethod(this, method)));
  }

  @Override
  public String toString() {
    return type.toString() + "(classpath class)";
  }

  @Override
  public void addDependencies(MixedSectionCollection collector) {
    // Should never happen but does not harm.
    assert false;
  }

  @Override
  public boolean isClasspathClass() {
    return true;
  }

  @Override
  public DexClasspathClass asClasspathClass() {
    return this;
  }

  @Override
  public DexClasspathClass asClasspathOrLibraryClass() {
    return this;
  }

  public static DexClasspathClass asClasspathClassOrNull(DexClass clazz) {
    return clazz != null ? clazz.asClasspathClass() : null;
  }

  @Override
  public boolean isNotProgramClass() {
    return true;
  }

  @Override
  public KotlinClassLevelInfo getKotlinInfo() {
    throw new Unreachable("Kotlin info on classpath class is not supported yet.");
  }

  @Override
  public ClassKind<DexClasspathClass> getKind() {
    return ClassKind.CLASSPATH;
  }

  @Override
  public DexClasspathClass get() {
    return this;
  }

  @Override
  boolean internalClassOrInterfaceMayHaveInitializationSideEffects(
      AppView<?> appView,
      DexClass initialAccessHolder,
      Predicate<DexType> ignore,
      Set<DexType> seen) {
    if (!seen.add(getType()) || ignore.test(getType())) {
      return false;
    }
    return !isInterface() || appView.options().classpathInterfacesMayHaveStaticInitialization;
  }

  @Override
  public DexClasspathClass self() {
    return this;
  }

  @Override
  public StructuralMapping<DexClasspathClass> getStructuralMapping() {
    return DexClasspathClass::specify;
  }

  private static void specify(StructuralSpecification<DexClasspathClass, ?> spec) {
    spec.withItem(DexClass::getType)
        .withItem(DexClass::getSuperType)
        .withItem(DexClass::getInterfaces)
        .withItem(DexClass::getAccessFlags)
        .withNullableItem(DexClass::getSourceFile)
        .withNullableItem(DexClass::getNestHostClassAttribute)
        .withItemCollection(DexClass::getNestMembersClassAttributes)
        .withItem(DexDefinition::annotations)
        // TODO(b/158159959): Make signatures structural.
        .withAssert(c -> c.classSignature == ClassSignature.noSignature())
        .withItemCollection(DexClass::allFieldsSorted)
        .withItemCollection(DexClass::allMethodsSorted);
  }
}
