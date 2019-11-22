// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jasmin;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class InvokeSpecialTests extends JasminTestBase {

  @Test
  public void testPrivateInvokeSpecial() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addPrivateVirtualMethod("foo", ImmutableList.of(), "I",
        ".limit stack 1",
        ".limit locals 1",
        "  ldc 42",
        "  ireturn");

    clazz.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  new Test",
        "  dup",
        "  invokespecial Test/<init>()V",
        "  invokespecial Test/foo()I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    String expected = "42";
    String javaResult = runOnJava(builder, clazz.name);
    assertEquals(expected, javaResult);
    String artResult = runOnArtD8(builder, clazz.name);
    assertEquals(expected, artResult);
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void testPublicInvokeSpecial() throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addVirtualMethod("foo", ImmutableList.of(), "I",
        ".limit stack 1",
        ".limit locals 1",
        "  ldc 42",
        "  ireturn");

    clazz.addMainMethod(
        ".limit stack 3",
        ".limit locals 1",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        "  new Test",
        "  dup",
        "  invokespecial Test/<init>()V",
        "  invokespecial Test/foo()I",
        "  invokevirtual java/io/PrintStream/print(I)V",
        "  return");

    String expected = "42";
    String javaResult = runOnJava(builder, clazz.name);
    assertEquals(expected, javaResult);

    if (ToolHelper.artSupported()) {
      thrown.expect(CompilationFailedException.class);
    }

    // TODO(b/110175213): This will fail with a compilation exception since we cannot translate
    //  an invoke-special to a member on the same class.
    String artResult = runOnArtD8(builder, clazz.name);
    assertEquals(expected, artResult);
  }
}
