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
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class IndexedItemsUsedByClassesInTransaction
    implements IndexedItemCollection, ClassUseCollector {

  private final AppView<?> appView;
  private final LensCodeRewriterUtils rewriter;
  private final VirtualFile.VirtualFileIndexedItemCollection base;
  private final IndexedItemTransaction transaction;

  private final Set<DexProgramClass> classes = new LinkedHashSet<>();

  private final Map<DexString, Set<DexProgramClass>> stringsUse = new IdentityHashMap<>();
  private final Map<DexType, Set<DexProgramClass>> typesUse = new IdentityHashMap<>();
  private final Map<DexProto, Set<DexProgramClass>> protosUse = new IdentityHashMap<>();
  private final Map<DexField, Set<DexProgramClass>> fieldsUse = new IdentityHashMap<>();
  private final Map<DexMethod, Set<DexProgramClass>> methodsUse = new IdentityHashMap<>();
  private final Map<DexCallSite, Set<DexProgramClass>> callSitesUse = new IdentityHashMap<>();
  private final Map<DexMethodHandle, Set<DexProgramClass>> methodHandlessUse =
      new LinkedHashMap<>();

  DexProgramClass currentClass = null;

  IndexedItemsUsedByClassesInTransaction(
      AppView<?> appView,
      LensCodeRewriterUtils rewriter,
      VirtualFile.VirtualFileIndexedItemCollection base,
      IndexedItemTransaction transaction) {
    this.appView = appView;
    this.rewriter = rewriter;
    this.base = base;
    this.transaction = transaction;
  }

  @Override
  public void collectClassDependencies(DexProgramClass clazz) {
    clazz.collectIndexedItems(appView, this, rewriter);
  }

  @Override
  public boolean addClass(DexProgramClass clazz) {
    assert currentClass == null;
    currentClass = clazz;
    assert !classes.contains(clazz);
    classes.add(clazz);
    return true;
  }

  @Override
  public void collectClassDependenciesDone() {
    currentClass = null;
  }

  @Override
  public boolean addString(DexString string) {
    collectUse(string, transaction.strings, base.strings, stringsUse);
    return true;
  }

  @Override
  public boolean addType(DexType type) {
    collectUse(type, transaction.types, base.types, typesUse);
    return true;
  }

  @Override
  public boolean addProto(DexProto proto) {
    collectUse(proto, transaction.protos.keySet(), base.protos.keySet(), protosUse);
    return true;
  }

  @Override
  public boolean addField(DexField field) {
    collectUse(field, transaction.fields, base.fields, fieldsUse);
    return true;
  }

  @Override
  public boolean addMethod(DexMethod method) {
    collectUse(method, transaction.methods, base.methods, methodsUse);
    return true;
  }

  @Override
  public boolean addCallSite(DexCallSite callSite) {
    collectUse(callSite, transaction.callSites, base.callSites, callSitesUse);
    return true;
  }

  @Override
  public boolean addMethodHandle(DexMethodHandle methodHandle) {
    collectUse(methodHandle, transaction.methodHandles, base.methodHandles, methodHandlessUse);
    return true;
  }

  private <T extends DexItem> void collectUse(
      T item, Set<T> set, Set<T> baseSet, Map<T, Set<DexProgramClass>> use) {
    assert baseSet.contains(item) || set.contains(item);
    if (set.contains(item)) {
      assert classes.contains(currentClass);
    }
    use.computeIfAbsent(item, unused -> Sets.newIdentityHashSet()).add(currentClass);
  }

  @Override
  public Map<DexString, Set<DexProgramClass>> getStringsUse() {
    return stringsUse;
  }

  @Override
  public Map<DexType, Set<DexProgramClass>> getTypesUse() {
    return typesUse;
  }

  @Override
  public Map<DexProto, Set<DexProgramClass>> getProtosUse() {
    return protosUse;
  }

  @Override
  public Map<DexField, Set<DexProgramClass>> getFieldsUse() {
    return fieldsUse;
  }

  @Override
  public Map<DexMethod, Set<DexProgramClass>> getMethodsUse() {
    return methodsUse;
  }

  @Override
  public Map<DexCallSite, Set<DexProgramClass>> getCallSitesUse() {
    return callSitesUse;
  }

  @Override
  public Map<DexMethodHandle, Set<DexProgramClass>> getMethodHandlesUse() {
    return methodHandlessUse;
  }

  @Override
  public void clear() {
    classes.clear();
    stringsUse.clear();
    typesUse.clear();
    protosUse.clear();
    fieldsUse.clear();
    methodsUse.clear();
    callSitesUse.clear();
    methodHandlessUse.clear();
  }
}
