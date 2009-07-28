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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.data.AccountInfoCache;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.PatchLineComment;
import com.google.gerrit.client.reviewdb.PatchSetInfo;

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

  public Map<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>> getAllowed() {
    return allowed;
  }

  public void setAllowed(
      Map<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>> allowed) {
    this.allowed = allowed;
  }

  public Map<ApprovalCategory.Id, ChangeApproval> getGiven() {
    return given;
  }

  public void setGiven(Map<ApprovalCategory.Id, ChangeApproval> given) {
    this.given = given;
  }

  public void setAccounts(AccountInfoCache accounts) {
    this.accounts = accounts;
  }

  public void setPatchSetInfo(PatchSetInfo patchSetInfo) {
    this.patchSetInfo = patchSetInfo;
  }
  
  public void setChange(Change change) {
    this.change = change;
  }
  
  public void setDrafts(List<PatchLineComment> drafts) {
    this.drafts = drafts;
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
