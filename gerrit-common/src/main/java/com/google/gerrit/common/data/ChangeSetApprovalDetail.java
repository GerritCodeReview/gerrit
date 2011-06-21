// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.Account.Id;

import java.sql.Timestamp;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class ChangeSetApprovalDetail extends ApprovalDetail<ChangeSetApproval> {
  public static final Comparator<ChangeSetApprovalDetail> SORT =
  new Comparator<ChangeSetApprovalDetail>() {
    public int compare(final ChangeSetApprovalDetail o1, final ChangeSetApprovalDetail o2) {
      int cmp;
      cmp = o2.hasNonZero - o1.hasNonZero;
      if (cmp != 0) return cmp;
      return o1.sortOrder.compareTo(o2.sortOrder);
    }
  };

  private transient int hasNonZero;
  private transient Timestamp sortOrder = EG_D;

  public ChangeSetApprovalDetail() {
  }

  public ChangeSetApprovalDetail(Id aId) {
    super(aId);
  }

  @Override
  public Map<ApprovalCategory.Id, ChangeSetApproval> getApprovalMap() {
    final HashMap<ApprovalCategory.Id, ChangeSetApproval> r;
    r = new HashMap<ApprovalCategory.Id, ChangeSetApproval>();
    for (final ChangeSetApproval ca : approvals) {
      r.put(ca.getCategoryId(), ca);
    }
    return r;
  }

  @Override
  public void sortFirst() {
    hasNonZero = 1;
    sortOrder = ChangeSetApprovalDetail.EG_0;
  }

  @Override
  public void add(final ChangeSetApproval ca) {
    approvals.add(ca);

    final Timestamp g = ca.getGranted();
    if (g != null && g.compareTo(sortOrder) < 0) {
      sortOrder = g;
    }
    if (ca.getValue() != 0) {
      hasNonZero = 1;
    }
  }
}
