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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.CommitMessageEditedSender;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

import javax.annotation.Nullable;

class EditCommitMessageHandler extends Handler<ChangeDetail> {
  interface Factory {
    EditCommitMessageHandler create(PatchSet.Id patchSetId, String message);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final ChangeDetailFactory.Factory changeDetailFactory;
  private final CommitMessageEditedSender.Factory commitMessageEditedSenderFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final PatchSet.Id patchSetId;
  @Nullable
  private final String message;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final GitRepositoryManager gitManager;
  private final PersonIdent myIdent;
  private final PatchSetInserter patchSetInserter;

  @Inject
  EditCommitMessageHandler(final ChangeControl.Factory changeControlFactory,
      final ReviewDb db, final IdentifiedUser currentUser,
      final ChangeDetailFactory.Factory changeDetailFactory,
      final CommitMessageEditedSender.Factory commitMessageEditedSenderFactory,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted @Nullable final String message,
      final CommitValidators.Factory commitValidatorsFactory,
      final GitRepositoryManager gitManager,
      final GitReferenceUpdated gitRefUpdated,
      @GerritPersonIdent final PersonIdent myIdent,
      final PatchSetInserter patchSetInserter) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;
    this.changeDetailFactory = changeDetailFactory;
    this.commitMessageEditedSenderFactory = commitMessageEditedSenderFactory;
    this.patchSetId = patchSetId;
    this.message = message;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.gitManager = gitManager;
    this.gitRefUpdated = gitRefUpdated;
    this.myIdent = myIdent;
    this.patchSetInserter = patchSetInserter;
  }

  @Override
  public ChangeDetail call() throws NoSuchChangeException, OrmException,
      EmailException, NoSuchEntityException, PatchSetInfoNotAvailableException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      InvalidChangeOperationException, NoSuchProjectException {

    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    if (!control.canAddPatchSet()) {
      throw new InvalidChangeOperationException(
          "Not allowed to add new Patch Sets to: " + changeId.toString());
    }

    final Repository git;
    try {
      git = gitManager.openRepository(db.changes().get(changeId).getProject());
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
    }
    try {
      CommitValidators commitValidators =
          commitValidatorsFactory.create(control.getRefControl(), new NoSshInfo(), git);

      ChangeUtil.editCommitMessage(patchSetId, control.getRefControl(), commitValidators, currentUser, message, db,
          commitMessageEditedSenderFactory, git, gitRefUpdated, myIdent, patchSetInserter);

      return changeDetailFactory.create(changeId).call();
    } finally {
      git.close();
    }
  }
}
