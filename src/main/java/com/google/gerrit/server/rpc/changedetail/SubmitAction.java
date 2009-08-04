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
import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.git.MergeQueue;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.rpc.Handler;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class SubmitAction extends Handler<VoidResult> {
  interface Factory {
    SubmitAction create(PatchSet.Id patchSetId);
  }

  private final ReviewDb db;
  private final MergeQueue merger;
  private final GerritConfig gerritConfig;
  private final FunctionState.Factory functionState;
  private final PatchSet.Id patchSetId;

  @Inject
  SubmitAction(final ReviewDb db, final MergeQueue mq, final GerritConfig gc,
      final FunctionState.Factory fs, @Assisted final PatchSet.Id patchSetId) {
    this.db = db;
    this.merger = mq;
    this.gerritConfig = gc;
    this.functionState = fs;
    this.patchSetId = patchSetId;
  }

  @Override
  public VoidResult call() throws OrmException, NoSuchEntityException,
      IllegalStateException {
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

    final List<ChangeApproval> allApprovals =
        new ArrayList<ChangeApproval>(db.changeApprovals().byChange(
            change.getId()).toList());

    final Account.Id me = Common.getAccountId();
    final ChangeApproval.Key ak =
        new ChangeApproval.Key(change.getId(), me, SUBMIT);
    ChangeApproval myAction = null;
    boolean isnew = true;
    for (final ChangeApproval ca : allApprovals) {
      if (ak.equals(ca.getKey())) {
        isnew = false;
        myAction = ca;
        myAction.setValue((short) 1);
        myAction.setGranted();
        break;
      }
    }
    if (myAction == null) {
      myAction = new ChangeApproval(ak, (short) 1);
      allApprovals.add(myAction);
    }

    final ApprovalType actionType =
        gerritConfig.getApprovalType(myAction.getCategoryId());
    if (actionType == null || !actionType.getCategory().isAction()) {
      throw new IllegalArgumentException(actionType.getCategory().getName()
          + " not an action");
    }

    final FunctionState fs = functionState.create(change, allApprovals);
    for (ApprovalType c : gerritConfig.getApprovalTypes()) {
      CategoryFunction.forCategory(c.getCategory()).run(c, fs);
    }
    if (!CategoryFunction.forCategory(actionType.getCategory()).isValid(me,
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
      db.changeApprovals().update(fs.getDirtyChangeApprovals(), txn);
    }
    if (isnew) {
      db.changeApprovals().insert(Collections.singleton(myAction), txn);
    } else {
      db.changeApprovals().update(Collections.singleton(myAction), txn);
    }
    txn.commit();

    if (change.getStatus() == Change.Status.SUBMITTED) {
      merger.merge(change.getDest());
    }

    return VoidResult.INSTANCE;
  }
}
