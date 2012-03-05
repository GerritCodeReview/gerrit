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

import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Constants;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.concurrent.Callable;

public class MoveChange implements Callable<VoidResult> {

  public interface Factory {
    MoveChange create(@Assisted PatchSet.Id patchSetId,
        @Assisted("branch") String branch,
        @Assisted("message") String changeComment);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;

  private final PatchSet.Id patchSetId;
  private final String branch;
  @Nullable
  private final String changeComment;

  @Inject
  MoveChange(final ChangeControl.Factory changeControlFactory, final ReviewDb db,
      final IdentifiedUser currentUser, final @Assisted PatchSet.Id patchSetId,
      final @Assisted("branch") String branch,
      final @Assisted("message") @Nullable String changeComment) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;

    this.patchSetId = patchSetId;
    this.branch = branch;
    this.changeComment = changeComment;
  }

  @Override
  public VoidResult call() throws EmailException, InvalidChangeOperationException,
      NoSuchChangeException, OrmException {
    final String ref = (branch.startsWith(Constants.R_REFS) ?
        "" : Constants.R_HEADS) + branch;
    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    if (!control.canMoveTo(ref)) {
      throw new InvalidChangeOperationException("Permission denied moving"
          + " destination branch to " + branch + " for change " + changeId);
    }

    final PatchSet patch = db.patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }

    final ChangeMessage cmsg =
        new ChangeMessage(new ChangeMessage.Key(changeId, ChangeUtil
            .messageUUID(db)), currentUser.getAccountId(), patchSetId);
    final StringBuilder msgBuf =
        new StringBuilder("Patch set " + patchSetId.get() +
            ": Change destination moved to " + branch + " branch");
    if (changeComment != null && changeComment.length() > 0) {
      msgBuf.append("\n\n");
      msgBuf.append(changeComment);
    }
    cmsg.setMessage(msgBuf.toString());

    final Change updatedChange = db.changes().atomicUpdate(changeId,
        new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        if (change.getStatus() == Change.Status.NEW
            && change.currentPatchSetId().equals(patchSetId)) {
          change.setDest(new Branch.NameKey(change.getProject(), ref));
          ChangeUtil.updated(change);
          return change;
        } else {
          return null;
        }
      }
    });

    if (updatedChange == null) {
      String err = "Change is closed or patchset is not latest";
      throw new InvalidChangeOperationException(err);
    }
    db.changeMessages().insert(Collections.singleton(cmsg));

    ApprovalsUtil.clearCurrentPatchSetApprovals(db, updatedChange);

    return VoidResult.INSTANCE;
  }
}
