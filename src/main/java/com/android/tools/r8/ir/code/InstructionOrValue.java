// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

public interface InstructionOrValue {

  default boolean isInstruction() {
    return false;
  }

  default Instruction asInstruction() {
    return null;
  }

  default boolean isPhi() {
    return false;
  }

  default boolean isStackMapPhi() {
    return false;
  }

  default Phi asPhi() {
    return null;
  }

  default Value asValue() {
    return null;
  }

  BasicBlock getBlock();
}
