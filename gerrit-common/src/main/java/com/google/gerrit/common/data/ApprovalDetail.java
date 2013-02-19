// Copyright (C) 2008 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSetApproval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApprovalDetail {
  protected Account.Id account;
  protected List<PatchSetApproval> approvals;
  protected boolean canRemove;
  private Set<String> votable;

  private transient Set<String> approved;
  private transient Set<String> rejected;
  private transient Map<String, Integer> values;

  protected ApprovalDetail() {
  }

  public ApprovalDetail(final Account.Id id) {
    account = id;
    approvals = new ArrayList<PatchSetApproval>();
  }

  public Account.Id getAccount() {
    return account;
  }

  public boolean canRemove() {
    return canRemove;
  }

  public void setCanRemove(boolean removeable) {
    canRemove = removeable;
  }

  public void approved(String label) {
    if (approved == null) {
      approved = new HashSet<String>();
    }
    approved.add(label);
  }

  public void rejected(String label) {
    if (rejected == null) {
      rejected = new HashSet<String>();
    }
    rejected.add(label);
  }

  public void votable(String label) {
    if (votable == null) {
      votable = new HashSet<String>();
    }
    votable.add(label);
  }

  public void value(String label, int value) {
    if (values == null) {
      values = new HashMap<String, Integer>();
    }
    values.put(label, value);
  }

  public boolean isApproved(String label) {
    return approved != null && approved.contains(label);
  }

  public boolean isRejected(String label) {
    return rejected != null && rejected.contains(label);
  }

  public boolean canVote(String label) {
    return votable != null && votable.contains(label);
  }

  public int getValue(String label) {
    if (values == null) {
      return 0;
    }
    Integer v = values.get(label);
    return v != null ? v : 0;
  }
}
