// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.blastradius;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.blastradius.proto.BlastRadius;
import com.android.tools.r8.blastradius.proto.BlastRadiusContainer;
import com.android.tools.r8.blastradius.proto.FieldReference;
import com.android.tools.r8.blastradius.proto.FileOrigin;
import com.android.tools.r8.blastradius.proto.KeepConstraint;
import com.android.tools.r8.blastradius.proto.KeepConstraints;
import com.android.tools.r8.blastradius.proto.KeepRuleBlastRadius;
import com.android.tools.r8.blastradius.proto.KeptClassInfo;
import com.android.tools.r8.blastradius.proto.KeptFieldInfo;
import com.android.tools.r8.blastradius.proto.KeptMethodInfo;
import com.android.tools.r8.blastradius.proto.MethodReference;
import com.android.tools.r8.blastradius.proto.ProtoReference;
import com.android.tools.r8.blastradius.proto.TextFileOrigin;
import com.android.tools.r8.blastradius.proto.TypeReference;
import com.android.tools.r8.blastradius.proto.TypeReferenceList;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.shaking.ProguardKeepRuleModifiers;
import com.android.tools.r8.utils.ArrayUtils;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class RootSetBlastRadiusSerializer {

  private final AppView<?> appView;

  private final BlastRadiusContainer.Builder container = BlastRadiusContainer.newBuilder();

  // Ids.
  private final Reference2IntMap<RootSetBlastRadiusForRule> ruleIds =
      new Reference2IntOpenHashMap<>();

  // Origins.
  private final Map<Origin, FileOrigin> origins = new HashMap<>();

  // References. Intentionally using HashMap for DexTypeList since it is not canonicalized.
  private final Map<DexField, FieldReference> fieldReferences = new IdentityHashMap<>();
  private final Map<DexMethod, MethodReference> methodReferences = new IdentityHashMap<>();
  private final Map<DexProto, ProtoReference> protoReferences = new IdentityHashMap<>();
  private final Map<DexType, TypeReference> typeReferences = new IdentityHashMap<>();
  private final Map<DexTypeList, TypeReferenceList> typeReferenceLists = new HashMap<>();

  // Kept items. LinkedHashMap for deterministic output when writing to the proto container.
  private final Map<DexType, KeptClassInfo.Builder> keptClassInfos = new LinkedHashMap<>();
  private final Map<DexField, KeptFieldInfo.Builder> keptFieldInfos = new LinkedHashMap<>();
  private final Map<DexMethod, KeptMethodInfo.Builder> keptMethodInfos = new LinkedHashMap<>();

  private final Map<Wrapper<KeepConstraints>, KeepConstraints> keepConstraints = new HashMap<>();

  RootSetBlastRadiusSerializer(AppView<?> appView) {
    this.appView = appView;
  }

  public BlastRadiusContainer serialize(RootSetBlastRadius blastRadius) {
    Collection<RootSetBlastRadiusForRule> sortedBlastRadius =
        blastRadius.getBlastRadiusWithDeterministicOrder();
    Map<RootSetBlastRadiusForRule, Collection<RootSetBlastRadiusForRule>> subsumedByInfo =
        blastRadius.getSubsumedByInfo();
    for (RootSetBlastRadiusForRule blastRadiusForRule : sortedBlastRadius) {
      ruleIds.put(blastRadiusForRule, ruleIds.size());
    }
    for (RootSetBlastRadiusForRule blastRadiusForRule : sortedBlastRadius) {
      int ruleId = ruleIds.getInt(blastRadiusForRule);
      KeepRuleBlastRadius.Builder ruleProto =
          KeepRuleBlastRadius.newBuilder()
              .setId(ruleId)
              .setBlastRadius(serializeBlastRadius(blastRadiusForRule, ruleId, subsumedByInfo))
              .setConstraintsId(serializeConstraints(blastRadiusForRule).getId())
              .setOrigin(serializeTextFileOrigin(blastRadiusForRule.getRule()))
              .setSource(blastRadiusForRule.getSource());
      container.addKeepRuleBlastRadiusTable(ruleProto);
    }
    keptClassInfos.values().forEach(container::addKeptClassInfoTable);
    keptFieldInfos.values().forEach(container::addKeptFieldInfoTable);
    keptMethodInfos.values().forEach(container::addKeptMethodInfoTable);
    BlastRadiusContainer result = container.build();
    assert validate(result);
    return result;
  }

  private BlastRadius serializeBlastRadius(
      RootSetBlastRadiusForRule blastRadiusForRule,
      int ruleId,
      Map<RootSetBlastRadiusForRule, Collection<RootSetBlastRadiusForRule>> subsumedByInfo) {
    BlastRadius.Builder blastRadius = BlastRadius.newBuilder();
    // Populate subsumed by.
    Collection<RootSetBlastRadiusForRule> dominators = subsumedByInfo.get(blastRadiusForRule);
    if (dominators != null) {
      Iterator<RootSetBlastRadiusForRule> iterator = dominators.iterator();
      int[] dominatorIds =
          ArrayUtils.initialize(new int[dominators.size()], i -> ruleIds.getInt(iterator.next()));
      Arrays.sort(dominatorIds);
      for (int dominatorId : dominatorIds) {
        blastRadius.addSubsumedBy(dominatorId);
      }
    }
    // Populate blast radius.
    for (DexType matchedClass : blastRadiusForRule.getMatchedClassesWithDeterministicOrder()) {
      KeptClassInfo.Builder keptClassInfo =
          keptClassInfos.computeIfAbsent(
              matchedClass,
              k ->
                  KeptClassInfo.newBuilder()
                      .setId(keptClassInfos.size())
                      .setClassReferenceId(serializeTypeReference(k).getId())
                      .setFileOriginId(serializeOrigin(k).getId()));
      blastRadius.addClassBlastRadius(keptClassInfo.getId());
      keptClassInfo.addKeptBy(ruleId);
    }
    for (DexField matchedField : blastRadiusForRule.getMatchedFieldsWithDeterministicOrder()) {
      KeptFieldInfo.Builder keptFieldInfo =
          keptFieldInfos.computeIfAbsent(
              matchedField,
              k ->
                  KeptFieldInfo.newBuilder()
                      .setId(keptFieldInfos.size())
                      .setFieldReferenceId(serializeFieldReference(k).getId())
                      .setFileOriginId(serializeOrigin(k).getId()));
      blastRadius.addFieldBlastRadius(keptFieldInfo.getId());
      keptFieldInfo.addKeptBy(ruleId);
    }
    for (DexMethod matchedMethod : blastRadiusForRule.getMatchedMethodsWithDeterministicOrder()) {
      KeptMethodInfo.Builder keptMethodInfo =
          keptMethodInfos.computeIfAbsent(
              matchedMethod,
              k ->
                  KeptMethodInfo.newBuilder()
                      .setId(keptMethodInfos.size())
                      .setMethodReferenceId(serializeMethodReference(k).getId())
                      .setFileOriginId(serializeOrigin(k).getId()));
      blastRadius.addMethodBlastRadius(keptMethodInfo.getId());
      keptMethodInfo.addKeptBy(ruleId);
    }
    return blastRadius.build();
  }

  private KeepConstraints serializeConstraints(RootSetBlastRadiusForRule blastRadiusForRule) {
    KeepConstraints.Builder builder = KeepConstraints.newBuilder().setId(keepConstraints.size());
    ProguardKeepRuleModifiers modifiers = blastRadiusForRule.getRule().getModifiers();
    if (!modifiers.allowsObfuscation) {
      builder.addConstraints(KeepConstraint.DONT_OBFUSCATE);
    }
    if (!modifiers.allowsOptimization) {
      builder.addConstraints(KeepConstraint.DONT_OPTIMIZE);
    }
    if (!modifiers.allowsShrinking) {
      builder.addConstraints(KeepConstraint.DONT_SHRINK);
    }
    KeepConstraints constraints = builder.build();
    Wrapper<KeepConstraints> wrapper = KeepConstraintsEquivalence.doWrap(constraints);
    KeepConstraints previous = keepConstraints.putIfAbsent(wrapper, constraints);
    if (previous != null) {
      return previous;
    }
    container.addKeepConstraintsTable(constraints);
    return constraints;
  }

  private FieldReference serializeFieldReference(DexField field) {
    return fieldReferences.computeIfAbsent(
        field,
        k -> {
          FieldReference fieldReference =
              FieldReference.newBuilder()
                  .setId(fieldReferences.size())
                  .setClassReferenceId(serializeTypeReference(field.getHolderType()).getId())
                  .setTypeReferenceId(serializeTypeReference(field.getType()).getId())
                  .setName(field.getName().toString())
                  .build();
          container.addFieldReferenceTable(fieldReference);
          return fieldReference;
        });
  }

  private MethodReference serializeMethodReference(DexMethod method) {
    return methodReferences.computeIfAbsent(
        method,
        k -> {
          MethodReference methodReference =
              MethodReference.newBuilder()
                  .setId(methodReferences.size())
                  .setClassReferenceId(serializeTypeReference(method.getHolderType()).getId())
                  .setProtoReferenceId(serializeProtoReference(method.getProto()).getId())
                  .setName(method.getName().toString())
                  .build();
          container.addMethodReferenceTable(methodReference);
          return methodReference;
        });
  }

  private FileOrigin serializeOrigin(DexReference reference) {
    DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(reference.getContextType()));
    assert clazz != null;
    return serializeOrigin(clazz.getOrigin());
  }

  private FileOrigin serializeOrigin(Origin origin) {
    return origins.computeIfAbsent(
        origin,
        o -> {
          // TODO(b/441055269): Set the filename correctly.
          // TODO(b/441055269): Set maven coordinate.
          FileOrigin fileOrigin =
              FileOrigin.newBuilder().setId(origins.size()).setFilename(o.toString()).build();
          container.addFileOriginTable(fileOrigin);
          return fileOrigin;
        });
  }

  private TextFileOrigin serializeTextFileOrigin(ProguardKeepRuleBase keepRule) {
    int line, column;
    if (keepRule.getPosition() instanceof TextRange) {
      TextRange textRange = (TextRange) keepRule.getPosition();
      line = textRange.getStart().getLine();
      column = textRange.getStart().getColumn();
    } else {
      line = -1;
      column = -1;
    }
    return TextFileOrigin.newBuilder()
        .setFileOriginId(serializeOrigin(keepRule.getOrigin()).getId())
        .setLineNumber(line)
        .setColumnNumber(column)
        .build();
  }

  private ProtoReference serializeProtoReference(DexProto proto) {
    return protoReferences.computeIfAbsent(
        proto,
        k -> {
          ProtoReference protoReference =
              ProtoReference.newBuilder()
                  .setId(protoReferences.size())
                  .setParametersId(serializeTypeReferenceList(proto.getParameters()).getId())
                  .setReturnTypeId(serializeTypeReference(proto.getReturnType()).getId())
                  .build();
          container.addProtoReferenceTable(protoReference);
          return protoReference;
        });
  }

  private TypeReference serializeTypeReference(DexType type) {
    return typeReferences.computeIfAbsent(
        type,
        k -> {
          TypeReference typeReference =
              TypeReference.newBuilder()
                  .setId(typeReferences.size())
                  .setJavaDescriptor(type.toDescriptorString())
                  .build();
          container.addTypeReferenceTable(typeReference);
          return typeReference;
        });
  }

  private TypeReferenceList serializeTypeReferenceList(DexTypeList types) {
    return typeReferenceLists.computeIfAbsent(
        types,
        k -> {
          TypeReferenceList.Builder builder =
              TypeReferenceList.newBuilder().setId(typeReferenceLists.size());
          for (DexType type : types) {
            builder.addTypeReferenceIds(serializeTypeReference(type).getId());
          }
          TypeReferenceList typeReferenceList = builder.build();
          container.addTypeReferenceListTable(typeReferenceList);
          return typeReferenceList;
        });
  }

  @SuppressWarnings("UnusedVariable")
  private boolean validate(BlastRadiusContainer container) {
    // TODO(b/441055269): Check that ids of ClassFileInJarOrigin and FileOrigin are non-overlapping.
    // TODO(b/441055269): Check that the reference constants pools do not contain duplicates.
    return true;
  }

  private static class KeepConstraintsEquivalence extends Equivalence<KeepConstraints> {

    private static final KeepConstraintsEquivalence INSTANCE = new KeepConstraintsEquivalence();

    public static Wrapper<KeepConstraints> doWrap(KeepConstraints constraints) {
      return INSTANCE.wrap(constraints);
    }

    @Override
    protected boolean doEquivalent(KeepConstraints a, KeepConstraints b) {
      if (a.getConstraintsCount() != b.getConstraintsCount()) {
        return false;
      }
      int flags = 0;
      for (int i = 0; i < a.getConstraintsCount(); i++) {
        flags ^= 1 << a.getConstraints(i).getNumber();
        flags ^= 1 << b.getConstraints(i).getNumber();
      }
      return flags == 0;
    }

    @Override
    protected int doHash(KeepConstraints constraints) {
      int hash = 0;
      for (KeepConstraint constraint : constraints.getConstraintsList()) {
        hash |= 1 << constraint.getNumber();
      }
      return hash;
    }
  }
}
