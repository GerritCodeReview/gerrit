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

import com.google.common.base.Strings;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.change.EditCommitMessage.Input;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.CommitMessageEditedSender;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

class EditCommitMessage implements RestModifyView<RevisionResource, Input>,
    UiAction<RevisionResource> {
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final CommitMessageEditedSender.Factory commitMessageEditedSenderFactory;
  private final GitRepositoryManager gitManager;
  private final PersonIdent myIdent;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final ChangeJson json;

  static class Input {
    String message;
  }

  @Inject
  EditCommitMessage(final ReviewDb db, final IdentifiedUser currentUser,
      final CommitMessageEditedSender.Factory commitMessageEditedSenderFactory,
      final GitRepositoryManager gitManager,
      final PatchSetInserter.Factory patchSetInserterFactory,
      @GerritPersonIdent final PersonIdent myIdent,
      ChangeJson json) {
    this.db = db;
    this.currentUser = currentUser;
    this.commitMessageEditedSenderFactory = commitMessageEditedSenderFactory;
    this.gitManager = gitManager;
    this.myIdent = myIdent;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.json = json;
  }

  @Override
  public ChangeInfo apply(RevisionResource rsrc, Input input)
      throws BadRequestException, ResourceConflictException, EmailException,
      OrmException, NoSuchChangeException, IOException {
    if (Strings.isNullOrEmpty(input.message)) {
      throw new BadRequestException("message must be non-empty");
    }
    final Repository git;
    final PatchSet.Id patchSetId = rsrc.getPatchSet().getId();
    final Change.Id changeId = patchSetId.getParentKey();
    try {
      git = gitManager.openRepository(db.changes().get(changeId).getProject());
    } catch (RepositoryNotFoundException e) {
      throw new NoSuchChangeException(changeId, e);
    }
    try {
      return json.format(ChangeUtil.editCommitMessage(patchSetId, rsrc
          .getControl().getRefControl(), currentUser, input.message, db,
          commitMessageEditedSenderFactory, git, myIdent,
          patchSetInserterFactory));
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (MissingObjectException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (IncorrectObjectTypeException e) {
      throw new ResourceConflictException(e.getMessage());
    } catch (PatchSetInfoNotAvailableException e) {
      throw new ResourceConflictException(e.getMessage());
    } finally {
      git.close();
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    return new UiAction.Description()
        .setLabel("Change commit message")
        .setVisible(
            resource.getChange().getStatus().isOpen()
                && resource.getControl().canAddPatchSet());
  }
}
