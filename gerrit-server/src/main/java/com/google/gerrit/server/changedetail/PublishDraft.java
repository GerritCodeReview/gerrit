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

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.concurrent.Callable;

public class PublishDraft implements Callable<ReviewResult> {

  public interface Factory {
    PublishDraft create(PatchSet.Id patchSetId);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final ChangeHooks hooks;

  private final PatchSet.Id patchSetId;

  @Inject
  PublishDraft(ChangeControl.Factory changeControlFactory,
      ReviewDb db, @Assisted final PatchSet.Id patchSetId,
      final ChangeHooks hooks) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.hooks = hooks;

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
      boolean published = false;
      final PatchSet updatedPatch = db.patchSets().atomicUpdate(patchSetId,
          new AtomicUpdate<PatchSet>() {
        @Override
        public PatchSet update(PatchSet patchset) {
          if (patchset.isDraft()) {
            patchset.setDraft(false);
            return patchset;
          }
          return null;
        }
      });

      if ((updatedPatch != null) && (!updatedPatch.isDraft())) {
        published = true;
      }

      final Change change = db.changes().get(changeId);
      if (change.getStatus() == Change.Status.DRAFT) {
        final Change updatedChange = db.changes().atomicUpdate(changeId,
            new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus() == Change.Status.DRAFT) {
              change.setStatus(Change.Status.NEW);
              ChangeUtil.updated(change);
              return change;
            } else {
              return null;
            }
          }
        });

        if ((updatedChange != null) &&
            (updatedChange.getStatus() == Change.Status.NEW)) {
          published = true;
        }
      }

      if (published) {
        hooks.doDraftPublishedHook(change, patch, db);
      }
    }

    return result;
  }
}
