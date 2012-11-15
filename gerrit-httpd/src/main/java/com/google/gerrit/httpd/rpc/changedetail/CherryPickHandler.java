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
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.changedetail.CherryPickChange;
import com.google.gerrit.server.git.MergeException;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;

import javax.annotation.Nullable;

class CherryPickHandler extends Handler<ChangeDetail> {
  interface Factory {
    CherryPickHandler create(PatchSet.Id patchSetId,
        final @Assisted("message") String message,
        final @Assisted("destinationBranch") String destinationBranch);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;
  private final CherryPickChange cherryPickChange;

  private final PatchSet.Id patchSetId;
  private final String message;
  private final String destinationBranch;

  @Inject
  CherryPickHandler(final ChangeControl.Factory changeControlFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      final CherryPickChange cherryPickChange,
      @GerritPersonIdent final PersonIdent myIdent,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted("message") @Nullable final String message,
      @Assisted("destinationBranch") @Nullable final String destinationBranch) {
    this.changeControlFactory = changeControlFactory;
    this.changeDetailFactory = changeDetailFactory;
    this.cherryPickChange = cherryPickChange;
    this.patchSetId = patchSetId;
    this.message = message;
    this.destinationBranch = destinationBranch;
  }

  @Override
  public ChangeDetail call() throws OrmException,
      EmailException, NoSuchEntityException, PatchSetInfoNotAvailableException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      InvalidChangeOperationException, NoSuchChangeException, MergeException {

    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    if (!control.getProjectControl().controlForRef(destinationBranch).canUpload()) {
      throw new InvalidChangeOperationException(
          "Not allowed to cherry pick " + changeId.toString() + " to " + destinationBranch);
    }

    Change.Id cherryPickedChangeId = cherryPickChange.cherryPick(patchSetId,
        message, destinationBranch);

    return changeDetailFactory.create(cherryPickedChangeId).call();
  }
}
