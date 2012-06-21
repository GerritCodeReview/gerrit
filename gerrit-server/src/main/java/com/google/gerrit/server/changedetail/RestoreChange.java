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
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ProjectUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.RestoredSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class RestoreChange implements Callable<ReviewResult> {

  private final RestoredSender.Factory restoredSenderFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final GitRepositoryManager repoManager;
  private final IdentifiedUser currentUser;
  private final ChangeHooks hooks;

  @Argument(index = 0, required = true, multiValued = false,
            usage = "change to restore", metaVar = "CHANGE")
  private Change.Id changeId;
  public void setChangeId(final Change.Id changeId) {
    this.changeId = changeId;
  }

  @Option(name = "--message", aliases = {"-m"},
          usage = "optional message to append to change")
  private String message;
  public void setMessage(final String message) {
    this.message = message;
  }

  @Inject
  RestoreChange(final RestoredSender.Factory restoredSenderFactory,
      final ChangeControl.Factory changeControlFactory, final ReviewDb db,
      final GitRepositoryManager repoManager, final IdentifiedUser currentUser,
      final ChangeHooks hooks) {
    this.restoredSenderFactory = restoredSenderFactory;
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.repoManager = repoManager;
    this.currentUser = currentUser;
    this.hooks = hooks;

    changeId = null;
    message = null;
  }

  @Override
  public ReviewResult call() throws EmailException, NoSuchChangeException,
      InvalidChangeOperationException, OrmException,
      RepositoryNotFoundException, IOException {
    if (changeId == null) {
      throw new InvalidChangeOperationException("changeId is required");
    }

    final ReviewResult result = new ReviewResult();
    result.setChangeId(changeId);

    final ChangeControl control = changeControlFactory.validateFor(changeId);
    final Change change = db.changes().get(changeId);
    final PatchSet.Id patchSetId = change.currentPatchSetId();
    if (!control.canRestore()) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.RESTORE_NOT_PERMITTED));
      return result;
    }

    final PatchSet patch = db.patchSets().get(patchSetId);
    if (patch == null) {
      throw new NoSuchChangeException(changeId);
    }

    final Branch.NameKey destBranch = control.getChange().getDest();
    if (!ProjectUtil.branchExists(repoManager, destBranch)) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.DEST_BRANCH_NOT_FOUND, destBranch.get()));
      return result;
    }

    // Create a message to accompany the restored change
    final ChangeMessage cmsg =
        new ChangeMessage(new ChangeMessage.Key(changeId, ChangeUtil
            .messageUUID(db)), currentUser.getAccountId(), patchSetId);
    final StringBuilder msgBuf =
        new StringBuilder("Patch Set " + patchSetId.get() + ": Restored");
    if (message != null && message.length() > 0) {
      msgBuf.append("\n\n");
      msgBuf.append(message);
    }
    cmsg.setMessage(msgBuf.toString());

    // Restore the change
    final Change updatedChange = db.changes().atomicUpdate(changeId,
        new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus() == Change.Status.ABANDONED) {
              change.setStatus(Change.Status.NEW);
              ChangeUtil.updated(change);
              return change;
            } else {
              return null;
            }
          }
        });

    if (updatedChange == null) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.CHANGE_NOT_ABANDONED));
      return result;
    }

    ChangeUtil.updatedChange(db, currentUser, updatedChange, cmsg,
                             restoredSenderFactory);
    hooks.doChangeRestoreHook(updatedChange, currentUser.getAccount(),
                              message, db);

    return result;
  }
}
