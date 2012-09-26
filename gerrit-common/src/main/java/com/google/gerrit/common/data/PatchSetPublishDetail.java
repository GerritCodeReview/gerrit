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

import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PatchSetPublishDetail {
  protected AccountInfoCache accounts;
  protected PatchSetInfo patchSetInfo;
  protected Change change;
  protected List<PatchLineComment> drafts;
  protected List<PermissionRange> labels;
  protected List<ApprovalDetail> approvals;
  protected List<SubmitRecord> submitRecords;
  protected SubmitTypeRecord submitTypeRecord;
  protected List<PatchSetApproval> given;
  protected boolean canSubmit;

  public List<PermissionRange> getLabels() {
    return labels;
  }

  public void setLabels(List<PermissionRange> labels) {
    this.labels = labels;
  }

  public List<ApprovalDetail> getApprovals() {
    return approvals;
  }

  public void setApprovals(Collection<ApprovalDetail> list) {
    approvals = new ArrayList<ApprovalDetail>(list);
    Collections.sort(approvals, ApprovalDetail.SORT);
  }

  public void setSubmitRecords(List<SubmitRecord> all) {
    submitRecords = all;
  }

  public List<SubmitRecord> getSubmitRecords() {
    return submitRecords;
  }

  public void setSubmitTypeRecord(SubmitTypeRecord submitTypeRecord) {
    this.submitTypeRecord = submitTypeRecord;
  }

  public SubmitTypeRecord getSubmitTypeRecord() {
    return submitTypeRecord;
  }

  public List<PatchSetApproval> getGiven() {
    return given;
  }

  public void setGiven(List<PatchSetApproval> given) {
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

  public void setCanSubmit(boolean allowed) {
    canSubmit = allowed;
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

  public PermissionRange getRange(final String permissionName) {
    for (PermissionRange s : labels) {
      if (s.getName().equals(permissionName)) {
        return s;
      }
    }
    return null;
  }

  public PatchSetApproval getChangeApproval(ApprovalCategory.Id id) {
    for (PatchSetApproval a : given) {
      if (a.getCategoryId().equals(id)) {
        return a;
      }
    }
    return null;
  }

  public boolean canSubmit() {
    return canSubmit;
  }
}
