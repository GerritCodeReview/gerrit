// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.notedb;

import com.google.gerrit.entities.Change;
import com.google.gerrit.server.project.SubmitRequirementsEvaluator;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;

/** A {@link BatchUpdateOp} that stores the evaluated submit requirements of a change in NoteDb. */
public class StoreSubmitRequirementsOp implements BatchUpdateOp {
  private final ChangeData.Factory changeDataFactory;
  private final SubmitRequirementsEvaluator evaluator;

  public interface Factory {
    StoreSubmitRequirementsOp create();
  }

  @Inject
  public StoreSubmitRequirementsOp(
      ChangeData.Factory changeDataFactory, SubmitRequirementsEvaluator evaluator) {
    this.changeDataFactory = changeDataFactory;
    this.evaluator = evaluator;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws Exception {
    Change change = ctx.getChange();
    ChangeData changeData = changeDataFactory.create(change);
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    update.putSubmitRequirementResults(evaluator.evaluateAllRequirements(changeData).values());
    return !changeData.submitRequirements().isEmpty();
  }
}
