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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class PatchSetInserter {
  public static interface Factory {
    PatchSetInserter create(Repository git, RevWalk revWalk, RefControl refControl,
        Change change, RevCommit commit);
  }

  private final ChangeHooks hooks;
  private final TrackingFooters trackingFooters;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ReviewDb db;
  private final IdentifiedUser user;
  private final GitReferenceUpdated gitRefUpdated;

  private final Repository git;
  private final RevWalk revWalk;
  private final RevCommit commit;
  private final Change change;
  private final RefControl refControl;

  private PatchSet patchSet;
  private ChangeMessage changeMessage;
  private boolean copyLabels;

  @Inject
  public PatchSetInserter(ChangeHooks hooks,
      TrackingFooters trackingFooters,
      ReviewDb db,
      PatchSetInfoFactory patchSetInfoFactory,
      IdentifiedUser user,
      GitReferenceUpdated gitRefUpdated,
      @Assisted Repository git,
      @Assisted RevWalk revWalk,
      @Assisted RefControl refControl,
      @Assisted Change change,
      @Assisted RevCommit commit) {
    this.hooks = hooks;
    this.trackingFooters = trackingFooters;
    this.db = db;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.user = user;
    this.gitRefUpdated = gitRefUpdated;

    this.git = git;
    this.revWalk = revWalk;
    this.refControl = refControl;
    this.change = change;
    this.commit = commit;
  }

  public PatchSetInserter setPatchSet(PatchSet patchSet) {
    PatchSet.Id psid = patchSet.getId();
    checkArgument(psid.getParentKey().equals(change.getId()),
        "patch set %s not for change %s", psid, change.getId());
    checkArgument(psid.get() > change.currentPatchSetId().get(),
        "new patch set ID %s is not greater than current patch set ID %s",
        psid.get(), change.currentPatchSetId().get());
    this.patchSet = patchSet;
    return this;
  }

  public PatchSetInserter setMessage(String message) throws OrmException {
    changeMessage = new ChangeMessage(new ChangeMessage.Key(change.getId(),
        ChangeUtil.messageUUID(db)), user.getAccountId(), patchSet.getId());
    changeMessage.setMessage(message);
    return this;
  }

  public PatchSetInserter setMessage(ChangeMessage changeMessage) throws OrmException {
    this.changeMessage = changeMessage;
    return this;
  }

  public PatchSetInserter setCopyLabels(boolean copyLabels) {
    this.copyLabels = copyLabels;
    return this;
  }

  public Change insert() throws InvalidChangeOperationException, OrmException,
      IOException {
    checkState(patchSet != null, "patch set not set");

    Change updatedChange;
    RefUpdate ru = git.updateRef(patchSet.getRefName());
    ru.setExpectedOldObjectId(ObjectId.zeroId());
    ru.setNewObjectId(commit);
    ru.disableRefLog();
    if (ru.update(revWalk) != RefUpdate.Result.NEW) {
      throw new IOException(String.format(
          "Failed to create ref %s in %s: %s", patchSet.getRefName(),
          change.getDest().getParentKey().get(), ru.getResult()));
    }
    gitRefUpdated.fire(change.getProject(), ru);

    final PatchSet.Id currentPatchSetId = change.currentPatchSetId();

    db.changes().beginTransaction(change.getId());
    try {
      if (!db.changes().get(change.getId()).getStatus().isOpen()) {
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
      if (updatedChange == null) {
        throw new ChangeModifiedException(String.format(
            "Change %s was modified", change.getId()));
      }

      if (copyLabels) {
        ApprovalsUtil.copyLabels(db, refControl.getProjectControl()
            .getLabelTypes(), currentPatchSetId, change.currentPatchSetId());
      }

      final List<FooterLine> footerLines = commit.getFooterLines();
      ChangeUtil.updateTrackingIds(db, change, trackingFooters, footerLines);
      db.commit();

      if (changeMessage != null) {
        db.changeMessages().insert(Collections.singleton(changeMessage));
      }

      hooks.doPatchsetCreatedHook(change, patchSet, db);
    } finally {
      db.rollback();
    }
    return updatedChange;
  }

  public class ChangeModifiedException extends InvalidChangeOperationException {
    private static final long serialVersionUID = 1L;

    public ChangeModifiedException(String msg) {
      super(msg);
    }
  }
}
