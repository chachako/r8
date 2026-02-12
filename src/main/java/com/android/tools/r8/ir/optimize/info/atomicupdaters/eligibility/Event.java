// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.InvokeMethod;

public abstract class Event {

  @Override
  public abstract String toString();

  public abstract boolean isFailure();

  public abstract static class SuccessfulEvent extends Event {
    @Override
    public boolean isFailure() {
      return false;
    }
  }

  public abstract static class FailureEvent extends Event {
    @Override
    public boolean isFailure() {
      return true;
    }
  }

  public static final class CanOptimize extends SuccessfulEvent {
    private final InvokeMethod invoke;
    private final boolean updaterNullCheck;
    private final boolean holderNullCheck;

    public CanOptimize(InvokeMethod invoke, boolean updaterNullCheck, boolean holderNullCheck) {
      this.invoke = invoke;
      this.updaterNullCheck = updaterNullCheck;
      this.holderNullCheck = holderNullCheck;
    }

    @Override
    public String toString() {
      String nullCheckPart;
      if (updaterNullCheck && holderNullCheck) {
        nullCheckPart = " with two null-checks";
      } else if (updaterNullCheck) {
        nullCheckPart = " with updater null-check";
      } else if (holderNullCheck) {
        nullCheckPart = " with holder null-check";
      } else {
        nullCheckPart = "";
      }
      DexMethod method = invoke.getInvokedMethod();
      DexMethod contextMethod = invoke.getPosition().getMethod();
      return "Can optimize "
          + method.holder.getSimpleName()
          + "."
          + method.name
          + nullCheckPart
          + " in "
          + contextMethod.qualifiedName();
    }
  }

  public static final class CannotOptimize extends FailureEvent {
    private final InvokeMethod invoke;

    public CannotOptimize(InvokeMethod invoke) {
      this.invoke = invoke;
    }

    @Override
    public String toString() {
      DexMethod method = invoke.getInvokedMethod();
      DexMethod contextMethod = invoke.getPosition().getMethod();
      return "Cannot optimize "
          + method.holder.getSimpleName()
          + "."
          + method.name
          + " in "
          + contextMethod.qualifiedName();
    }
  }

  public static final class CanInstrument extends SuccessfulEvent {
    private final DexField field;

    public CanInstrument(DexField field) {
      this.field = field;
    }

    @Override
    public String toString() {
      return "Can instrument " + field.qualifiedName();
    }
  }

  public static final class CannotInstrument extends FailureEvent {
    private final DexField field;

    public CannotInstrument(DexField field) {
      this.field = field;
    }

    @Override
    public String toString() {
      return "Cannot instrument " + field.qualifiedName();
    }
  }

  public static final class CanRemove extends SuccessfulEvent {
    private final DexField field;

    public CanRemove(DexField field) {
      this.field = field;
    }

    @Override
    public String toString() {
      return "Can remove " + field.qualifiedName();
    }
  }

  public static final class CannotRemove extends FailureEvent {
    private final DexField field;

    public CannotRemove(DexField field) {
      this.field = field;
    }

    @Override
    public String toString() {
      return "Cannot remove " + field.qualifiedName();
    }
  }
}
