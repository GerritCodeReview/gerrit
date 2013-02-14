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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSetInfo;

import java.util.List;

public class PatchSetPublishDetail {
  protected AccountInfoCache accounts;
  protected PatchSetInfo patchSetInfo;
  protected Change change;
  protected List<PatchLineComment> drafts;
  protected List<SubmitRecord> submitRecords;
  protected SubmitTypeRecord submitTypeRecord;
  protected boolean canSubmit;

  public void setSubmitTypeRecord(SubmitTypeRecord submitTypeRecord) {
    this.submitTypeRecord = submitTypeRecord;
  }

  public SubmitTypeRecord getSubmitTypeRecord() {
    return submitTypeRecord;
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

  public boolean canSubmit() {
    return canSubmit;
  }
}
