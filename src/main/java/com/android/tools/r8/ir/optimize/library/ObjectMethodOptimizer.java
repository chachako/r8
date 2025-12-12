// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.ConsumerUtils;
import java.util.List;
import java.util.Set;

public class ObjectMethodOptimizer extends StatelessLibraryMethodModelCollection {
  private final DexItemFactory dexItemFactory;

  ObjectMethodOptimizer(AppView<?> appView) {
    this.dexItemFactory = appView.dexItemFactory();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.objectType;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    DexMethod method = singleTarget.getReference();
    if (method.isIdenticalTo(dexItemFactory.objectMembers.getClass)) {
      optimizeGetClass(instructionIterator, invoke);
    } else if (method.isIdenticalTo(dexItemFactory.objectMembers.equals)) {
      return optimizeEquals(
          code, blockIterator, instructionIterator, invoke, affectedValues, blocksToRemove);
    }
    return instructionIterator;
  }

  private void optimizeGetClass(InstructionListIterator instructionIterator, InvokeMethod invoke) {
    if ((!invoke.hasOutValue() || !invoke.outValue().hasAnyUsers())
        && invoke.inValues().get(0).isNeverNull()) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private InstructionListIterator optimizeEquals(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    Value lhs = invoke.getFirstArgument();
    if (!lhs.isNeverNull()) {
      return instructionIterator;
    }
    if (code.metadata().mayHaveMonitorInstruction()) {
      // TODO(b/465869067): Remove this restriction by maintaining catch handlers.
      return instructionIterator;
    }
    Value outValue = invoke.outValue();
    if (outValue == null || !outValue.hasAnyUsers()) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
      return instructionIterator;
    }

    Value rhs = invoke.getSecondArgument();

    BasicBlock startBlock = invoke.getBlock();
    boolean hadCatchHandlers = startBlock.hasCatchHandlers();
    if (hadCatchHandlers) {
      // We're replacing the equals() invoke with an If. Blocks with catch handlers cannot have If
      // as the exit instruction.
      startBlock.removeAllExceptionalSuccessors();
      startBlock.clearCatchHandlers();
    }

    BasicBlock resultBlock = instructionIterator.split(code, blockIterator, false);
    blockIterator.previous();
    BasicBlock falseBlock = instructionIterator.split(code, blockIterator, false);
    blockIterator.previous();
    BasicBlock trueBlock = instructionIterator.split(code, blockIterator, false);
    falseBlock.replacePredecessor(trueBlock, startBlock);
    trueBlock.replaceSuccessor(falseBlock, resultBlock);
    startBlock.getMutableSuccessors().add(falseBlock);
    resultBlock.getMutablePredecessors().add(trueBlock);

    ConstNumber constFalse = code.createBooleanConstant(false);
    constFalse.setPosition(invoke.getPosition());
    falseBlock.getInstructions().addFirst(constFalse);
    ConstNumber constTrue = code.createBooleanConstant(true);
    constTrue.setPosition(invoke.getPosition());
    trueBlock.getInstructions().addFirst(constTrue);

    Phi resultPhi = code.createPhi(resultBlock, TypeElement.getInt());
    resultPhi.appendOperand(constFalse.outValue());
    resultPhi.appendOperand(constTrue.outValue());
    outValue.replaceUsers(resultPhi);

    If ifInst = new If(IfType.EQ, List.of(lhs, rhs));
    assert invoke.getNext().isGoto();
    invoke.getNext().remove();
    invoke.replace(ifInst);
    assert ifInst.getTrueTarget() == trueBlock;
    assert ifInst.fallthroughBlock() == falseBlock;

    if (hadCatchHandlers) {
      Set<BasicBlock> toRemove = code.getUnreachableBlocks();
      blocksToRemove.addAll(toRemove);
      for (BasicBlock block : toRemove) {
        block.cleanForRemoval(
            affectedValues, ConsumerUtils.emptyConsumer(), blocksToRemove::contains);
      }
    }

    return instructionIterator;
  }
}
