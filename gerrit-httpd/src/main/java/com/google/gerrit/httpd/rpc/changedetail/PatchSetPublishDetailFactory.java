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

import com.google.gerrit.common.data.ApprovalDetail;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.PatchSetPublishDetail;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PatchSetPublishDetailFactory extends Handler<PatchSetPublishDetail> {
  interface Factory {
    PatchSetPublishDetailFactory create(PatchSet.Id patchSetId);
  }

  private final PatchSetInfoFactory infoFactory;
  private final ReviewDb db;
  private final FunctionState.Factory functionState;
  private final ChangeControl.Factory changeControlFactory;
  private final ChangeControl.GenericFactory changeControlGenericFactory;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;
  private final ApprovalTypes approvalTypes;
  private final AccountInfoCacheFactory aic;
  private final IdentifiedUser user;

  private final PatchSet.Id patchSetId;

  private PatchSetInfo patchSetInfo;
  private Change change;
  private List<PatchLineComment> drafts;

  @Inject
  PatchSetPublishDetailFactory(final PatchSetInfoFactory infoFactory,
      final ReviewDb db,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final FunctionState.Factory functionState,
      final ChangeControl.Factory changeControlFactory,
      final ChangeControl.GenericFactory changeControlGenericFactory,
      final IdentifiedUser.GenericFactory identifiedUserFactory,
      final ApprovalTypes approvalTypes,
      final IdentifiedUser user, @Assisted final PatchSet.Id patchSetId) {
    this.infoFactory = infoFactory;
    this.db = db;
    this.functionState = functionState;
    this.changeControlFactory = changeControlFactory;
    this.changeControlGenericFactory = changeControlGenericFactory;
    this.identifiedUserFactory = identifiedUserFactory;
    this.approvalTypes = approvalTypes;
    this.aic = accountInfoCacheFactory.create();
    this.user = user;

    this.patchSetId = patchSetId;
  }

  @Override
  public PatchSetPublishDetail call() throws OrmException,
      PatchSetInfoNotAvailableException, NoSuchChangeException {
    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    change = control.getChange();
    PatchSet patchSet = db.patchSets().get(patchSetId);
    patchSetInfo = infoFactory.get(change, patchSet);
    drafts = db.patchComments().draftByPatchSetAuthor(patchSetId, user.getAccountId()).toList();

    aic.want(change.getOwner());

    PatchSetPublishDetail detail = new PatchSetPublishDetail();
    detail.setPatchSetInfo(patchSetInfo);
    detail.setChange(change);
    detail.setDrafts(drafts);

    List<PermissionRange> allowed = Collections.emptyList();
    List<PatchSetApproval> given = Collections.emptyList();

    if (change.getStatus().isOpen()
        && patchSetId.equals(change.currentPatchSetId())) {
      // TODO Push this selection of labels down into the Prolog interpreter.
      // Ideally we discover the labels the user can apply here based on doing
      // a findall() over the space of labels they can apply combined against
      // the submit rule, thereby skipping any mutually exclusive cases. However
      // those are not common, so it might just be reasonable to take this
      // simple approach.

      Map<String, PermissionRange> rangeByName =
          new HashMap<String, PermissionRange>();
      for (PermissionRange r : control.getLabelRanges()) {
        if (r.isLabel()) {
          rangeByName.put(r.getLabel(), r);
        }
      }
      allowed = new ArrayList<PermissionRange>();

      given = db.patchSetApprovals() //
          .byPatchSetUser(patchSetId, user.getAccountId()) //
          .toList();

      boolean couldSubmit = false;
      List<SubmitRecord> submitRecords = control.canSubmit(db, patchSet);
      for (SubmitRecord rec : submitRecords) {
        if (rec.status == SubmitRecord.Status.OK) {
          couldSubmit = true;
        }

        if (rec.labels != null) {
          int ok = 0;

          for (SubmitRecord.Label lbl : rec.labels) {
            aic.want(lbl.appliedBy);

            boolean canMakeOk = false;
            PermissionRange range = rangeByName.get(lbl.label);
            if (range != null) {
              if (!allowed.contains(range)) {
                allowed.add(range);
              }

              ApprovalType at = approvalTypes.byLabel(lbl.label);
              if (at == null || at.getMax().getValue() == range.getMax()) {
                canMakeOk = true;
              }
            }

            switch (lbl.status) {
              case OK:
              case MAY:
                ok++;
                break;

              case NEED:
                if (canMakeOk) {
                  ok++;
                }
                break;
            }
          }

          if (rec.status == SubmitRecord.Status.NOT_READY
              && ok == rec.labels.size()) {
            couldSubmit = true;
          }
        }
      }

      if (couldSubmit && control.getRefControl().canSubmit()) {
        detail.setCanSubmit(true);
      }

      detail.setSubmitRecords(submitRecords);
    }

    detail.setLabels(allowed);
    detail.setGiven(given);
    loadApprovals(detail, control);

    detail.setAccounts(aic.create());

    return detail;
  }

  private void loadApprovals(final PatchSetPublishDetail detail,
      final ChangeControl control) throws OrmException, NoSuchChangeException {
    final PatchSet.Id psId = detail.getChange().currentPatchSetId();
    final Change.Id changeId = patchSetId.getParentKey();
    final List<PatchSetApproval> allApprovals =
        db.patchSetApprovals().byChange(changeId).toList();

    if (detail.getChange().getStatus().isOpen()) {
      final FunctionState fs = functionState.create(control, psId, allApprovals);

      for (final ApprovalType at : approvalTypes.getApprovalTypes()) {
        CategoryFunction.forCategory(at.getCategory()).run(at, fs);
      }
    }

    final boolean canRemoveReviewers = detail.getChange().getStatus().isOpen() //
        && control.getCurrentUser() instanceof IdentifiedUser;
    final HashMap<Account.Id, ApprovalDetail> ad =
        new HashMap<Account.Id, ApprovalDetail>();
    for (PatchSetApproval ca : allApprovals) {
      ApprovalDetail d = ad.get(ca.getAccountId());
      if (d == null) {
        d = new ApprovalDetail(ca.getAccountId());
        d.setCanRemove(canRemoveReviewers);
        ad.put(d.getAccount(), d);
      }
      if (d.canRemove()) {
        d.setCanRemove(control.canRemoveReviewer(ca));
      }
      if (ca.getPatchSetId().equals(psId)) {
        d.add(ca);
      }
      final ChangeControl chgCtrl =
          changeControlGenericFactory.controlFor(detail.getChange(),
              identifiedUserFactory.create(ca.getAccountId()));
      for (PermissionRange pr : chgCtrl.getLabelRanges()) {
        if (pr.getMin() != 0 || pr.getMax() != 0) {
          d.votable(pr.getLabel());
        }
      }
    }

    final Account.Id owner = detail.getChange().getOwner();
    if (ad.containsKey(owner)) {
      // Ensure the owner always sorts to the top of the table
      ad.get(owner).sortFirst();
    }

    aic.want(ad.keySet());
    detail.setApprovals(ad.values());
  }
}
