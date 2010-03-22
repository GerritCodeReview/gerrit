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

import static com.google.gerrit.reviewdb.ApprovalCategory.SUBMIT;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetAncestor;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Tree;

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
    final Change.Id changeId = patchSetId.getParentKey();
    Change change = db.changes().get(changeId);
    if (change == null) {
      throw new NoSuchEntityException();
    }

    if (!patchSetId.equals(change.currentPatchSetId())) {
      throw new IllegalStateException("Patch set " + patchSetId
          + " not current");
    }
    if (change.getStatus().isClosed()) {
      throw new IllegalStateException("Change" + changeId + " is closed");
    }

    final List<PatchSetApproval> allApprovals =
        new ArrayList<PatchSetApproval>(db.patchSetApprovals().byPatchSet(
            patchSetId).toList());

    final PatchSetApproval.Key ak =
        new PatchSetApproval.Key(patchSetId, user.getAccountId(), SUBMIT);
    PatchSetApproval myAction = null;
    for (final PatchSetApproval ca : allApprovals) {
      if (ak.equals(ca.getKey())) {
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

    db.patchSetApprovals().upsert(Collections.singleton(myAction));

    change = db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        if (change.getStatus() == Change.Status.NEW) {
          change.setStatus(Change.Status.SUBMITTED);
          ChangeUtil.updated(change);
        }
        return change;
      }
    });


    if ((this.getDependsOn(change).isEmpty()) && (change.getStatus() == Change.Status.SUBMITTED)) {
      mergeCascade(change);
    }

    return changeDetailFactory.create(changeId).call();
  }

  private void mergeCascade(Change change) throws OrmException{
    List <Change> neededBy = getNeededBy(change);
    merger.merge(change.getDest());
    for (Change c : neededBy){
      if ((this.getDependsOn(c).isEmpty()) && (c.getStatus() == Change.Status.SUBMITTED))
        this.mergeCascade(c);
    }

  }

  private List<Change> getDependsOn(Change change) throws OrmException{
    List<Change> dependsOn = new ArrayList<Change>();

    PatchSet pset = db.patchSets().get(change.currPatchSetId());
    List <PatchSetAncestor> psetAncList = db.patchSetAncestors().ancestorsOf(pset.getId()).toList();

    for (PatchSetAncestor psetAnc : psetAncList){
      String revId = psetAnc.getAncestorRevision().get();
      List<Change> changesPerRevIdList = db.changes().byKey(new Change.Key("I"+revId)).toList();

      for (Change c : changesPerRevIdList){
        if ((c.getStatus() != Change.Status.MERGED) && (c.getStatus() != Change.Status.SUBMITTED))
          dependsOn.add(c);
      }
    }
    return dependsOn;
  }

  private List<Change> getNeededBy(Change change) throws OrmException{
    List<Change> neededBy = new ArrayList<Change>();

    String revId = change.getKey().get().substring(1);

    List <PatchSetAncestor> psetAncList = db.patchSetAncestors().descendantsOf(new RevId(revId)).toList();

    for (PatchSetAncestor psetAnc : psetAncList){
      Change.Id chId = psetAnc.getPatchSet().getParentKey();
      Change c = db.changes().get(chId);
      neededBy.add(c);
    }

    return neededBy;
  }
}
