// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.Reporter;

@KeepForApi
public class PartitionCommand {

  private final PartitionMapConsumer partitionMapConsumer;
  private final ProguardMapProducer proguardMapProducer;
  private final boolean printHelp;
  private final boolean printVersion;
  private final Reporter reporter;

  private PartitionCommand(
      PartitionMapConsumer partitionMapConsumer,
      ProguardMapProducer proguardMapProducer,
      Reporter reporter) {
    this.partitionMapConsumer = partitionMapConsumer;
    this.proguardMapProducer = proguardMapProducer;
    this.printHelp = false;
    this.printVersion = false;
    this.reporter = reporter;
  }

  private PartitionCommand(boolean printHelp, boolean printVersion) {
    this.partitionMapConsumer = null;
    this.proguardMapProducer = null;
    this.printHelp = printHelp;
    this.printVersion = printVersion;
    this.reporter = null;
  }

  public static PartitionCommand.Builder parse(String[] args, Origin origin) {
    return PartitionCommandParser.parse(args, origin);
  }

  PartitionMapConsumer getPartitionMapConsumer() {
    return partitionMapConsumer;
  }

  ProguardMapProducer getProguardMapProducer() {
    return proguardMapProducer;
  }

  Reporter getReporter() {
    return reporter;
  }

  boolean isPrintHelp() {
    return printHelp;
  }

  boolean isPrintVersion() {
    return printVersion;
  }

  /** Utility method for obtaining a RetraceCommand builder with a default diagnostics handler. */
  public static Builder builder() {
    return new Builder(new Reporter());
  }

  @KeepForApi
  public static class Builder {

    private final Reporter reporter;

    private ProguardMapProducer proguardMapProducer;
    private PartitionMapConsumer partitionMapConsumer;

    private boolean printHelp;
    private boolean printVersion;

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = Reporter.create(diagnosticsHandler);
    }

    Reporter getReporter() {
      return reporter;
    }

    public Builder setProguardMapProducer(ProguardMapProducer proguardMapProducer) {
      this.proguardMapProducer = proguardMapProducer;
      return this;
    }

    public Builder setPartitionMapConsumer(PartitionMapConsumer partitionMapConsumer) {
      this.partitionMapConsumer = partitionMapConsumer;
      return this;
    }

    Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    public PartitionCommand build() throws CompilationFailedException {
      Box<PartitionCommand> box = new Box<>();
      ExceptionUtils.withCompilationHandler(
          reporter,
          () -> {
            validate();
            box.set(makeCommand());
            reporter.failIfPendingErrors();
          });
      return box.get();
    }

    private PartitionCommand makeCommand() {
      if (printHelp || printVersion) {
        return new PartitionCommand(printHelp, printVersion);
      } else {
        return new PartitionCommand(partitionMapConsumer, proguardMapProducer, reporter);
      }
    }

    private void validate() {
      if (partitionMapConsumer == null) {
        throw new RetracePartitionException("PartitionMapConsumer not specified");
      }
      if (proguardMapProducer == null) {
        throw new RetracePartitionException("ProguardMapSupplier not specified");
      }
    }
  }
}
