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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.mail.CommitMessageEditedSender;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;

class EditCommitMessageHandler extends Handler<ChangeDetail> {
  interface Factory {
    EditCommitMessageHandler create(PatchSet.Id patchSetId, String message);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;
  private final ChangeUtil changeUtil;
  private final PatchSet.Id patchSetId;
  @Nullable
  private final String message;
  private final PersonIdent myIdent;

  @Inject
  EditCommitMessageHandler(ChangeControl.Factory changeControlFactory,
      ChangeDetailFactory.Factory changeDetailFactory,
      CommitMessageEditedSender.Factory commitMessageEditedSenderFactory,
      @Assisted PatchSet.Id patchSetId,
      @Assisted @Nullable String message,
      ChangeUtil changeUtil,
      @GerritPersonIdent PersonIdent myIdent,
      PatchSetInserter.Factory patchSetInserterFactory) {
    this.changeControlFactory = changeControlFactory;
    this.changeDetailFactory = changeDetailFactory;
    this.changeUtil = changeUtil;
    this.patchSetId = patchSetId;
    this.message = message;
    this.myIdent = myIdent;
  }

  @Override
  public ChangeDetail call() throws NoSuchChangeException, OrmException,
      EmailException, NoSuchEntityException, PatchSetInfoNotAvailableException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      InvalidChangeOperationException, NoSuchProjectException {
    Change.Id changeId = patchSetId.getParentKey();
    ChangeControl control = changeControlFactory.validateFor(changeId);
    if (!control.canAddPatchSet()) {
      throw new InvalidChangeOperationException(
          "Not allowed to add new Patch Sets to: " + changeId.toString());
    }
    changeUtil.editCommitMessage(control, patchSetId, message, myIdent);
    return changeDetailFactory.create(changeId).call();
  }
}
