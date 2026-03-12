// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.inliner.InliningWithUnsafeTest.TestClass.UnsafeStub;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InliningWithUnsafeTest extends TestBase {

  @Parameterized.Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withAllRuntimesAndApiLevels().build();
  }

  // sun.misc.Unsafe is a hidden class, accessed via reflection. This non-existence at compile-time
  // foils some inlining checks that reasons about method accessibility and thus prevents inlining.
  @Test
  public void testR8() throws Exception {
    String stubDescriptor = DescriptorUtils.javaClassToDescriptor(UnsafeStub.class);
    ClassFileTransformer transformedClass =
        transformer(TestClass.class)
            .replaceClassDescriptorInMembers(stubDescriptor, "Lsun/misc/Unsafe;")
            .replaceClassDescriptorInMethodInstructions(stubDescriptor, "Lsun/misc/Unsafe;");
    ClassReference testClassReference = transformedClass.getClassReference();
    byte[] testClass = transformedClass.transform();
    testForR8(parameters)
        .addProgramClassFileData(testClass)
        .addKeepMainRule(testClassReference)
        .enableMemberValuePropagationAnnotations()
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              Set<String> foundMethods =
                  inspector.clazz(testClassReference).allMethods().stream()
                      .map(MethodSubject::getOriginalMethodName)
                      .collect(Collectors.toSet());
              ImmutableSet<String> expected;
              // The CF runtime jar has sun.misc.Unsafe exposed, which permits inlining.
              boolean isInlined = parameters.isCfRuntime();
              if (isInlined) {
                expected = ImmutableSet.of("main", "nonObviousNull");
              } else {
                expected = ImmutableSet.of("main", "nonObviousNull", "useUnsafe");
              }
              assertEquals(
                  "The remaining methods of the test class were unexpected:",
                  expected,
                  foundMethods);
            });
  }

  public static class TestClass {

    private final int x = 42;

    public static class UnsafeStub {
      long objectFieldOffset(Field f) {
        throw new RuntimeException("Called stub");
      }
    }

    public static void main(String[] args) throws NoSuchFieldException {
      if (nonObviousNull() != null) {
        Field reflectedField = TestClass.class.getDeclaredField("x");
        System.out.println(useUnsafe(nonObviousNull(), reflectedField));
      }
    }

    // Test assumes that this method is inlined (single-caller, small method).
    private static long useUnsafe(UnsafeStub unsafeInstance, Field field) {
      return unsafeInstance.objectFieldOffset(field);
    }

    @NeverPropagateValue
    @NeverInline
    private static <T> T nonObviousNull() {
      return null;
    }
  }
}
