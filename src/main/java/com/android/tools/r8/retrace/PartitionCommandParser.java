// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagInfoImpl;
import com.android.tools.r8.ParseFlagPrinter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.OptionsParsing;
import com.android.tools.r8.utils.OptionsParsing.ParseContext;
import com.android.tools.r8.utils.PartitionMapZipContainer;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.List;

public class PartitionCommandParser {

  private static List<ParseFlagInfo> getFlags() {
    return ImmutableList.<ParseFlagInfo>builder()
        .add(
            ParseFlagInfoImpl.flag1(
                "--output", "<partition-map>", "Output destination of partitioned map"))
        .add(ParseFlagInfoImpl.getHelp())
        .build();
  }

  static String getUsageMessage() {
    StringBuilder builder = new StringBuilder();
    StringUtils.appendLines(
        builder,
        "Usage: partition [options] <proguard-map>",
        " where <proguard-map> is a generated mapping file and options are:");
    new ParseFlagPrinter().addFlags(getFlags()).appendLinesToBuilder(builder);
    return builder.toString();
  }

  public static PartitionCommand.Builder parse(String[] args, Origin origin) {
    return new PartitionCommandParser().parse(args, origin, PartitionCommand.builder());
  }

  private PartitionCommand.Builder parse(
      String[] args, Origin origin, PartitionCommand.Builder builder) {
    ParseContext context = new ParseContext(args);
    boolean isProguardMapProducerSet = false;
    while (context.head() != null) {
      Boolean help = OptionsParsing.tryParseBoolean(context, "--help");
      if (help != null) {
        builder.setPrintHelp(true);
        continue;
      }
      Boolean version = OptionsParsing.tryParseBoolean(context, "--version");
      if (version != null) {
        builder.setPrintVersion(true);
        continue;
      }
      String output = OptionsParsing.tryParseSingle(context, "--output", null);
      if (output != null && !output.isEmpty()) {
        builder.setPartitionMapConsumer(
            PartitionMapZipContainer.createPartitionMapZipContainerConsumer(Paths.get(output)));
        continue;
      }
      if (!isProguardMapProducerSet) {
        builder.setProguardMapProducer(ProguardMapProducer.fromPath(Paths.get(context.head())));
        context.next();
        isProguardMapProducerSet = true;
      } else {
        builder
            .getReporter()
            .error(
                new StringDiagnostic(
                    String.format(
                        "Too many arguments specified for builder at '%s'", context.head()),
                    origin));
      }
    }
    return builder;
  }
}
