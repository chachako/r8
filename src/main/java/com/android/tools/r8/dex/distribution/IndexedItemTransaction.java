// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.distribution;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.synthesis.SyntheticNaming;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class IndexedItemTransaction implements IndexedItemCollection {

  private final AppView<?> appView;
  private final VirtualFile.VirtualFileIndexedItemCollection base;
  private final LensCodeRewriterUtils rewriter;

  private final Set<DexProgramClass> classes = new LinkedHashSet<>();
  final Set<DexField> fields = new LinkedHashSet<>();
  final Set<DexMethod> methods = new LinkedHashSet<>();
  final Set<DexType> types = new LinkedHashSet<>();
  final Map<DexProto, DexString> protos = new LinkedHashMap<>();
  final Set<DexString> strings = new LinkedHashSet<>();
  final Set<DexCallSite> callSites = new LinkedHashSet<>();
  final Set<DexMethodHandle> methodHandles = new LinkedHashSet<>();

  private final ClassUseCollector indexedItemsReferencedFromClassesInTransaction;

  public IndexedItemTransaction(
      VirtualFile.VirtualFileIndexedItemCollection base, AppView<?> appView) {
    this.appView = appView;
    this.base = base;
    this.rewriter = new LensCodeRewriterUtils(appView, true);
    this.indexedItemsReferencedFromClassesInTransaction =
        appView.options().testing.calculateItemUseCountInDex
            ? new IndexedItemsUsedByClassesInTransaction(appView, rewriter, base, this)
            : new EmptyIndexedItemUsedByClasses();
  }

  public LensCodeRewriterUtils getRewriter() {
    return rewriter;
  }

  private <T extends DexItem> boolean maybeInsert(T item, Predicate<T> adder, Set<T> baseSet) {
    return maybeInsert(item, adder, baseSet, true);
  }

  private <T extends DexItem> boolean maybeInsert(
      T item, Predicate<T> adder, Set<T> baseSet, boolean requireCurrentClass) {
    if (baseSet.contains(item)) {
      return false;
    }
    boolean added = adder.test(item);
    assert !added || !requireCurrentClass || classes.contains(currentClass);
    return added;
  }

  public void addClassAndDependencies(DexProgramClass clazz) {
    clazz.collectIndexedItems(appView, this, rewriter);
    addClassDone();
    indexedItemsReferencedFromClassesInTransaction.collectClassDependencies(clazz);
    indexedItemsReferencedFromClassesInTransaction.collectClassDependenciesDone();
  }

  DexProgramClass currentClass = null;

  @Override
  public boolean addClass(DexProgramClass dexProgramClass) {
    assert currentClass == null;
    currentClass = dexProgramClass;
    return maybeInsert(dexProgramClass, classes::add, base.classes);
  }

  public void addClassDone() {
    currentClass = null;
  }

  @Override
  public boolean addField(DexField field) {
    assert currentClass != null;
    return maybeInsert(field, fields::add, base.fields);
  }

  @Override
  public boolean addMethod(DexMethod method) {
    assert currentClass != null;
    return maybeInsert(method, methods::add, base.methods);
  }

  @Override
  public boolean addString(DexString string) {
    // Only marker strings can be added outside a class context.
    assert currentClass != null || string.startsWith("~~");
    return maybeInsert(string, strings::add, base.strings, false);
  }

  @Override
  public boolean addProto(DexProto proto) {
    assert currentClass != null;
    return maybeInsert(
        proto,
        p ->
            VirtualFile.addProtoWithShorty(
                p, protos, base.shortyCache, this::addString, appView.dexItemFactory()),
        base.protos.keySet());
  }

  @Override
  public boolean addType(DexType type) {
    assert currentClass != null;
    assert SyntheticNaming.verifyNotInternalSynthetic(type);
    return maybeInsert(type, types::add, base.types);
  }

  @Override
  public boolean addCallSite(DexCallSite callSite) {
    assert currentClass != null;
    return maybeInsert(callSite, callSites::add, base.callSites);
  }

  @Override
  public boolean addMethodHandle(DexMethodHandle methodHandle) {
    assert currentClass != null;
    return maybeInsert(methodHandle, methodHandles::add, base.methodHandles);
  }

  public int getNumberOfMethods() {
    return methods.size() + base.getNumberOfMethods();
  }

  public int getNumberOfClasses() {
    return classes.size() + base.classes.size();
  }

  public int getNumberOfTypes() {
    return types.size() + base.types.size();
  }

  public int getNumberOfFields() {
    return fields.size() + base.getNumberOfFields();
  }

  private <T extends DexItem> void commitItemsIn(Set<T> set, Function<T, Boolean> hook) {
    set.forEach(
        (item) -> {
          boolean newlyAdded = hook.apply(item);
          assert newlyAdded;
        });
    set.clear();
  }

  public void commit() {
    commitItemsIn(classes, base::addClass);
    commitItemsIn(fields, base::addField);
    commitItemsIn(methods, base::addMethod);
    // The shorty strings are maintained in the transaction strings, so don't add them twice.
    commitItemsIn(protos.keySet(), base::addProtoWithoutShorty);
    commitItemsIn(types, base::addType);
    commitItemsIn(strings, base::addString);
    commitItemsIn(callSites, base::addCallSite);
    commitItemsIn(methodHandles, base::addMethodHandle);

    if (appView.options().testing.calculateItemUseCountInDex) {
      transferUsedBy(
          indexedItemsReferencedFromClassesInTransaction.getStringsUse(), base.stringsUse);
      transferUsedBy(indexedItemsReferencedFromClassesInTransaction.getTypesUse(), base.typesUse);
      transferUsedBy(indexedItemsReferencedFromClassesInTransaction.getProtosUse(), base.protosUse);
      transferUsedBy(indexedItemsReferencedFromClassesInTransaction.getFieldsUse(), base.fieldsUse);
      transferUsedBy(
          indexedItemsReferencedFromClassesInTransaction.getMethodsUse(), base.methodsUse);
      transferUsedBy(
          indexedItemsReferencedFromClassesInTransaction.getCallSitesUse(), base.callSitesUse);
      transferUsedBy(
          indexedItemsReferencedFromClassesInTransaction.getMethodHandlesUse(),
          base.methodHandlesUse);
    }
  }

  private <T extends DexItem> void transferUsedBy(
      Map<T, Set<DexProgramClass>> classesInTransactionReferringToItem,
      Map<T, VirtualFile.ItemUseInfo> itemUse) {
    assert appView.options().testing.calculateItemUseCountInDex;
    classesInTransactionReferringToItem.forEach(
        (item, classes) -> {
          VirtualFile.ItemUseInfo currentItemUse = itemUse.get(item);
          if (currentItemUse == null) {
            itemUse.put(item, new VirtualFile.ItemUseInfo(classes));
          } else {
            currentItemUse.addUse(classes);
          }
        });

    classesInTransactionReferringToItem.clear();
  }

  public void abort() {
    classes.clear();
    fields.clear();
    methods.clear();
    protos.clear();
    types.clear();
    strings.clear();
    callSites.clear();
    methodHandles.clear();

    indexedItemsReferencedFromClassesInTransaction.clear();
  }

  public boolean isEmpty() {
    return classes.isEmpty()
        && fields.isEmpty()
        && methods.isEmpty()
        && protos.isEmpty()
        && types.isEmpty()
        && strings.isEmpty()
        && callSites.isEmpty()
        && methodHandles.isEmpty();
  }
}
