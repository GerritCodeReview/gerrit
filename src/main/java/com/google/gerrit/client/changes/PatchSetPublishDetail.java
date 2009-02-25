// Copyright 2009 Google Inc.
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

package com.google.gerrit.client.changes;

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
import com.google.gwtorm.client.OrmException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PatchSetPublishDetail {
  protected AccountInfoCache accounts;
  protected PatchSetInfo patchSetInfo;
  protected Change change;
  protected List<PatchLineComment> drafts;
  protected Map<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>> allowed;
  protected Map<ApprovalCategory.Id, ChangeApproval> given;

  public void load(final ReviewDb db, final Change c, final PatchSet.Id psi)
      throws OrmException {
    final AccountInfoCacheFactory acc = new AccountInfoCacheFactory(db);
    final Account.Id me = Common.getAccountId();
    change = c;
    patchSetInfo = db.patchSetInfo().get(psi);
    drafts = db.patchComments().draft(psi, me).toList();

    allowed = new HashMap<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>>();
    given = new HashMap<ApprovalCategory.Id, ChangeApproval>();
    if (change.getStatus().isOpen()) {
      computeAllowed();
      for (final ChangeApproval a : db.changeApprovals().byChangeUser(
          c.getId(), me)) {
        given.put(a.getCategoryId(), a);
      }
    }

    acc.want(change.getOwner());
    accounts = acc.create();
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

  public AccountInfoCache getAccounts() {
    return accounts;
  }

  public Change getChange() {
    return change;
  }

  public PatchSetInfo getPatchSetInfo() {
    return patchSetInfo;
  }

  public List<PatchLineComment> getDrafts() {
    return drafts;
  }

  public boolean isAllowed(final ApprovalCategory.Id id) {
    final Set<ApprovalCategoryValue.Id> s = getAllowed(id);
    return s != null && !s.isEmpty();
  }

  public Set<ApprovalCategoryValue.Id> getAllowed(final ApprovalCategory.Id id) {
    return allowed.get(id);
  }

  public ChangeApproval getChangeApproval(final ApprovalCategory.Id id) {
    return given.get(id);
  }
}
