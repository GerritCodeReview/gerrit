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
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.change.ChangeJson.ChangeInfo;
import com.google.gerrit.server.change.EditMessage.Input;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;

@Singleton
class EditMessage implements RestModifyView<RevisionResource, Input>,
    UiAction<RevisionResource> {
  private final ChangeUtil changeUtil;
  private final PersonIdent myIdent;
  private final ChangeJson json;

  static class Input {
    @DefaultInput
    String message;
  }

  @Inject
  EditMessage(ChangeUtil changeUtil,
      @GerritPersonIdent PersonIdent myIdent,
      ChangeJson json) {
    this.changeUtil = changeUtil;
    this.myIdent = myIdent;
    this.json = json;
  }

  @Override
  public ChangeInfo apply(RevisionResource rsrc, Input input)
      throws BadRequestException, ResourceConflictException,
      ResourceNotFoundException, EmailException, OrmException, IOException {
    if (Strings.isNullOrEmpty(input.message)) {
      throw new BadRequestException("message must be non-empty");
    } else if (!rsrc.getPatchSet().getId()
        .equals(rsrc.getChange().currentPatchSetId())) {
      throw new ResourceConflictException(String.format(
          "revision %s is not current revision",
          rsrc.getPatchSet().getRevision().get()));
    }
    try {
      return json.format(changeUtil.editCommitMessage(
          rsrc.getControl(),
          rsrc.getPatchSet().getId(),
          input.message,
          new PersonIdent(myIdent, TimeUtil.nowTs())));
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException();
    } catch (MissingObjectException | IncorrectObjectTypeException
        | PatchSetInfoNotAvailableException e) {
      throw new ResourceConflictException(e.getMessage());
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
