// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Collections;
import java.util.List;

public class PatchSetInserter {
  private final ChangeHooks hooks;
  private final TrackingFooters trackingFooters;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ReviewDb db;
  private final IdentifiedUser user;

  @Inject
  public PatchSetInserter(final ChangeHooks hooks,
      final TrackingFooters trackingFooters, final ReviewDb db,
      final PatchSetInfoFactory patchSetInfoFactory, final IdentifiedUser user) {
    this.hooks = hooks;
    this.trackingFooters = trackingFooters;
    this.db = db;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.user = user;
  }

  public Change insertPatchSet(Change change, final PatchSet patchSet,
      final RevCommit commit, RefControl refControl, String message,
      boolean copyLabels) throws OrmException, InvalidChangeOperationException,
      NoSuchChangeException {

    final PatchSet.Id currentPatchSetId = change.currentPatchSetId();

    if (patchSet.getId().get() <= currentPatchSetId.get()) {
      throw new InvalidChangeOperationException("New Patch Set ID ["
          + patchSet.getId().get()
          + "] is not greater than the current Patch Set ID ["
          + currentPatchSetId.get() + "]");
    }

    db.changes().beginTransaction(change.getId());
    try {
      if (!db.changes().get(change.getId()).getStatus().isOpen()) {
        throw new InvalidChangeOperationException(String.format(
            "Change %s is closed", change.getId()));
      }

      ChangeUtil.insertAncestors(db, patchSet.getId(), commit);
      db.patchSets().insert(Collections.singleton(patchSet));

      Change updatedChange =
          db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
            @Override
            public Change update(Change change) {
              if (change.getStatus().isClosed()) {
                return null;
              }
              if (!change.currentPatchSetId().equals(currentPatchSetId)) {
                return null;
              }
              if (change.getStatus() != Change.Status.DRAFT) {
                change.setStatus(Change.Status.NEW);
              }
              change.setLastSha1MergeTested(null);
              change.setCurrentPatchSet(patchSetInfoFactory.get(commit,
                  patchSet.getId()));
              ChangeUtil.updated(change);
              return change;
            }
          });
      if (updatedChange != null) {
        change = updatedChange;
      } else {
        throw new ChangeModifiedException(String.format(
            "Change %s was modified", change.getId()));
      }

      if (copyLabels) {
        ApprovalsUtil.copyLabels(db, refControl.getProjectControl()
            .getLabelTypes(), currentPatchSetId, change.currentPatchSetId());
      }

      final List<FooterLine> footerLines = commit.getFooterLines();
      ChangeUtil.updateTrackingIds(db, change, trackingFooters, footerLines);

      if (message != null) {
        final ChangeMessage cmsg =
            new ChangeMessage(new ChangeMessage.Key(change.getId(),
                ChangeUtil.messageUUID(db)), user.getAccountId(),
                patchSet.getId());

        cmsg.setMessage(message);
        db.changeMessages().insert(Collections.singleton(cmsg));
      }
      db.commit();

      hooks.doPatchsetCreatedHook(change, patchSet, db);
    } finally {
      db.rollback();
    }
    return change;
  }

  public class ChangeModifiedException extends InvalidChangeOperationException {
    private static final long serialVersionUID = 1L;

    public ChangeModifiedException(String msg) {
      super(msg);
    }
  }
}
