package com.google.gerrit.server.notedb;

import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;

public class StoreSubmitRequirementsOp implements BatchUpdateOp {
  private final ChangeData.Factory changeDataFactory;

  public StoreSubmitRequirementsOp(ChangeData.Factory changeDataFactory) {
    this.changeDataFactory = changeDataFactory;
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws Exception {
    Change change = ctx.getChange();

    // Get the submit requirements
    ChangeData changeData = changeDataFactory.create(change);
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    for (SubmitRequirementResult rs : changeData.submitRequirements().values()) {
      update.putSubmitRequirementResult(rs);
    }
    return false;
  }
}
