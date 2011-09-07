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
import com.google.gerrit.server.changedetail.MoveChange;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import javax.annotation.Nullable;

class MoveChangeHandler extends Handler<ChangeDetail> {
  interface Factory {
    MoveChangeHandler create(@Assisted PatchSet.Id patchSetId,
        @Assisted("branch") String branch,
        @Assisted("message") String changeComment);
  }

  private final MoveChange.Factory moveChangeFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  private final PatchSet.Id patchSetId;
  private final String branch;
  @Nullable
  private final String changeComment;

  @Inject
  MoveChangeHandler(final MoveChange.Factory moveChangeFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted("branch") final String branch,
      @Assisted("message") @Nullable final String changeComment) {
    this.moveChangeFactory = moveChangeFactory;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
    this.branch = branch;
    this.changeComment = changeComment;
  }

  @Override
  public ChangeDetail call() throws NoSuchChangeException, OrmException,
      EmailException, NoSuchEntityException, InvalidChangeOperationException,
      PatchSetInfoNotAvailableException {
    final Change.Id changeId = patchSetId.getParentKey();
    moveChangeFactory.create(patchSetId, branch, changeComment).call();
    return changeDetailFactory.create(changeId).call();
  }
}
