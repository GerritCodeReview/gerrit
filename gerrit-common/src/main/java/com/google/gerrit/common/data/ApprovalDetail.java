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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.SetApproval;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ApprovalDetail<T extends SetApproval<?>> {
  static final Timestamp EG_0 = new Timestamp(0);
  static final Timestamp EG_D = new Timestamp(Long.MAX_VALUE);

  protected Account.Id account;
  protected List<T> approvals;
  protected boolean canRemove;

  private transient Set<String> approved;
  private transient Set<String> rejected;

  protected ApprovalDetail() {
  }

  protected ApprovalDetail(final Account.Id id) {
    account = id;
    approvals = new ArrayList<T>();
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

  public List<T> getPatchSetApprovals() {
    return approvals;
  }

  public List<T> getSetApprovals() {
    return approvals;
  }

  public T getPatchSetApproval(ApprovalCategory.Id category) {
    for (T psa : approvals) {
      if (psa.getCategoryId().equals(category)) {
        return psa;
      }
    }
    return null;
  }

  public abstract Map<ApprovalCategory.Id, T> getApprovalMap();

  public abstract void sortFirst();

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

  public boolean isApproved(String label) {
    return approved != null && approved.contains(label);
  }

  public boolean isRejected(String label) {
    return rejected != null && rejected.contains(label);
  }

  public abstract void add(final T ca);
}
