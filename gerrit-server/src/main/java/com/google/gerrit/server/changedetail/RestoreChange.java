// Copyright (C) 2012 The Android Open Source Project
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
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.RestoredSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.concurrent.Callable;

public class RestoreChange implements Callable<ReviewResult> {

  public interface Factory {
    RestoreChange create(PatchSet.Id patchSetId, String changeComment,
        boolean suppressMails);
  }

  private final RestoredSender.Factory restoredSenderFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final ChangeHooks hooks;

  private final PatchSet.Id patchSetId;
  private final String changeComment;
  private final boolean suppressMails;

  @Inject
  RestoreChange(final RestoredSender.Factory restoredSenderFactory,
      final ChangeControl.Factory changeControlFactory, final ReviewDb db,
      final IdentifiedUser currentUser, final ChangeHooks hooks,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted final String changeComment,
      @Assisted final boolean suppressMails) {
    this.restoredSenderFactory = restoredSenderFactory;
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;
    this.hooks = hooks;

    this.patchSetId = patchSetId;
    this.changeComment = changeComment;
    this.suppressMails = suppressMails;
  }

  @Override
  public ReviewResult call() throws EmailException,
      InvalidChangeOperationException, NoSuchChangeException, OrmException,
      PermissionDeniedException {
    if (suppressMails && !currentUser.getCapabilities().canSilenceEmail()) {
      throw new PermissionDeniedException(String.format(
        "%s does not have \"Silence email\" capability.",
        currentUser.getUserName()));
    }
    final ReviewResult result = new ReviewResult();

    final Change.Id changeId = patchSetId.getParentKey();
    result.setChangeId(changeId);
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    final PatchSet patch = db.patchSets().get(patchSetId);
    if (!control.canRestore()) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.RESTORE_NOT_PERMITTED));
    } else if (patch == null) {
      throw new NoSuchChangeException(changeId);
    } else {

      // Create a message to accompany the restored change
      final ChangeMessage cmsg =
          new ChangeMessage(new ChangeMessage.Key(changeId, ChangeUtil
              .messageUUID(db)), currentUser.getAccountId(), patchSetId);
      final StringBuilder msgBuf =
          new StringBuilder("Patch Set " + patchSetId.get() + ": Restored");
      if (changeComment != null && changeComment.length() > 0) {
        msgBuf.append("\n\n");
        msgBuf.append(changeComment);
      }
      cmsg.setMessage(msgBuf.toString());

      // Restore the change
      final Change updatedChange = db.changes().atomicUpdate(changeId,
          new AtomicUpdate<Change>() {
        @Override
        public Change update(Change change) {
          if (change.getStatus() == Change.Status.ABANDONED
              && change.currentPatchSetId().equals(patchSetId)) {
            change.setStatus(Change.Status.NEW);
            ChangeUtil.updated(change);
            return change;
          } else {
            return null;
          }
        }
      });

      ChangeUtil.updatedChange(
          db, currentUser, updatedChange, cmsg, restoredSenderFactory,
          suppressMails, "Change is not abandoned or patchset is not latest");

      hooks.doChangeRestoreHook(updatedChange, currentUser.getAccount(),
                                changeComment, db);
    }

    return result;
  }
}
