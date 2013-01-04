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
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

public class PublishDraft implements Callable<ReviewResult> {
  private static final Logger log =
      LoggerFactory.getLogger(PublishDraft.class);

  public interface Factory {
    PublishDraft create(PatchSet.Id patchSetId);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final ChangeHooks hooks;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final CreateChangeSender.Factory createChangeSenderFactory;

  private final PatchSet.Id patchSetId;

  @Inject
  PublishDraft(final ChangeControl.Factory changeControlFactory,
      final ReviewDb db, final ChangeHooks hooks,
      final PatchSetInfoFactory patchSetInfoFactory,
      final CreateChangeSender.Factory createChangeSenderFactory,
      @Assisted final PatchSet.Id patchSetId) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.hooks = hooks;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.createChangeSenderFactory = createChangeSenderFactory;

    this.patchSetId = patchSetId;
  }

  @Override
  public ReviewResult call() throws NoSuchChangeException, OrmException,
      PatchSetInfoNotAvailableException {
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
      final PatchSet updatedPatchSet = db.patchSets().atomicUpdate(patchSetId,
          new AtomicUpdate<PatchSet>() {
        @Override
        public PatchSet update(PatchSet patchset) {
          patchset.setDraft(false);
          return patchset;
        }
      });

      final Change updatedChange = db.changes().atomicUpdate(changeId,
          new AtomicUpdate<Change>() {
        @Override
        public Change update(Change change) {
          if (change.getStatus() == Change.Status.DRAFT) {
            change.setStatus(Change.Status.NEW);
            ChangeUtil.updated(change);
          }
          return change;
        }
      });

      if (!updatedPatchSet.isDraft() || updatedChange.getStatus() == Change.Status.NEW) {
        hooks.doDraftPublishedHook(updatedChange, updatedPatchSet, db);

        if (control.getChange().getStatus() == Change.Status.DRAFT) {
          notifyWatchers((IdentifiedUser) control.getCurrentUser(),
              updatedChange, updatedPatchSet);
        }
      }
    }

    return result;
  }

  private void notifyWatchers(final IdentifiedUser currentUser,
      final Change updatedChange, final PatchSet updatedPatchSet)
      throws PatchSetInfoNotAvailableException {
    final PatchSetInfo info = patchSetInfoFactory.get(updatedChange, updatedPatchSet);
    try {
      CreateChangeSender cm = createChangeSenderFactory.create(updatedChange);
      cm.setFrom(currentUser.getAccountId());
      cm.setPatchSet(updatedPatchSet, info);
      cm.send();
    } catch (Exception e) {
      log.error("Cannot send email for new change " + updatedChange.getId(), e);
    }
  }
}
