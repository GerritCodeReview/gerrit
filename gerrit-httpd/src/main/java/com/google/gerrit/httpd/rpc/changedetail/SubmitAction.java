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

import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.MergeOp;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class SubmitAction extends Handler<ChangeDetail> {
  interface Factory {
    SubmitAction create(PatchSet.Id patchSetId);
  }

  private final ReviewDb db;
  private final MergeQueue merger;
  private final IdentifiedUser user;
  private final ChangeDetailFactory.Factory changeDetailFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final MergeOp.Factory opFactory;

  private final PatchSet.Id patchSetId;

  @Inject
  SubmitAction(final ReviewDb db, final MergeQueue mq,
      final IdentifiedUser user,
      final ChangeDetailFactory.Factory changeDetailFactory,
      final ChangeControl.Factory changeControlFactory,
      final MergeOp.Factory opFactory,
      @Assisted final PatchSet.Id patchSetId) {
    this.db = db;
    this.merger = mq;
    this.user = user;
    this.changeControlFactory = changeControlFactory;
    this.changeDetailFactory = changeDetailFactory;
    this.opFactory = opFactory;

    this.patchSetId = patchSetId;
  }

  @Override
  public ChangeDetail call() throws OrmException, NoSuchEntityException,
      IllegalStateException, PatchSetInfoNotAvailableException,
      NoSuchChangeException {

    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl changeControl =
        changeControlFactory.validateFor(changeId);

    Capable err = changeControl.canSubmit(db, patchSetId);
    if (err == Capable.OK) {
      ChangeUtil.submit(patchSetId, user, db, opFactory, merger);
      return changeDetailFactory.create(changeId).call();
    } else {
      throw new IllegalStateException(err.getMessage());
    }
  }
}
