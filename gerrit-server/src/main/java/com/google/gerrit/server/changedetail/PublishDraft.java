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
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.concurrent.Callable;

public class PublishDraft implements Callable<ReviewResult> {

  public interface Factory {
    PublishDraft create(PatchSet.Id patchSetId);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;

  private final PatchSet.Id patchSetId;

  @Inject
  PublishDraft(ChangeControl.Factory changeControlFactory,
      ReviewDb db, @Assisted final PatchSet.Id patchSetId) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;

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

    if (!control.canPublish(db)) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.PUBLISH_NOT_PERMITTED));
    } else {
      db.patchSets().atomicUpdate(patchSetId, new AtomicUpdate<PatchSet>() {
        @Override
        public PatchSet update(PatchSet patchset) {
          if (patchset.isDraft()) {
            patchset.setDraft(false);
          }
          return null;
        }
      });

      final Change change = db.changes().get(changeId);
      if (change.getStatus() == Change.Status.DRAFT) {
        db.changes().atomicUpdate(changeId,
            new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus() == Change.Status.DRAFT
                && change.currentPatchSetId().equals(patchSetId)) {
              change.setStatus(Change.Status.NEW);
              ChangeUtil.updated(change);
              return change;
            } else {
              return null;
            }
          }
        });
      }
    }

    return result;
  }
}
