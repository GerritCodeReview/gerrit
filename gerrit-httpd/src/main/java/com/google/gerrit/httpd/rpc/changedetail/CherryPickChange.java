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

package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;

import javax.annotation.Nullable;

class CherryPickChange extends Handler<ChangeDetail> {
  interface Factory {
    CherryPickChange create(PatchSet.Id patchSetId,
        final @Assisted("message") String message,
        final @Assisted("destinationBranch") String destinationBranch);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final ChangeDetailFactory.Factory changeDetailFactory;
  private final GitReferenceUpdated replication;

  private final PatchSet.Id patchSetId;

  private final String message;
  private final String destinationBranch;

  private final ChangeHooks hooks;

  private final GitRepositoryManager gitManager;
  private final PatchSetInfoFactory patchSetInfoFactory;

  private final PersonIdent myIdent;

  @Inject
  CherryPickChange(final ChangeControl.Factory changeControlFactory,
      final ReviewDb db, final IdentifiedUser currentUser,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted("message") @Nullable final String message,
      @Assisted("destinationBranch") @Nullable final String destinationBranch,
      final ChangeHooks hooks, final GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final GitReferenceUpdated replication,
      @GerritPersonIdent final PersonIdent myIdent) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
    this.message = message;
    this.destinationBranch = destinationBranch;
    this.hooks = hooks;
    this.gitManager = gitManager;

    this.patchSetInfoFactory = patchSetInfoFactory;
    this.replication = replication;
    this.myIdent = myIdent;
  }

  @Override
  public ChangeDetail call() throws NoSuchChangeException, OrmException,
      EmailException, NoSuchEntityException, PatchSetInfoNotAvailableException,
      MissingObjectException, IncorrectObjectTypeException, IOException {

    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    if (!control.canAddPatchSet()) {
      throw new NoSuchChangeException(changeId);
    }

    Change.Id cherryPickedChangeId = ChangeUtil.cherryPick(patchSetId, currentUser,
        message, destinationBranch, db, hooks, gitManager,
        patchSetInfoFactory, replication, myIdent);

    return changeDetailFactory.create(cherryPickedChangeId).call();
  }
}
