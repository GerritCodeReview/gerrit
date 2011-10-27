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

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.RevertedSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.IOException;

import javax.annotation.Nullable;

class RevertChange extends Handler<ChangeDetail> {
  interface Factory {
    RevertChange create(PatchSet.Id patchSetId, String message);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final RevertedSender.Factory revertedSenderFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;
  private final ReplicationQueue replication;

  private final PatchSet.Id patchSetId;
  @Nullable
  private final String message;

  private final ChangeHookRunner hooks;

  private final GitRepositoryManager gitManager;
  private final PatchSetInfoFactory patchSetInfoFactory;

  private final PersonIdent myIdent;

  @Inject
  RevertChange(final ChangeControl.Factory changeControlFactory,
      final ReviewDb db, final IdentifiedUser currentUser,
      final RevertedSender.Factory revertedSenderFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted @Nullable final String message, final ChangeHookRunner hooks,
      final GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ReplicationQueue replication,
      @GerritPersonIdent final PersonIdent myIdent) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;
    this.revertedSenderFactory = revertedSenderFactory;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
    this.message = message;
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

    Change.Id revertedChangeId = ChangeUtil.revert(patchSetId, currentUser, message, db,
        revertedSenderFactory, hooks, gitManager, patchSetInfoFactory,
        replication, myIdent);

    return changeDetailFactory.create(revertedChangeId).call();
  }
}
