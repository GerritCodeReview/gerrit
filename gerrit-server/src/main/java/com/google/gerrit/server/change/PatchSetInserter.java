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
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
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

  @Inject
  public PatchSetInserter(final GitReferenceUpdated gitRefUpdated,
      ChangeHooks hooks, TrackingFooters trackingFooters, final ReviewDb db,
      final PatchSetInfoFactory patchSetInfoFactory) {
    this.hooks = hooks;
    this.trackingFooters = trackingFooters;
    this.db = db;
    this.patchSetInfoFactory = patchSetInfoFactory;
  }

  public Change insertPatchSet(Change change, final PatchSet patchSet,
      final RevCommit commit, RefControl refControl, ChangeMessage cMessage,
      boolean copyLabels) throws OrmException, InvalidChangeOperationException,
      NoSuchChangeException {

    final Change.Id changeId = change.getId();
    final PatchSet.Id currentPatchSetId = change.currentPatchSetId();
    final PatchSet originalPS = db.patchSets().get(currentPatchSetId);

    if (originalPS == null) {
      throw new NoSuchChangeException(changeId);
    }

    db.changes().beginTransaction(change.getId());
    try {
      Change updatedChange =
          db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
            @Override
            public Change update(Change change) {
              if (change.getStatus().isOpen()) {
                return change;
              } else {
                return null;
              }
            }
          });
      if (updatedChange != null) {
        change = updatedChange;
      } else {
        throw new InvalidChangeOperationException(String.format(
            "Change %s is closed", change.getId()));
      }

      ChangeUtil.insertAncestors(db, patchSet.getId(), commit);
      db.patchSets().insert(Collections.singleton(patchSet));

      updatedChange =
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
        throw new InvalidChangeOperationException(String.format(
            "Change %s was modified", change.getId()));
      }

      if (copyLabels) {
        ApprovalsUtil.copyLabels(db, refControl.getProjectControl()
            .getLabelTypes(), originalPS.getId(), change.currentPatchSetId());
      }

      final List<FooterLine> footerLines = commit.getFooterLines();
      ChangeUtil.updateTrackingIds(db, change, trackingFooters, footerLines);

      if (cMessage != null) {
        db.changeMessages().insert(Collections.singleton(cMessage));
      }
      db.commit();

      hooks.doPatchsetCreatedHook(change, patchSet, db);
    } finally {
      db.rollback();
    }
    return change;
  }
}
