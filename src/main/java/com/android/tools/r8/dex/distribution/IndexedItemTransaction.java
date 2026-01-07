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
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Set;

public class IndexedItemTransaction implements IndexedItemCollection {

  static final int NO_REF_COUNT = -1;

  private final AppView<?> appView;
  private final VirtualFile.VirtualFileIndexedItemCollection base;
  private final LensCodeRewriterUtils rewriter;

  private final Set<DexProgramClass> classes = Sets.newIdentityHashSet();

  // Sets with reference counting.
  final Reference2IntMap<DexCallSite> callSites = new Reference2IntOpenHashMap<>();
  final Reference2IntMap<DexField> fields = new Reference2IntOpenHashMap<>();
  final Reference2IntMap<DexMethod> methods = new Reference2IntOpenHashMap<>();
  final Reference2IntMap<DexMethodHandle> methodHandles = new Reference2IntOpenHashMap<>();
  final Reference2IntMap<DexProto> protos = new Reference2IntOpenHashMap<>();
  final Reference2IntMap<DexString> strings = new Reference2IntOpenHashMap<>();
  final Reference2IntMap<DexType> types = new Reference2IntOpenHashMap<>();

  // For reference counting.
  Set<DexCallSite> callSitesInCurrentClass;
  Set<DexField> fieldsInCurrentClass;
  Set<DexMethod> methodsInCurrentClass;
  Set<DexMethodHandle> methodHandlesInCurrentClass;
  Set<DexProto> protosInCurrentClass;
  Set<DexString> stringsInCurrentClass;
  Set<DexType> typesInCurrentClass;

  public IndexedItemTransaction(
      VirtualFile.VirtualFileIndexedItemCollection base, AppView<?> appView) {
    this.appView = appView;
    this.base = base;
    this.rewriter = new LensCodeRewriterUtils(appView, true);
  }

  public static IndexedItemTransaction create(
      VirtualFile.VirtualFileIndexedItemCollection base, AppView<?> appView) {
    if (appView.testing().classToDexDistributionRefinementPasses > 0) {
      return new IndexedItemTransaction(base, appView);
    } else {
      // Avoid overhead from reference counting. Notably, when the reference counters are needed,
      // we must collect all items referenced from the class in a new set. Without reference
      // counters, when a given item is already referenced from the virtual file, we can simply
      // skip collecting the item and all of its descendants.
      return new IndexedItemTransactionWithoutReferenceCounting(base, appView);
    }
  }

  public LensCodeRewriterUtils getRewriter() {
    return rewriter;
  }

  <T extends DexItem> boolean addItemWithReferenceCount(
      T item,
      Reference2IntMap<T> baseSet,
      Reference2IntMap<T> transactionSet,
      Set<T> transactionCurrentClassSet) {
    assert currentClass != null;
    return transactionCurrentClassSet.add(item);
  }

  public void addClassAndDependencies(DexProgramClass clazz) {
    clazz.collectIndexedItems(appView, this, rewriter);
    tearDownAfterTracing();
  }

  public void setupForTracing(DexProgramClass clazz) {
    assert currentClass == null;
    currentClass = clazz;
    callSitesInCurrentClass = Sets.newIdentityHashSet();
    fieldsInCurrentClass = Sets.newIdentityHashSet();
    methodsInCurrentClass = Sets.newIdentityHashSet();
    methodHandlesInCurrentClass = Sets.newIdentityHashSet();
    protosInCurrentClass = Sets.newIdentityHashSet();
    stringsInCurrentClass = Sets.newIdentityHashSet();
    typesInCurrentClass = Sets.newIdentityHashSet();
  }

  public void tearDownAfterTracing() {
    currentClass = null;
    commitItemsInCurrentClass(callSites, callSitesInCurrentClass);
    callSitesInCurrentClass = null;
    commitItemsInCurrentClass(fields, fieldsInCurrentClass);
    fieldsInCurrentClass = null;
    commitItemsInCurrentClass(methods, methodsInCurrentClass);
    methodsInCurrentClass = null;
    commitItemsInCurrentClass(methodHandles, methodHandlesInCurrentClass);
    methodHandlesInCurrentClass = null;
    commitItemsInCurrentClass(protos, protosInCurrentClass);
    protosInCurrentClass = null;
    commitItemsInCurrentClass(strings, stringsInCurrentClass);
    stringsInCurrentClass = null;
    commitItemsInCurrentClass(types, typesInCurrentClass);
    typesInCurrentClass = null;
  }

  private <T> void commitItemsInCurrentClass(Reference2IntMap<T> committedItems, Set<T> items) {
    for (T item : items) {
      int counter = committedItems.containsKey(item) ? committedItems.getInt(item) : 0;
      assert counter != NO_REF_COUNT;
      committedItems.put(item, counter + 1);
    }
    items.clear();
  }

  DexProgramClass currentClass = null;

  @Override
  public boolean addClass(DexProgramClass clazz) {
    if (!base.classes.contains(clazz) && classes.add(clazz)) {
      setupForTracing(clazz);
      return true;
    }
    return false;
  }

  @Override
  public boolean addField(DexField field) {
    return addItemWithReferenceCount(field, base.fields, fields, fieldsInCurrentClass);
  }

  @Override
  public boolean addMethod(DexMethod method) {
    return addItemWithReferenceCount(method, base.methods, methods, methodsInCurrentClass);
  }

  @Override
  public boolean addString(DexString string) {
    return addItemWithReferenceCount(string, base.strings, strings, stringsInCurrentClass);
  }

  public void addChecksumString(DexString string) {
    addChecksumOrMarkerString(string);
  }

  public void addMarkerString(DexString string) {
    addChecksumOrMarkerString(string);
  }

  private void addChecksumOrMarkerString(DexString string) {
    if (base.strings.containsKey(string)) {
      // If this checksum or marker is already present in the DEX, we need to clear the reference
      // counter. Otherwise, we may incorrectly conclude that the string can be removed from the DEX
      // file if the class referencing the string is moved to a different file.
      //
      // Note that strictly speaking, at this point the transaction has not been committed, yet we
      // still update the virtual file directly. This is OK only because we always commit all
      // checksums and markers (i.e., this transaction is guaranteed to be committed in the future).
      base.strings.put(string, NO_REF_COUNT);
    } else {
      strings.put(string, NO_REF_COUNT);
    }
  }

  @Override
  public boolean addType(DexType type) {
    assert SyntheticNaming.verifyNotInternalSynthetic(type);
    return addItemWithReferenceCount(type, base.types, types, typesInCurrentClass);
  }

  @Override
  public boolean addProto(DexProto proto) {
    if (addItemWithReferenceCount(proto, base.protos, protos, protosInCurrentClass)) {
      DexString shorty =
          base.shortyCache.computeIfAbsent(
              proto.createShortyString(), appView.dexItemFactory()::createString);
      addString(shorty);
      return true;
    }
    return false;
  }

  @Override
  public boolean addCallSite(DexCallSite callSite) {
    return addItemWithReferenceCount(callSite, base.callSites, callSites, callSitesInCurrentClass);
  }

  @Override
  public boolean addMethodHandle(DexMethodHandle methodHandle) {
    return addItemWithReferenceCount(
        methodHandle, base.methodHandles, methodHandles, methodHandlesInCurrentClass);
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

  private <T extends DexItem> void commitItemsTo(
      Reference2IntMap<T> items, Reference2IntMap<T> committedItems) {
    for (Reference2IntMap.Entry<T> entry : items.reference2IntEntrySet()) {
      T item = entry.getKey();
      int committedCounter = committedItems.containsKey(item) ? committedItems.getInt(item) : 0;
      committedItems.put(item, committedCounter + entry.getIntValue());
    }
    items.clear();
  }

  public void commit() {
    base.classes.addAll(classes);
    classes.clear();
    commitItemsTo(callSites, base.callSites);
    commitItemsTo(fields, base.fields);
    commitItemsTo(methods, base.methods);
    commitItemsTo(methodHandles, base.methodHandles);
    commitItemsTo(strings, base.strings);
    commitItemsTo(protos, base.protos);
    commitItemsTo(types, base.types);
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
