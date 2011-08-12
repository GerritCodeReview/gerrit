// Copyright (C) 2009 The Android Open Source Project
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
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;

class DeleteDraftChange extends Handler<VoidResult> {
  interface Factory {
    DeleteDraftChange create(PatchSet.Id patchSetId);
  }

  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final ChangeDetailFactory.Factory changeDetailFactory;
  private final GitRepositoryManager gitManager;
  private final ReplicationQueue replication;

  private final PatchSet.Id patchSetId;

  @Inject
  DeleteDraftChange(final ReviewDb db,
      final ChangeControl.Factory changeControlFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      final GitRepositoryManager gitManager,
      final ReplicationQueue replication,
      @Assisted final PatchSet.Id patchSetId) {
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.changeDetailFactory = changeDetailFactory;
    this.gitManager = gitManager;
    this.replication = replication;

    this.patchSetId = patchSetId;
  }

  @Override
  public VoidResult call() throws NoSuchChangeException, OrmException, IOException {

    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    if (!control.isOwner() || !control.isVisible(db)) {
      throw new NoSuchChangeException(changeId);
    }

    ChangeUtil.deleteDraftChange(patchSetId, gitManager, replication, db);
    return VoidResult.INSTANCE;
  }
}