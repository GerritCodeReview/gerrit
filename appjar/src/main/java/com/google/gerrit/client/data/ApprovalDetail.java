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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ChangeApproval;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApprovalDetail {
  static final Timestamp EG_0 = new Timestamp(0);
  static final Timestamp EG_D = new Timestamp(Long.MAX_VALUE);

  protected Account.Id account;
  protected List<ChangeApproval> approvals;

  transient Timestamp sortOrder = EG_D;

  protected ApprovalDetail() {
  }

  public ApprovalDetail(final Account.Id id) {
    account = id;
    approvals = new ArrayList<ChangeApproval>();
  }

  public Account.Id getAccount() {
    return account;
  }

  public Map<ApprovalCategory.Id, ChangeApproval> getApprovalMap() {
    final HashMap<ApprovalCategory.Id, ChangeApproval> r;
    r = new HashMap<ApprovalCategory.Id, ChangeApproval>();
    for (final ChangeApproval ca : approvals) {
      r.put(ca.getCategoryId(), ca);
    }
    return r;
  }

  void add(final ChangeApproval ca) {
    approvals.add(ca);

    final Timestamp g = ca.getGranted();
    if (g != null && g.compareTo(sortOrder) < 0) {
      sortOrder = g;
    }
  }
}
