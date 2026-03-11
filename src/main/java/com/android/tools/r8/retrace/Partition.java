// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.utils.ExceptionUtils.withMainProgramHandler;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Version;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.StringUtils;

/** A tool for creating a partition-map from a proguard map. */
@KeepForApi
public class Partition {

  public static void run(String[] args) throws CompilationFailedException {
    PartitionCommand command = PartitionCommand.parse(args, CommandLineOrigin.INSTANCE).build();
    run(command);
  }

  /**
   * The main entry point for partitioning a map.
   *
   * @param command The command that describes the desired behavior of this partition invocation.
   */
  public static void run(PartitionCommand command) throws CompilationFailedException {
    if (command.isPrintHelp()) {
      System.out.println(PartitionCommandParser.getUsageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("Partition " + Version.getVersionString());
      return;
    }
    ExceptionUtils.withCompilationHandler(
        command.getReporter(),
        () -> {
          command
              .getPartitionMapConsumer()
              .acceptMappingPartitionMetadata(
                  ProguardMapPartitioner.builder(command.getReporter())
                      .setProguardMapProducer(command.getProguardMapProducer())
                      .setPartitionConsumer(
                          command.getPartitionMapConsumer()::acceptMappingPartition)
                      .setAllowEmptyMappedRanges(true)
                      .setAllowExperimentalMapping(false)
                      .build()
                      .run());
          command.getPartitionMapConsumer().finished(command.getReporter());
        });
  }

  /**
   * The main entry point for running a legacy proguard map to partition map from command line.
   *
   * @param args The argument that describes this command.
   */
  public static void main(String... args) {
    if (args.length == 0) {
      throw new RuntimeException(
          StringUtils.joinLines("Invalid invocation.", PartitionCommandParser.getUsageMessage()));
    }
    withMainProgramHandler(() -> run(args));
  }
}
