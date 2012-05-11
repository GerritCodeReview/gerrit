// Copyright (C) 2011 The Android Open Source Project
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


package com.google.gerrit.server.changedetail;

import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class DeleteDraftPatchSet implements Callable<ReviewResult> {

  public interface Factory {
    DeleteDraftPatchSet create(PatchSet.Id patchSetId);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final GitRepositoryManager gitManager;
  private final GitReferenceUpdated replication;
  private final PatchSetInfoFactory patchSetInfoFactory;

  private final PatchSet.Id patchSetId;

  @Inject
  DeleteDraftPatchSet(ChangeControl.Factory changeControlFactory,
      ReviewDb db, GitRepositoryManager gitManager,
      GitReferenceUpdated replication, PatchSetInfoFactory patchSetInfoFactory,
      @Assisted final PatchSet.Id patchSetId) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.gitManager = gitManager;
    this.replication = replication;
    this.patchSetInfoFactory = patchSetInfoFactory;

    this.patchSetId = patchSetId;
  }

  @Override
  public ReviewResult call() throws NoSuchChangeException, OrmException {
    final ReviewResult result = new ReviewResult();

    final Change.Id changeId = patchSetId.getParentKey();
    result.setChangeId(changeId);
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    final PatchSet patch = db.patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }
    if (!patch.isDraft()) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.NOT_A_DRAFT));
      return result;
    }

    if (!control.canDeleteDraft(db)) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.DELETE_NOT_PERMITTED));
      return result;
    }
    final Change change = control.getChange();

    try {
      ChangeUtil.deleteOnlyDraftPatchSet(patch, change, gitManager, replication, db);
    } catch (IOException e) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.GIT_ERROR, e.getMessage()));
    }

    List<PatchSet> restOfPatches = db.patchSets().byChange(changeId).toList();
    if (restOfPatches.size() == 0) {
      try {
        ChangeUtil.deleteDraftChange(patchSetId, gitManager, replication, db);
        result.setChangeId(null);
      } catch (IOException e) {
        result.addError(new ReviewResult.Error(
            ReviewResult.Error.Type.GIT_ERROR, e.getMessage()));
      }
    } else {
      PatchSet.Id highestId = null;
      for (PatchSet ps : restOfPatches) {
        if (highestId == null || ps.getPatchSetId() > highestId.get()) {
          highestId = ps.getId();
        }
      }
      if (change.currentPatchSetId().equals(patchSetId)) {
        change.removeLastPatchSetId();
        try {
          change.setCurrentPatchSet(patchSetInfoFactory.get(db, change.currPatchSetId()));
        } catch (PatchSetInfoNotAvailableException e) {
          throw new NoSuchChangeException(changeId);
        }
        db.changes().update(Collections.singleton(change));
      }
    }
    return result;
  }
}
