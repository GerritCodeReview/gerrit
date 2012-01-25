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

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.RebasedPatchSetSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
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

class RebaseChange extends Handler<ChangeDetail> {
  interface Factory {
    RebaseChange create(PatchSet.Id patchSetId);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;
  private final RebasedPatchSetSender.Factory rebasedPatchSetSenderFactory;

  private final ChangeDetailFactory.Factory changeDetailFactory;
  private final ReplicationQueue replication;

  private final PatchSet.Id patchSetId;

  private final ChangeHookRunner hooks;

  private final GitRepositoryManager gitManager;
  private final PatchSetInfoFactory patchSetInfoFactory;

  private final PersonIdent myIdent;

  private final ApprovalTypes approvalTypes;

  @Inject
  RebaseChange(final ChangeControl.Factory changeControlFactory,
      final ReviewDb db, final IdentifiedUser currentUser,
      final RebasedPatchSetSender.Factory rebasedPatchSetSenderFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId, final ChangeHookRunner hooks,
      final GitRepositoryManager gitManager,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ReplicationQueue replication,
      @GerritPersonIdent final PersonIdent myIdent,
      final ApprovalTypes approvalTypes) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;
    this.rebasedPatchSetSenderFactory = rebasedPatchSetSenderFactory;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
    this.hooks = hooks;
    this.gitManager = gitManager;

    this.patchSetInfoFactory = patchSetInfoFactory;
    this.replication = replication;
    this.myIdent = myIdent;

    this.approvalTypes = approvalTypes;
  }

  @Override
  public ChangeDetail call() throws NoSuchChangeException, OrmException,
      EmailException, NoSuchEntityException, PatchSetInfoNotAvailableException,
      MissingObjectException, IncorrectObjectTypeException, IOException,
      InvalidChangeOperationException {

    ChangeUtil.rebaseChange(patchSetId, currentUser, db,
        rebasedPatchSetSenderFactory, hooks, gitManager, patchSetInfoFactory,
        replication, myIdent, changeControlFactory, approvalTypes);

    return changeDetailFactory.create(patchSetId.getParentKey()).call();
  }
}
