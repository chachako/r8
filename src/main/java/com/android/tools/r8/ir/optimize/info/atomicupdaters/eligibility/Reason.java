// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility;

import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;

public abstract class Reason {

  @Override
  public abstract String toString();

  public static final Reason NOT_SUPPORTED = new GenericReason("not supported");

  public static final Reason UPDATER_NOT_INSTRUMENTED =
      new GenericReason("uses un-instrumented updater field");

  public static final Reason WRITTEN_OUTSIDE_CLASS_INITIALIZER =
      new GenericReason("written outside class initializer");

  public static final Reason MULTIPLE_WRITES = new GenericReason("multiple writes");

  public static final Reason UPDATER_INITIALIZED_BY_PHI =
      new GenericReason("updater initialized by phi function");

  public static final Reason UPDATER_HOLDER_INITIALIZED_BY_PHI =
      new GenericReason("holder initialized by phi function");

  public static final Reason UPDATER_FIELD_TYPE_INITIALIZED_BY_PHI =
      new GenericReason("field type initialized by phi function");

  public static final Reason UPDATER_VALUE_INITIALIZED_BY_PHI =
      new GenericReason("value initialized by phi function");

  public static final Reason UPDATER_HOLDER_NOT_CONSTANT_CLASS =
      new GenericReason("holder is not a constant class");

  public static final Reason UPDATER_FIELD_TYPE_NOT_CONSTANT_CLASS =
      new GenericReason("field type is not a constant class");

  public static final Reason UPDATER_HOLDER_IS_OUTSIDE_CLASS =
      new GenericReason("holder refers to another class");

  public static final Reason UPDATER_NOT_INITIALIZED_BY_INVOKE_STATIC =
      new GenericReason("not initialized by static call");

  public static final Reason UPDATER_NOT_INITIALIZED_BY_NEW_UPDATER =
      new GenericReason("updater not initialized by newUpdater");

  public static final Reason UPDATER_FIELD_NOT_CONSTANT_STRING =
      new GenericReason("field name is not a constant string");

  public static final Reason NEW_UPDATER_INVALID_FIELD =
      new GenericReason("newUpdater does not resolve to a field");

  public static final Reason UPDATER_REFLECTS_NON_VOLATILE_FIELD =
      new GenericReason("reflected field is not volatile");

  public static final Reason NOT_UNUSED = new GenericReason("not unused");

  public static final Reason NO_REASON =
      new Reason() {
        @Override
        public String toString() {
          return "";
        }
      };

  public static final class GenericReason extends Reason {

    private final String reason;

    public GenericReason(String reason) {
      this.reason = reason;
    }

    @Override
    public String toString() {
      return reason;
    }
  }

  public static final class StaticallyUnclearUpdater extends Reason {

    private final AbstractValue value;

    public StaticallyUnclearUpdater(AbstractValue value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return "updater is statically " + value;
    }
  }

  public static final class WrongHolderType extends Reason {

    private final TypeElement holderType;
    private final TypeElement expectedHolderType;

    public WrongHolderType(TypeElement holderType, TypeElement expectedHolderType) {
      this.holderType = holderType;
      this.expectedHolderType = expectedHolderType;
    }

    @Override
    public String toString() {
      return "holder type " + holderType + " is not " + expectedHolderType;
    }
  }

  public static final class WrongValueType extends Reason {

    private final TypeElement holderType;
    private final TypeElement expectedHolderType;

    public WrongValueType(TypeElement holderType, TypeElement expectedHolderType) {
      this.holderType = holderType;
      this.expectedHolderType = expectedHolderType;
    }

    @Override
    public String toString() {
      return "value type " + holderType + " is not " + expectedHolderType;
    }
  }
}
