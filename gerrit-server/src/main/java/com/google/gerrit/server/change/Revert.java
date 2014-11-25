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

package com.google.gerrit.server.change;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;

@Singleton
public class Revert implements RestModifyView<ChangeResource, RevertInput>,
    UiAction<ChangeResource> {
  private final ChangeJson json;
  private final ChangeUtil changeUtil;
  private final PersonIdent myIdent;

  @Inject
  Revert(ChangeJson json,
      ChangeUtil changeUtil,
      @GerritPersonIdent PersonIdent myIdent) {
    this.json = json;
    this.changeUtil = changeUtil;
    this.myIdent = myIdent;
  }

  @Override
  public ChangeInfo apply(ChangeResource req, RevertInput input)
      throws AuthException, BadRequestException, ResourceConflictException,
      ResourceNotFoundException, IOException, OrmException, EmailException {
    ChangeControl control = req.getControl();
    Change change = req.getChange();
    if (!control.canAddPatchSet()) {
      throw new AuthException("revert not permitted");
    } else if (change.getStatus() != Status.MERGED) {
      throw new ResourceConflictException("change is " + status(change));
    }

    try {
      Change.Id revertedChangeId =
          changeUtil.revert(control, change.currentPatchSetId(),
              Strings.emptyToNull(input.message),
              new PersonIdent(myIdent, TimeUtil.nowTs()), new NoSshInfo());

      return json.format(revertedChangeId);
    } catch (InvalidChangeOperationException e) {
      throw new BadRequestException(e.getMessage());
    } catch (NoSuchChangeException e) {
      throw new ResourceNotFoundException(e.getMessage());
    }
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    return new UiAction.Description()
      .setLabel("Revert")
      .setTitle("Revert the change")
      .setVisible(resource.getChange().getStatus() == Status.MERGED
          && resource.getControl().getRefControl().canUpload());
  }

  private static String status(Change change) {
    return change != null ? change.getStatus().name().toLowerCase() : "deleted";
   }
 }
