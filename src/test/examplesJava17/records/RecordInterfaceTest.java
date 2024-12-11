// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarGraphTestConsumer;
import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordInterfaceTest extends TestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("Human");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private boolean isCfRuntimeWithNativeRecordSupport() {
    return parameters.isCfRuntime()
        && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17)
        && parameters.getApiLevel().equals(AndroidApiLevel.B);
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(isCfRuntimeWithNativeRecordSupport());
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), RecordInterface.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClassesAndStrippedOuter(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), RecordInterface.class)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters)
                || runtimeWithRecordsSupport(parameters.getRuntime()),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testD8Intermediate() throws Exception {
    parameters.assumeDexRuntime();
    DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path path = compileIntermediate(globals);
    testForD8()
        .addProgramFiles(path)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters),
            b ->
                b.getBuilder()
                    .addGlobalSyntheticsResourceProviders(globals.getIndexedModeProvider()),
            b -> assertFalse(globals.hasGlobals()))
        .apply(b -> b.getBuilder().setDesugarGraphConsumer(consumer))
        .setMinApi(parameters)
        .setIncludeClassesChecksum(true)
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), RecordInterface.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
    assertNoEdgeToRecord(consumer);
  }

  @Test
  public void testD8IntermediateNoDesugaringInStep2() throws Exception {
    parameters.assumeDexRuntime();
    DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path path = compileIntermediate(globals);
    testForD8()
        .addProgramFiles(path)
        .applyIf(
            isRecordsFullyDesugaredForD8(parameters),
            b ->
                b.getBuilder()
                    .addGlobalSyntheticsResourceProviders(globals.getIndexedModeProvider()),
            b -> assertFalse(globals.hasGlobals()))
        .apply(b -> b.getBuilder().setDesugarGraphConsumer(consumer))
        .setMinApi(parameters)
        .setIncludeClassesChecksum(true)
        // In Android Studio they disable desugaring at this point to improve build speed.
        .disableDesugaring()
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), RecordInterface.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
    assertNoEdgeToRecord(consumer);
  }

  private Path compileIntermediate(GlobalSyntheticsConsumer globalSyntheticsConsumer)
      throws Exception {
    Origin fake = new PathOrigin(Paths.get("origin"));
    DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
    Path intermediate =
        testForD8(Backend.DEX)
            .addStrippedOuter(getClass(), fake)
            .apply(
                b -> {
                  try {
                    for (Path file :
                        ToolHelper.getClassFilesForInnerClasses(ImmutableList.of(getClass()))) {
                      b.getBuilder().addClassProgramData(Files.readAllBytes(file), fake);
                    }
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .setMinApi(parameters)
            .setIntermediate(true)
            .setIncludeClassesChecksum(true)
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globalSyntheticsConsumer))
            .apply(b -> b.getBuilder().setDesugarGraphConsumer(consumer))
            .compile()
            .assertNoMessages()
            .writeToZip();
    assertNoEdgeToRecord(consumer);
    return intermediate;
  }

  private void assertNoEdgeToRecord(DesugarGraphTestConsumer consumer) {
    Assert.assertEquals(0, consumer.totalEdgeCount());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(parameters.isDexRuntime() || isCfRuntimeWithNativeRecordSupport());
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addInnerClassesAndStrippedOuter(getClass())
            .setMinApi(parameters)
            .addKeepMainRule(RecordInterface.class);
    if (parameters.isCfRuntime()) {
      builder
          .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
          .compile()
          .inspect(RecordTestUtils::assertRecordsAreRecords)
          .run(parameters.getRuntime(), RecordInterface.class)
          .assertSuccessWithOutput(EXPECTED_RESULT);
      return;
    }
    builder
        .run(parameters.getRuntime(), RecordInterface.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  interface Human {

    default void printHuman() {
      System.out.println("Human");
    }
  }

  record Person(String name, int age) implements Human {}

  public class RecordInterface {

    public static void main(String[] args) {
      Person janeDoe = new Person("Jane Doe", 42);
      janeDoe.printHuman();
    }
  }
}
