// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.ast.KeepBindings.KeepBindingSymbol;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepEdgeAstTest extends TestBase {

  private static String CLASS = "com.example.Foo";

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepEdgeAstTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  public static String extract(KeepEdge edge) {
    StringBuilder builder = new StringBuilder();
    KeepRuleExtractor extractor = new KeepRuleExtractor(builder::append);
    extractor.extract(edge);
    return builder.toString();
  }

  @Test
  public void testKeepAll() {
    KeepBindings.Builder bindingsBuilder = KeepBindings.builder();
    KeepBindingSymbol anyClassSymbol = bindingsBuilder.generateFreshSymbol("ANY_CLASS");
    bindingsBuilder.addBinding(anyClassSymbol, KeepItemPattern.anyClass());
    KeepEdge edge =
        KeepEdge.builder()
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        KeepTarget.builder().setItemPattern(KeepItemPattern.anyClass()).build())
                    .addTarget(
                        KeepTarget.builder()
                            .setItemPattern(buildMemberItem(anyClassSymbol).build())
                            .build())
                    .build())
            .setBindings(bindingsBuilder.build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-keep,allowaccessmodification class ** { void finalize(); }",
            "-keepclassmembers,allowaccessmodification class ** { *; }"),
        extract(edge));
  }

  @Test
  public void testSoftPinViaConstraints() {
    KeepConstraints constraints =
        KeepConstraints.builder()
            .add(KeepConstraint.classInstantiate())
            .add(KeepConstraint.methodInvoke())
            .add(KeepConstraint.fieldGet())
            .add(KeepConstraint.fieldSet())
            .build();
    KeepBindings.Builder bindingsBuilder = KeepBindings.builder();
    KeepBindingSymbol anyClassSymbol = bindingsBuilder.generateFreshSymbol("ANY_CLASS");
    bindingsBuilder.addBinding(anyClassSymbol, KeepItemPattern.anyClass());
    KeepEdge edge =
        KeepEdge.builder()
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        KeepTarget.builder()
                            .setItemPattern(KeepItemPattern.anyClass())
                            .setConstraints(constraints)
                            .build())
                    .addTarget(
                        KeepTarget.builder()
                            .setItemPattern(buildMemberItem(anyClassSymbol).build())
                            .setConstraints(constraints)
                            .build())
                    .build())
            .setBindings(bindingsBuilder.build())
            .build();
    // Pinning just the use constraints points will issue the full inverse of the known options,
    // e.g., 'allowaccessmodification'.
    List<String> options = ImmutableList.of("shrinking", "obfuscation", "accessmodification");
    String allows = String.join(",allow", options);
    // The "any" item will be split in two rules, one for the targeted types and one for the
    // targeted members.
    assertEquals(
        StringUtils.unixLines(
            "-keep,allow" + allows + " class ** { void finalize(); }",
            "-keepclassmembers,allow" + allows + " class ** { *; }"),
        extract(edge));
  }
  @Test
  public void testKeepClass() {
    KeepTarget target = target(classItem(CLASS));
    KeepConsequences consequences = KeepConsequences.builder().addTarget(target).build();
    KeepEdge edge = KeepEdge.builder().setConsequences(consequences).build();
    assertEquals(
        StringUtils.unixLines(
            "-keep,allowaccessmodification class " + CLASS + " { void finalize(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInitIfReferenced() {
    KeepBindings.Builder bindingsBuilder = KeepBindings.builder();
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(KeepCondition.builder().setItemPattern(classItem(CLASS)).build())
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        target(
                            buildMemberItem(CLASS, bindingsBuilder)
                                .setMemberPattern(defaultInitializerPattern())
                                .build()))
                    .build())
            .setBindings(bindingsBuilder.build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-keepclassmembers,allowaccessmodification class " + CLASS + " { void <init>(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInstanceIfReferenced() {
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(KeepCondition.builder().setItemPattern(classItem(CLASS)).build())
                    .build())
            .setConsequences(KeepConsequences.builder().addTarget(target(classItem(CLASS))).build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-if class "
                + CLASS
                + " -keep,allowaccessmodification class "
                + CLASS
                + " { void finalize(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInstanceAndInitIfReferenced() {
    KeepBindings.Builder bindingsBuilder = KeepBindings.builder();
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(KeepCondition.builder().setItemPattern(classItem(CLASS)).build())
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(target(classItem(CLASS)))
                    .addTarget(
                        target(
                            buildMemberItem(CLASS, bindingsBuilder)
                                .setMemberPattern(defaultInitializerPattern())
                                .build()))
                    .build())
            .setBindings(bindingsBuilder.build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-keepclassmembers,allowaccessmodification class " + CLASS + " { void <init>(); }",
            "-if class "
                + CLASS
                + " -keep,allowaccessmodification class "
                + CLASS
                + " { void finalize(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInstanceAndInitIfReferencedWithBinding() {
    KeepBindings.Builder bindings = KeepBindings.builder();
    KeepBindingSymbol classSymbol = bindings.create("CLASS");
    KeepEdge edge =
        KeepEdge.builder()
            .setBindings(bindings.addBinding(classSymbol, classItem(CLASS)).build())
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(
                        KeepCondition.builder()
                            .setItemReference(classItemBinding(classSymbol))
                            .build())
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(target(classItemBinding(classSymbol)))
                    .addTarget(
                        target(
                            KeepMemberItemPattern.builder()
                                .setClassReference(classItemBinding(classSymbol))
                                .setMemberPattern(defaultInitializerPattern())
                                .build()))
                    .build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-if class "
                + CLASS
                + " -keepclasseswithmembers,allowaccessmodification class "
                + CLASS
                + " { void <init>(); }"),
        extract(edge));
  }

  private KeepClassItemReference classItemBinding(KeepBindingSymbol bindingName) {
    return KeepBindingReference.forClass(bindingName).toClassItemReference();
  }

  private KeepTarget target(KeepItemPattern item) {
    return KeepTarget.builder().setItemPattern(item).build();
  }

  private KeepTarget target(KeepItemReference item) {
    return KeepTarget.builder().setItemReference(item).build();
  }

  private KeepItemPattern classItem(String typeName) {
    return buildClassItem(typeName).build();
  }

  private KeepClassItemPattern.Builder buildClassItem(String typeName) {
    return KeepClassItemPattern.builder()
        .setClassNamePattern(KeepQualifiedClassNamePattern.exact(typeName));
  }

  private KeepMemberItemPattern.Builder buildMemberItem(
      String holderType, KeepBindings.Builder bindingsBuilder) {

    KeepClassItemPattern classItemPattern = buildClassItem(holderType).build();
    KeepBindingSymbol holderSymbol = bindingsBuilder.generateFreshSymbol("HOLDER");
    bindingsBuilder.addBinding(holderSymbol, classItemPattern);
    return buildMemberItem(holderSymbol);
  }

  private static KeepMemberItemPattern.Builder buildMemberItem(KeepBindingSymbol holderSymbol) {
    return KeepMemberItemPattern.builder()
        .setClassReference(
            KeepClassItemReference.fromBindingReference(
                KeepClassBindingReference.forClass(holderSymbol)))
        .setMemberPattern(KeepMemberPattern.allMembers());
  }

  private KeepMemberPattern defaultInitializerPattern() {
    return KeepMethodPattern.builder()
        .setNamePattern(KeepMethodNamePattern.instanceInitializer())
        .setParametersPattern(KeepMethodParametersPattern.none())
        .setReturnTypeVoid()
        .build();
  }
}
