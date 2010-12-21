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

import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.PatchSetPublishDetail;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.CanSubmitResult;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PatchSetPublishDetailFactory extends Handler<PatchSetPublishDetail> {
  interface Factory {
    PatchSetPublishDetailFactory create(PatchSet.Id patchSetId);
  }

  private final ProjectCache projectCache;
  private final PatchSetInfoFactory infoFactory;
  private final ApprovalTypes approvalTypes;
  private final ReviewDb db;
  private final ChangeControl.Factory changeControlFactory;
  private final AccountInfoCacheFactory aic;
  private final IdentifiedUser user;

  private final PatchSet.Id patchSetId;

  private AccountInfoCache accounts;
  private PatchSetInfo patchSetInfo;
  private Change change;
  private List<PatchLineComment> drafts;
  private Map<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>> allowed;
  private Map<ApprovalCategory.Id, PatchSetApproval> given;

  @Inject
  PatchSetPublishDetailFactory(final PatchSetInfoFactory infoFactory,
      final ProjectCache projectCache, final ApprovalTypes approvalTypes,
      final ReviewDb db,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final ChangeControl.Factory changeControlFactory,
      final IdentifiedUser user, @Assisted final PatchSet.Id patchSetId) {
    this.projectCache = projectCache;
    this.infoFactory = infoFactory;
    this.approvalTypes = approvalTypes;
    this.db = db;
    this.changeControlFactory = changeControlFactory;
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
    patchSetInfo = infoFactory.get(patchSetId);
    drafts = db.patchComments().draft(patchSetId, user.getAccountId()).toList();

    allowed = new HashMap<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>>();
    given = new HashMap<ApprovalCategory.Id, PatchSetApproval>();
    if (change.getStatus().isOpen()
        && patchSetId.equals(change.currentPatchSetId())) {
      computeAllowed();
      for (final PatchSetApproval a : db.patchSetApprovals().byPatchSetUser(
          patchSetId, user.getAccountId())) {
        given.put(a.getCategoryId(), a);
      }
    }

    aic.want(change.getOwner());
    accounts = aic.create();

    PatchSetPublishDetail detail = new PatchSetPublishDetail();
    detail.setAccounts(accounts);
    detail.setPatchSetInfo(patchSetInfo);
    detail.setChange(change);
    detail.setDrafts(drafts);
    detail.setAllowed(allowed);
    detail.setGiven(given);

    final CanSubmitResult canSubmitResult = control.canSubmit(patchSetId);
    detail.setSubmitAllowed(canSubmitResult == CanSubmitResult.OK);

    return detail;
  }

  private void computeAllowed() {
    final Set<AccountGroup.UUID> am = user.getEffectiveGroups();
    final ProjectState pe = projectCache.get(change.getProject());
    for (ApprovalCategory.Id category : approvalTypes.getApprovalCategories()) {
      RefControl rc = pe.controlFor(user).controlForRef(change.getDest());
      List<RefRight> categoryRights = rc.getApplicableRights(category);
      computeAllowed(am, categoryRights, category);
    }
  }

  private void computeAllowed(final Set<AccountGroup.UUID> am,
      final List<RefRight> list, ApprovalCategory.Id category) {

    Set<ApprovalCategoryValue.Id> s = allowed.get(category);
    if (s == null) {
      s = new HashSet<ApprovalCategoryValue.Id>();
      allowed.put(category, s);
    }

    for (final RefRight r : list) {
      if (!am.contains(r.getAccountGroupUUID())) {
        continue;
      }
      final ApprovalType at =
          approvalTypes.getApprovalType(r.getApprovalCategoryId());
      for (short m = r.getMinValue(); m <= r.getMaxValue(); m++) {
        final ApprovalCategoryValue v = at.getValue(m);
        if (v != null) {
          s.add(v.getId());
        }
      }
    }
  }
}
