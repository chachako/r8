// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.debuginfo.DebugRepresentation;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

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
            transaction.rewriter,
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

  public static class ItemUseInfo {

    private static final Set<DexProgramClass> MANY = null;
    private static final int manyCount = 3;

    private Set<DexProgramClass> use;

    ItemUseInfo(Set<DexProgramClass> initialUse) {
      use = initialUse.size() >= manyCount ? MANY : initialUse;
    }

    public void addUse(Set<DexProgramClass> moreUse) {
      if (use == MANY) {
        return;
      }
      if (moreUse.size() >= manyCount) {
        use = MANY;
        return;
      }
      use.addAll(moreUse);
      if (use.size() >= manyCount) {
        use = MANY;
      }
    }

    public static int getManyCount() {
      return manyCount;
    }

    public boolean isMany() {
      return use == MANY;
    }

    public int getSize() {
      assert use != MANY;
      return use.size();
    }

    public int getSizeOrMany() {
      if (use == MANY) return -1;
      return use.size();
    }

    public Set<DexProgramClass> getUse() {
      assert !isMany();
      return use;
    }
  }

  public static class VirtualFileIndexedItemCollection implements IndexedItemCollection {

    private final DexItemFactory factory;
    private final Map<String, DexString> shortyCache = new HashMap<>();

    private final Set<DexProgramClass> classes = Sets.newIdentityHashSet();
    private final Map<DexProto, DexString> protos = Maps.newIdentityHashMap();
    private final Set<DexType> types = Sets.newIdentityHashSet();
    private final Set<DexMethod> methods = Sets.newIdentityHashSet();
    private final Set<DexField> fields = Sets.newIdentityHashSet();
    private final Set<DexString> strings = Sets.newIdentityHashSet();
    private final Set<DexCallSite> callSites = Sets.newIdentityHashSet();
    private final Set<DexMethodHandle> methodHandles = Sets.newIdentityHashSet();

    public final Map<DexString, ItemUseInfo> stringsUse = new IdentityHashMap<>();
    public final Map<DexType, ItemUseInfo> typesUse = new IdentityHashMap<>();
    public final Map<DexProto, ItemUseInfo> protosUse = new IdentityHashMap<>();
    public final Map<DexField, ItemUseInfo> fieldsUse = new IdentityHashMap<>();
    public final Map<DexMethod, ItemUseInfo> methodsUse = new IdentityHashMap<>();
    public final Map<DexCallSite, ItemUseInfo> callSitesUse = new IdentityHashMap<>();
    public final Map<DexMethodHandle, ItemUseInfo> methodHandlesUse = new IdentityHashMap<>();

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

    int getNumberOfMethods() {
      return methods.size();
    }

    int getNumberOfFields() {
      return fields.size();
    }

    public Collection<DexString> getStrings() {
      return strings;
    }
  }

  private static boolean addProtoWithShorty(
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

  public static class IndexedItemTransaction implements IndexedItemCollection {

    public interface ClassUseCollector {

      void collectClassDependencies(DexProgramClass clazz);

      void collectClassDependenciesDone();

      void clear();

      Map<DexString, Set<DexProgramClass>> getStringsUse();

      Map<DexType, Set<DexProgramClass>> getTypesUse();

      Map<DexProto, Set<DexProgramClass>> getProtosUse();

      Map<DexField, Set<DexProgramClass>> getFieldsUse();

      Map<DexMethod, Set<DexProgramClass>> getMethodsUse();

      Map<DexCallSite, Set<DexProgramClass>> getCallSitesUse();

      Map<DexMethodHandle, Set<DexProgramClass>> getMethodHandlesUse();
    }

    public static class EmptyIndexedItemUsedByClasses implements ClassUseCollector {
      @Override
      public void collectClassDependencies(DexProgramClass clazz) {
        // Do nothing.
      }

      @Override
      public void collectClassDependenciesDone() {
        // Do nothing.
      }

      @Override
      public void clear() {
        // DO nothing.
      }

      @Override
      public Map<DexString, Set<DexProgramClass>> getStringsUse() {
        assert false;
        return null;
      }

      @Override
      public Map<DexType, Set<DexProgramClass>> getTypesUse() {
        assert false;
        return null;
      }

      @Override
      public Map<DexProto, Set<DexProgramClass>> getProtosUse() {
        assert false;
        return null;
      }

      @Override
      public Map<DexField, Set<DexProgramClass>> getFieldsUse() {
        assert false;
        return null;
      }

      @Override
      public Map<DexMethod, Set<DexProgramClass>> getMethodsUse() {
        assert false;
        return null;
      }

      @Override
      public Map<DexCallSite, Set<DexProgramClass>> getCallSitesUse() {
        assert false;
        return null;
      }

      @Override
      public Map<DexMethodHandle, Set<DexProgramClass>> getMethodHandlesUse() {
        assert false;
        return null;
      }
    }

    public static class IndexedItemsUsedByClassesInTransaction
        implements IndexedItemCollection, ClassUseCollector {

      private final AppView<?> appView;
      private final LensCodeRewriterUtils rewriter;
      private final VirtualFileIndexedItemCollection base;
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

      private IndexedItemsUsedByClassesInTransaction(
          AppView<?> appView,
          LensCodeRewriterUtils rewriter,
          VirtualFileIndexedItemCollection base,
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

    private final AppView<?> appView;
    private final VirtualFileIndexedItemCollection base;
    private final LensCodeRewriterUtils rewriter;

    private final Set<DexProgramClass> classes = new LinkedHashSet<>();
    private final Set<DexField> fields = new LinkedHashSet<>();
    private final Set<DexMethod> methods = new LinkedHashSet<>();
    private final Set<DexType> types = new LinkedHashSet<>();
    private final Map<DexProto, DexString> protos = new LinkedHashMap<>();
    private final Set<DexString> strings = new LinkedHashSet<>();
    private final Set<DexCallSite> callSites = new LinkedHashSet<>();
    private final Set<DexMethodHandle> methodHandles = new LinkedHashSet<>();

    private final ClassUseCollector indexedItemsReferencedFromClassesInTransaction;

    private IndexedItemTransaction(VirtualFileIndexedItemCollection base, AppView<?> appView) {
      this.appView = appView;
      this.base = base;
      this.rewriter = new LensCodeRewriterUtils(appView, true);
      this.indexedItemsReferencedFromClassesInTransaction =
          appView.options().testing.calculateItemUseCountInDex
              ? new IndexedItemsUsedByClassesInTransaction(appView, rewriter, base, this)
              : new EmptyIndexedItemUsedByClasses();
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

    void addClassAndDependencies(DexProgramClass clazz) {
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
      if (currentClass == null) {
        // Only marker strings can be added outside a class context.
        assert string.startsWith("~~");
      }
      return maybeInsert(string, strings::add, base.strings, false);
    }

    @Override
    public boolean addProto(DexProto proto) {
      assert currentClass != null;
      return maybeInsert(
          proto,
          p -> addProtoWithShorty(p, protos, base.shortyCache, this::addString, base.factory),
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

    int getNumberOfMethods() {
      return methods.size() + base.getNumberOfMethods();
    }

    int getNumberOfClasses() {
      return classes.size() + base.classes.size();
    }

    int getNumberOfTypes() {
      return types.size() + base.types.size();
    }

    int getNumberOfFields() {
      return fields.size() + base.getNumberOfFields();
    }

    private <T extends DexItem> void commitItemsIn(Set<T> set, Function<T, Boolean> hook) {
      set.forEach((item) -> {
        boolean newlyAdded = hook.apply(item);
        assert newlyAdded;
      });
      set.clear();
    }

    void commit() {
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
        transferUsedBy(
            indexedItemsReferencedFromClassesInTransaction.getProtosUse(), base.protosUse);
        transferUsedBy(
            indexedItemsReferencedFromClassesInTransaction.getFieldsUse(), base.fieldsUse);
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
        Map<T, ItemUseInfo> itemUse) {
      assert appView.options().testing.calculateItemUseCountInDex;
      classesInTransactionReferringToItem.forEach(
          (item, classes) -> {
            ItemUseInfo currentItemUse = itemUse.get(item);
            if (currentItemUse == null) {
              itemUse.put(item, new ItemUseInfo(classes));
            } else {
              currentItemUse.addUse(classes);
            }
          });

      classesInTransactionReferringToItem.clear();
    }

    void abort() {
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
}
