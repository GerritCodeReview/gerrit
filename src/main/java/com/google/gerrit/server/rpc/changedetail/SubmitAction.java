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

package com.google.gerrit.server.rpc.changedetail;

import static com.google.gerrit.client.reviewdb.ApprovalCategory.SUBMIT;

import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.ApprovalTypes;
import com.google.gerrit.client.data.ChangeDetail;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetApproval;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.git.MergeQueue;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.rpc.Handler;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class SubmitAction extends Handler<ChangeDetail> {
  interface Factory {
    SubmitAction create(PatchSet.Id patchSetId);
  }

  private final ReviewDb db;
  private final MergeQueue merger;
  private final ApprovalTypes approvalTypes;
  private final FunctionState.Factory functionState;
  private final IdentifiedUser user;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  private final PatchSet.Id patchSetId;

  @Inject
  SubmitAction(final ReviewDb db, final MergeQueue mq, final ApprovalTypes at,
      final FunctionState.Factory fs, final IdentifiedUser user,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId) {
    this.db = db;
    this.merger = mq;
    this.approvalTypes = at;
    this.functionState = fs;
    this.user = user;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
  }

  @Override
  public ChangeDetail call() throws OrmException, NoSuchEntityException,
      IllegalStateException, PatchSetInfoNotAvailableException,
      NoSuchChangeException {
    final Change change = db.changes().get(patchSetId.getParentKey());
    if (change == null) {
      throw new NoSuchEntityException();
    }

    if (!patchSetId.equals(change.currentPatchSetId())) {
      throw new IllegalStateException("Patch set " + patchSetId
          + " not current");
    }
    if (change.getStatus().isClosed()) {
      throw new IllegalStateException("Change" + change.getId() + " is closed");
    }

    final List<PatchSetApproval> allApprovals =
        new ArrayList<PatchSetApproval>(db.patchSetApprovals().byPatchSet(
            patchSetId).toList());

    final PatchSetApproval.Key ak =
        new PatchSetApproval.Key(patchSetId, user.getAccountId(), SUBMIT);
    PatchSetApproval myAction = null;
    boolean isnew = true;
    for (final PatchSetApproval ca : allApprovals) {
      if (ak.equals(ca.getKey())) {
        isnew = false;
        myAction = ca;
        myAction.setValue((short) 1);
        myAction.setGranted();
        break;
      }
    }
    if (myAction == null) {
      myAction = new PatchSetApproval(ak, (short) 1);
      allApprovals.add(myAction);
    }

    final ApprovalType actionType =
        approvalTypes.getApprovalType(myAction.getCategoryId());
    if (actionType == null || !actionType.getCategory().isAction()) {
      throw new IllegalArgumentException(actionType.getCategory().getName()
          + " not an action");
    }

    final FunctionState fs =
        functionState.create(change, patchSetId, allApprovals);
    for (ApprovalType c : approvalTypes.getApprovalTypes()) {
      CategoryFunction.forCategory(c.getCategory()).run(c, fs);
    }
    if (!CategoryFunction.forCategory(actionType.getCategory()).isValid(user,
        actionType, fs)) {
      throw new IllegalStateException(actionType.getCategory().getName()
          + " not permitted");
    }
    fs.normalize(actionType, myAction);
    if (myAction.getValue() <= 0) {
      throw new IllegalStateException(actionType.getCategory().getName()
          + " not permitted");
    }

    if (change.getStatus() == Change.Status.NEW) {
      change.setStatus(Change.Status.SUBMITTED);
      ChangeUtil.updated(change);
    }

    final Transaction txn = db.beginTransaction();
    db.changes().update(Collections.singleton(change), txn);
    if (change.getStatus().isClosed()) {
      db.patchSetApprovals().update(fs.getDirtyChangeApprovals(), txn);
    }
    if (isnew) {
      db.patchSetApprovals().insert(Collections.singleton(myAction), txn);
    } else {
      db.patchSetApprovals().update(Collections.singleton(myAction), txn);
    }
    txn.commit();

    if (change.getStatus() == Change.Status.SUBMITTED) {
      merger.merge(change.getDest());
    }

    return changeDetailFactory.create(change.getId()).call();
  }
}
