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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchLineComment;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.PatchSetInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class PatchSetPublishDetail {
  protected AccountInfoCache accounts;
  protected PatchSetInfo patchSetInfo;
  protected ChangeInfo changeInfo;
  protected List<PatchLineComment> drafts;
  protected Map<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>> allowed;
  protected Map<ApprovalCategory.Id, PatchSetApproval> given;
  protected boolean isSubmitAllowed;

  public Map<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>> getAllowed() {
    return allowed;
  }

  public void setAllowed(
      Map<ApprovalCategory.Id, Set<ApprovalCategoryValue.Id>> allowed) {
    this.allowed = allowed;
  }

  public Map<ApprovalCategory.Id, PatchSetApproval> getGiven() {
    return given;
  }

  public void setGiven(Map<ApprovalCategory.Id, PatchSetApproval> given) {
    this.given = given;
  }

  public void setAccounts(AccountInfoCache accounts) {
    this.accounts = accounts;
  }

  public void setPatchSetInfo(PatchSetInfo patchSetInfo) {
    this.patchSetInfo = patchSetInfo;
  }

  public void setChangeInfo(ChangeInfo changeInfo) {
    this.changeInfo = changeInfo;
  }

  public void setDrafts(List<PatchLineComment> drafts) {
    this.drafts = drafts;
  }

  public void setSubmitAllowed(boolean allowed) {
    isSubmitAllowed = allowed;
  }

  public AccountInfoCache getAccounts() {
    return accounts;
  }

  public Change getChange() {
    return changeInfo.getChange();
  }

  public ChangeInfo getChangeInfo() {
    return changeInfo;
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

  public PatchSetApproval getChangeApproval(final ApprovalCategory.Id id) {
    return given.get(id);
  }

  public boolean isSubmitAllowed() {
    return isSubmitAllowed;
  }
}
