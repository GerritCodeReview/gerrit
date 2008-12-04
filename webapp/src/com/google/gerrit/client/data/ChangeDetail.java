// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.data;

import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/** Detail necessary to display{@link ChangeScreen}. */
public class ChangeDetail {
  protected Change change;
  protected AccountInfo owner;
  protected List<ApprovalDetail> approvals;
  protected PatchSet currentPatchSet;
  protected PatchSetInfo currentPatchSetInfo;

  public ChangeDetail() {
  }

  public void load(final ReviewDb db, final AccountCache acc, final Change c)
      throws OrmException {
    change = c;
    owner = new AccountInfo(acc.get(change.getOwner()));

    final HashMap<Account.Id, ApprovalDetail> ad =
        new HashMap<Account.Id, ApprovalDetail>();
    {
      final ApprovalDetail d = new ApprovalDetail(owner);
      d.sortOrder = ApprovalDetail.EG_0;
      ad.put(change.getOwner(), d);
    }
    for (ChangeApproval ca : db.changeApprovals().byChange(change.getKey())) {
      ApprovalDetail d = ad.get(ca.getAccountId());
      if (d == null) {
        d = new ApprovalDetail(new AccountInfo(acc.get(ca.getAccountId())));
        ad.put(ca.getAccountId(), d);
      }
      d.add(ca);
    }
    approvals = new ArrayList<ApprovalDetail>(ad.values());
    Collections.sort(approvals, new Comparator<ApprovalDetail>() {
      public int compare(final ApprovalDetail o1, final ApprovalDetail o2) {
        return o2.sortOrder.compareTo(o1.sortOrder);
      }
    });

    final PatchSet.Id ps = change.currentPatchSetId();
    if (ps != null) {
      currentPatchSet = db.patchSets().get(ps);
      currentPatchSetInfo = db.patchSetInfo().get(ps);
    }
  }

  public Change getChange() {
    return change;
  }

  public List<ApprovalDetail> getApprovals() {
    return approvals;
  }

  public PatchSet getCurrentPatchSet() {
    return currentPatchSet;
  }

  public PatchSetInfo getCurrentPatchSetInfo() {
    return currentPatchSetInfo;
  }

  public String getDescription() {
    return currentPatchSetInfo != null ? currentPatchSetInfo.getMessage() : "";
  }
}
