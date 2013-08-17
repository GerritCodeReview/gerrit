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
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.change.EditMessage.Input;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.CommitMessageEditedSender;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

class EditMessage implements RestModifyView<RevisionResource, Input>,
    UiAction<RevisionResource> {

  private final Provider<ReviewDb> dbProvider;
  private final CommitMessageEditedSender.Factory commitMessageEditedSenderFactory;
  private final GitRepositoryManager gitManager;
  private final PersonIdent myIdent;
  private final PatchSetInserter.Factory patchSetInserterFactory;
  private final ChangeJson json;

  static class Input {
    @DefaultInput
    String message;
  }

  @Inject
  EditMessage(final Provider<ReviewDb> dbProvider,
      final CommitMessageEditedSender.Factory commitMessageEditedSenderFactory,
      final GitRepositoryManager gitManager,
      final PatchSetInserter.Factory patchSetInserterFactory,
      @GerritPersonIdent final PersonIdent myIdent,
      ChangeJson json) {
    this.dbProvider = dbProvider;
    this.commitMessageEditedSenderFactory = commitMessageEditedSenderFactory;
    this.gitManager = gitManager;
    this.myIdent = myIdent;
    this.patchSetInserterFactory = patchSetInserterFactory;
    this.json = json;
  }

  @Override
  public ChangeInfo apply(RevisionResource rsrc, Input input)
      throws BadRequestException, ResourceConflictException, EmailException,
      OrmException, ResourceNotFoundException, IOException {
    if (Strings.isNullOrEmpty(input.message)) {
      throw new BadRequestException("message must be non-empty");
    }

    final Repository git;
    try {
      git = gitManager.openRepository(rsrc.getChange().getProject());
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(e.getMessage());
    }

    try {
      return json.format(ChangeUtil.editCommitMessage(
          rsrc.getPatchSet().getId(),
          rsrc.getControl().getRefControl(),
          (IdentifiedUser) rsrc.getControl().getCurrentUser(),
          input.message, dbProvider.get(),
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
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException();
    } finally {
      git.close();
    }
  }

  @Override
  public UiAction.Description getDescription(RevisionResource resource) {
    PatchSet.Id current = resource.getChange().currentPatchSetId();
    return new UiAction.Description()
        .setLabel("Edit commit message")
        .setVisible(resource.getChange().getStatus().isOpen()
            && resource.getPatchSet().getId().equals(current)
            && resource.getControl().canAddPatchSet());
  }
}
