// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.WorkList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Function;

public class DestructivePhiTypeUpdater {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Function<DexType, DexType> mapping;

  public DestructivePhiTypeUpdater(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      GraphLens graphLens,
      GraphLens codeLens) {
    this(appView, type -> graphLens.lookupType(type, codeLens));
  }

  public DestructivePhiTypeUpdater(
      AppView<? extends AppInfoWithClassHierarchy> appView, Function<DexType, DexType> mapping) {
    this.appView = appView;
    this.mapping = mapping;
  }

  public Deque<Phi> unsetPhiTypes(Set<Phi> affectedPhis) {
    if (affectedPhis.isEmpty()) {
      return null;
    }

    // We have updated at least one type lattice element which can cause phi's to narrow to a more
    // precise type. Because cycles in phi's can occur, we have to reset all phi's before
    // computing the new values.
    WorkList<Assume> assumeWorklist = WorkList.newIdentityWorkList();
    Deque<Phi> phiWorklist = new ArrayDeque<>(affectedPhis);
    while (assumeWorklist.hasNext() || !phiWorklist.isEmpty()) {
      assumeWorklist.process(
          assume -> {
            Value assumeValue = assume.outValue();
            assumeValue.setType(TypeElement.getBottom());
            for (Phi affectedPhi : assumeValue.uniquePhiUsers()) {
              affectedPhis.add(affectedPhi);
              phiWorklist.add(affectedPhi);
            }
          });
      while (!phiWorklist.isEmpty()) {
        Phi phi = phiWorklist.poll();
        phi.setType(TypeElement.getBottom());
        assumeWorklist.addIfNotSeen(phi.uniqueUsers(Instruction::isAssume));
        for (Phi affectedPhi : phi.uniquePhiUsers()) {
          if (affectedPhis.add(affectedPhi)) {
            phiWorklist.add(affectedPhi);
          }
        }
      }
    }
    return phiWorklist;
  }

  public void recomputeAndPropagateTypes(IRCode code, Set<Phi> affectedPhis, Deque<Phi> worklist) {
    if (affectedPhis.isEmpty()) {
      assert worklist == null;
      return;
    }

    assert verifyAllChangedPhisAreScheduled(code, affectedPhis);
    AffectedValues affectedValues = new AffectedValues();
    worklist.addAll(affectedPhis);
    while (!worklist.isEmpty()) {
      Phi phi = worklist.poll();
      TypeElement newType = phi.computePhiType(appView);
      if (!phi.getType().equals(newType)) {
        assert !newType.isBottom();
        phi.setType(newType);
        worklist.addAll(phi.uniquePhiUsers());
        affectedValues.addAll(phi.affectedValues());
      }
    }

    assert TypeAnalysis.verifyValuesUpToDate(appView, code, affectedPhis);

    // Now that the types of all transitively type affected phis have been reset, we can
    // perform a narrowing, starting from the values that are affected by those phis.
    affectedValues.narrowingWithAssumeRemoval(appView, code);
  }

  private boolean verifyAllChangedPhisAreScheduled(IRCode code, Set<Phi> affectedPhis) {
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      for (Phi phi : block.getPhis()) {
        TypeElement phiType = phi.getType();
        TypeElement substituted = phiType.fixupClassTypeReferences(appView, mapping);
        assert substituted.equals(phiType) || affectedPhis.contains(phi);
      }
    }
    return true;
  }
}
