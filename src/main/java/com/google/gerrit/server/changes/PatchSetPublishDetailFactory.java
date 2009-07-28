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

package com.google.gerrit.server.changes;

import com.google.gerrit.client.changes.PatchSetPublishDetail;
import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.data.AccountInfoCacheFactory;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
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
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.server.BaseServiceImplementation.Action;
import com.google.gerrit.server.BaseServiceImplementation.Failure;
import com.google.gerrit.server.patchsetinfo.PatchSetInfoFactory;
import com.google.gerrit.server.patchsetinfo.PatchSetInfoNotAvailableException;
import com.google.gwtorm.client.OrmException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final public class PatchSetPublishDetailFactory 
  implements Action<PatchSetPublishDetail> {
  
  final PatchSet.Id patchSetId;

  protected AccountInfoCache accounts;
  protected PatchSetInfo patchSetInfo;
  protected Change change;
  protected List<PatchLineComment> drafts;
  protected Map<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>> allowed;
  protected Map<ApprovalCategory.Id, ChangeApproval> given;
  
  public PatchSetPublishDetailFactory(PatchSet.Id patchSetId) {
    this.patchSetId = patchSetId;
  }

  @Override
  public PatchSetPublishDetail run(ReviewDb db) throws OrmException, Failure {

    final AccountInfoCacheFactory acc = new AccountInfoCacheFactory(db);
    final Account.Id me = Common.getAccountId();
    final Change.Id changeId = patchSetId.getParentKey();
    change = db.changes().get(changeId);
    try {
      patchSetInfo = PatchSetInfoFactory.patchSetInfoForPatchSetId(patchSetId);
    } catch (PatchSetInfoNotAvailableException e) {
      throw new Failure(e);
    }
    drafts = db.patchComments().draft(patchSetId, me).toList();

    allowed = new HashMap<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>>();
    given = new HashMap<ApprovalCategory.Id, ChangeApproval>();
    if (change.getStatus().isOpen() && 
        patchSetId.equals(change.currentPatchSetId())) {
      computeAllowed();
      for (final ChangeApproval a : db.changeApprovals().byChangeUser(
          changeId, me)) {
        given.put(a.getCategoryId(), a);
      }
    }

    acc.want(change.getOwner());
    accounts = acc.create();

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
    final Account.Id me = Common.getAccountId();
    final Set<AccountGroup.Id> am = Common.getGroupCache().getEffectiveGroups(me);
    final ProjectCache.Entry pe =
        Common.getProjectCache().get(change.getDest().getParentKey());
    computeAllowed(am, pe.getRights());
    computeAllowed(am, Common.getProjectCache().getWildcardRights());
  }

  private void computeAllowed(final Set<AccountGroup.Id> am,
      final Collection<ProjectRight> list) {
    final GerritConfig cfg = Common.getGerritConfig();
    for (final ProjectRight r : list) {
      if (!am.contains(r.getAccountGroupId())) {
        continue;
      }

      Set<ApprovalCategoryValue.Id> s = allowed.get(r.getApprovalCategoryId());
      if (s == null) {
        s = new HashSet<ApprovalCategoryValue.Id>();
        allowed.put(r.getApprovalCategoryId(), s);
      }

      final ApprovalType at = cfg.getApprovalType(r.getApprovalCategoryId());
      for (short m = r.getMinValue(); m <= r.getMaxValue(); m++) {
        final ApprovalCategoryValue v = at.getValue(m);
        if (v != null) {
          s.add(v.getId());
        }
      }
    }
  }

}
