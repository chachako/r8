// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.dex.distribution.IndexedItemTransaction;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class VirtualFile {

  public static final int MAX_ENTRIES = Constants.U16BIT_MAX + 1;

  private final int id;
  public final VirtualFileIndexedItemCollection indexedItems;
  private final IndexedItemTransaction transaction;
  private final FeatureSplit featureSplit;
  private final StartupProfile startupProfile;

  private final DexString primaryClassDescriptor;
  private final DexString primaryClassSynthesizingContextDescriptor;
  private DebugRepresentation debugRepresentation;
  private boolean startup = false;
  private HashCode checksumForBuildMetadata;

  public VirtualFile(int id, AppView<?> appView) {
    this(id, appView, null, null, StartupProfile.empty());
  }

  public VirtualFile(int id, AppView<?> appView, FeatureSplit featureSplit) {
    this(id, appView, null, featureSplit, StartupProfile.empty());
  }

  public VirtualFile(int id, AppView<?> appView, DexProgramClass primaryClass) {
    this(id, appView, primaryClass, null, StartupProfile.empty());
  }

  public VirtualFile(
      int id,
      AppView<?> appView,
      DexProgramClass primaryClass,
      FeatureSplit featureSplit,
      StartupProfile startupProfile) {
    this.id = id;
    this.indexedItems = new VirtualFileIndexedItemCollection(appView);
    this.transaction = new IndexedItemTransaction(indexedItems, appView);
    this.featureSplit = featureSplit;
    this.startupProfile = startupProfile;
    if (primaryClass == null) {
      primaryClassDescriptor = null;
      primaryClassSynthesizingContextDescriptor = null;
    } else {
      DexType type = primaryClass.getType();
      primaryClassDescriptor = appView.getNamingLens().lookupClassDescriptor(type);
      Collection<DexType> contexts = appView.getSyntheticItems().getSynthesizingContextTypes(type);
      if (contexts.size() == 1) {
        primaryClassSynthesizingContextDescriptor =
            appView.getNamingLens().lookupClassDescriptor(contexts.iterator().next());
      } else {
        assert contexts.isEmpty();
        primaryClassSynthesizingContextDescriptor = null;
      }
    }
  }

  public HashCode getChecksumForBuildMetadata() {
    return checksumForBuildMetadata;
  }

  public IndexedItemTransaction getTransaction() {
    return transaction;
  }

  public void calculateChecksumForBuildMetadata(ByteDataView data, InternalOptions options) {
    if (options.r8BuildMetadataConsumer != null) {
      checksumForBuildMetadata =
          Hashing.sha256()
              .hashBytes(data.getBuffer(), data.getOffset(), data.getOffset() + data.getLength());
    }
  }

  public int getId() {
    return id;
  }

  public Set<String> getClassDescriptors() {
    Set<String> classDescriptors = new HashSet<>();
    for (DexProgramClass clazz : indexedItems.classes) {
      boolean added = classDescriptors.add(clazz.type.descriptor.toString());
      assert added;
    }
    return classDescriptors;
  }

  public FeatureSplit getFeatureSplit() {
    return featureSplit;
  }

  public FeatureSplit getFeatureSplitOrBase() {
    return featureSplit != null ? featureSplit : FeatureSplit.BASE;
  }

  public StartupProfile getStartupProfile() {
    return startupProfile;
  }

  public String getPrimaryClassDescriptor() {
    return primaryClassDescriptor == null ? null : primaryClassDescriptor.toString();
  }

  public String getPrimaryClassSynthesizingContextDescriptor() {
    return primaryClassSynthesizingContextDescriptor == null
        ? null
        : primaryClassSynthesizingContextDescriptor.toString();
  }

  public void setDebugRepresentation(DebugRepresentation debugRepresentation) {
    assert debugRepresentation != null;
    assert this.debugRepresentation == null;
    this.debugRepresentation = debugRepresentation;
  }

  public DebugRepresentation getDebugRepresentation() {
    assert debugRepresentation != null;
    return debugRepresentation;
  }

  public void setStartup() {
    startup = true;
  }

  public boolean isStartup() {
    return startup;
  }

  public static String deriveCommonPrefixAndSanityCheck(List<String> fileNames) {
    Iterator<String> nameIterator = fileNames.iterator();
    String first = nameIterator.next();
    if (!StringUtils.toLowerCase(first).endsWith(FileUtils.DEX_EXTENSION)) {
      throw new RuntimeException("Illegal suffix for dex file: `" + first + "`.");
    }
    String prefix = first.substring(0, first.length() - FileUtils.DEX_EXTENSION.length());
    int index = 2;
    while (nameIterator.hasNext()) {
      String next = nameIterator.next();
      if (!StringUtils.toLowerCase(next).endsWith(FileUtils.DEX_EXTENSION)) {
        throw new RuntimeException("Illegal suffix for dex file: `" + first + "`.");
      }
      if (!next.startsWith(prefix)) {
        throw new RuntimeException("Input filenames lack common prefix.");
      }
      String numberPart =
          next.substring(prefix.length(), next.length() - FileUtils.DEX_EXTENSION.length());
      if (Integer.parseInt(numberPart) != index++) {
        throw new RuntimeException("DEX files are not numbered consecutively.");
      }
    }
    return prefix;
  }

  public void injectString(DexString string) {
    transaction.addString(string);
    commitTransaction();
  }

  private ObjectToOffsetMapping objectMapping = null;

  public ObjectToOffsetMapping getObjectMapping() {
    assert objectMapping != null;
    return objectMapping;
  }

  public void computeMapping(
      AppView<?> appView,
      int lazyDexStringsCount,
      Timing timing) {
    computeMapping(appView, lazyDexStringsCount, timing, null);
  }

  public void computeMapping(
      AppView<?> appView,
      int lazyDexStringsCount,
      Timing timing,
      ObjectToOffsetMapping sharedMapping) {
    assert transaction.isEmpty();
    assert objectMapping == null;
    objectMapping =
        new ObjectToOffsetMapping(
            appView,
            sharedMapping,
            transaction.getRewriter(),
            indexedItems.classes,
            indexedItems.protos,
            indexedItems.types,
            indexedItems.methods,
            indexedItems.fields,
            indexedItems.strings,
            indexedItems.callSites,
            indexedItems.methodHandles,
            lazyDexStringsCount,
            startupProfile,
            this,
            timing);
  }

  public void addClass(DexProgramClass clazz) {
    transaction.addClassAndDependencies(clazz);
  }

  public boolean isFull(int maxEntries) {
    return transaction.getNumberOfMethods() > maxEntries
        || transaction.getNumberOfFields() > maxEntries
        || transaction.getNumberOfTypes() > maxEntries;
  }

  public boolean isFull() {
    return isFull(MAX_ENTRIES);
  }

  public int getNumberOfMethods() {
    return transaction.getNumberOfMethods();
  }

  public int getNumberOfFields() {
    return transaction.getNumberOfFields();
  }

  public int getNumberOfClasses() {
    return transaction.getNumberOfClasses();
  }

  public int getNumberOfTypes() {
    return transaction.getNumberOfTypes();
  }

  public void throwIfFull(boolean hasMainDexList, Reporter reporter) {
    if (!isFull()) {
      return;
    }
    throw reporter.fatalError(
        new DexFileOverflowDiagnostic(
            hasMainDexList, transaction.getNumberOfMethods(), transaction.getNumberOfFields()));
  }

  public void abortTransaction() {
    transaction.abort();
  }

  public void commitTransaction() {
    transaction.commit();
  }

  public boolean containsString(DexString string) {
    return indexedItems.strings.contains(string);
  }

  public boolean containsType(DexType type) {
    return indexedItems.types.contains(type);
  }

  public boolean isEmpty() {
    return indexedItems.classes.isEmpty();
  }

  public Collection<DexProgramClass> classes() {
    return indexedItems.classes;
  }

  public static class VirtualFileIndexedItemCollection implements IndexedItemCollection {

    private final DexItemFactory factory;
    public final Map<String, DexString> shortyCache = new HashMap<>();

    public final Set<DexProgramClass> classes = Sets.newIdentityHashSet();
    public final Map<DexProto, DexString> protos = Maps.newIdentityHashMap();
    public final Set<DexType> types = Sets.newIdentityHashSet();
    public final Set<DexMethod> methods = Sets.newIdentityHashSet();
    public final Set<DexField> fields = Sets.newIdentityHashSet();
    public final Set<DexString> strings = Sets.newIdentityHashSet();
    public final Set<DexCallSite> callSites = Sets.newIdentityHashSet();
    public final Set<DexMethodHandle> methodHandles = Sets.newIdentityHashSet();

    public VirtualFileIndexedItemCollection(AppView<?> appView) {
      this.factory = appView.dexItemFactory();
    }

    @Override
    public boolean addClass(DexProgramClass clazz) {
      return classes.add(clazz);
    }

    @Override
    public boolean addField(DexField field) {
      return fields.add(field);
    }

    @Override
    public boolean addMethod(DexMethod method) {
      return methods.add(method);
    }

    @Override
    public boolean addString(DexString string) {
      return strings.add(string);
    }

    public boolean addStrings(Collection<DexString> additionalStrings) {
      return strings.addAll(additionalStrings);
    }

    @Override
    public boolean addProto(DexProto proto) {
      return addProtoWithShorty(proto, protos, shortyCache, this::addString, factory);
    }

    public boolean addProtoWithoutShorty(DexProto proto) {
      return addProtoWithShorty(proto, protos, shortyCache, emptyConsumer(), factory);
    }

    @Override
    public boolean addType(DexType type) {
      assert SyntheticNaming.verifyNotInternalSynthetic(type);
      return types.add(type);
    }

    @Override
    public boolean addCallSite(DexCallSite callSite) {
      return callSites.add(callSite);
    }

    @Override
    public boolean addMethodHandle(DexMethodHandle methodHandle) {
      return methodHandles.add(methodHandle);
    }

    public int getNumberOfMethods() {
      return methods.size();
    }

    public int getNumberOfFields() {
      return fields.size();
    }

    public Collection<DexString> getStrings() {
      return strings;
    }
  }

  public static boolean addProtoWithShorty(
      DexProto proto,
      Map<DexProto, DexString> protoToShorty,
      Map<String, DexString> shortyCache,
      Consumer<DexString> addShortyDexString,
      DexItemFactory factory) {
    if (protoToShorty.containsKey(proto)) {
      return false;
    }
    String shortyString = proto.createShortyString();
    DexString shorty = shortyCache.computeIfAbsent(shortyString, factory::createString);
    addShortyDexString.accept(shorty);
    protoToShorty.put(proto, shorty);
    return true;
  }

}
