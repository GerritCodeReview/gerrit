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

import com.google.gerrit.client.changes.PatchSetPublishDetail;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.rpc.Handler;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collection;
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
  private final GerritConfig gerritConfig;
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
  private Map<ApprovalCategory.Id, ChangeApproval> given;

  @Inject
  PatchSetPublishDetailFactory(final PatchSetInfoFactory infoFactory,
      final ProjectCache projectCache, final GerritConfig gerritConfig,
      final ReviewDb db,
      final AccountInfoCacheFactory.Factory accountInfoCacheFactory,
      final ChangeControl.Factory changeControlFactory,
      final IdentifiedUser user, @Assisted final PatchSet.Id patchSetId) {
    this.projectCache = projectCache;
    this.infoFactory = infoFactory;
    this.gerritConfig = gerritConfig;
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
    given = new HashMap<ApprovalCategory.Id, ChangeApproval>();
    if (change.getStatus().isOpen()
        && patchSetId.equals(change.currentPatchSetId())) {
      computeAllowed();
      for (final ChangeApproval a : db.changeApprovals().byChangeUser(changeId,
          user.getAccountId())) {
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

    return detail;
  }

  private void computeAllowed() {
    final Set<AccountGroup.Id> am = user.getEffectiveGroups();
    final ProjectState pe = projectCache.get(change.getDest().getParentKey());
    computeAllowed(am, pe.getRights());
    computeAllowed(am, projectCache.getWildcardRights());
  }

  private void computeAllowed(final Set<AccountGroup.Id> am,
      final Collection<ProjectRight> list) {
    for (final ProjectRight r : list) {
      if (!am.contains(r.getAccountGroupId())) {
        continue;
      }

      Set<ApprovalCategoryValue.Id> s = allowed.get(r.getApprovalCategoryId());
      if (s == null) {
        s = new HashSet<ApprovalCategoryValue.Id>();
        allowed.put(r.getApprovalCategoryId(), s);
      }

      final ApprovalType at =
          gerritConfig.getApprovalType(r.getApprovalCategoryId());
      for (short m = r.getMinValue(); m <= r.getMaxValue(); m++) {
        final ApprovalCategoryValue v = at.getValue(m);
        if (v != null) {
          s.add(v.getId());
        }
      }
    }
  }
}
