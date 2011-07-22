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

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Detail necessary to display a change. */
public class ChangeDetail {
  protected AccountInfoCache accounts;
  protected boolean allowsAnonymous;
  protected boolean canAbandon;
  protected boolean canRestore;
  protected boolean canRevert;
  protected Change change;
  protected boolean starred;
  protected List<ChangeInfo> dependsOn;
  protected List<ChangeInfo> neededBy;
  protected List<PatchSet> patchSets;
  protected List<ApprovalDetail> approvals;
  protected List<SubmitRecord> submitRecords;
  protected boolean canSubmit;
  protected List<ChangeMessage> messages;
  protected PatchSet.Id currentPatchSetId;
  protected PatchSetDetail currentDetail;

  public ChangeDetail() {
  }

  public AccountInfoCache getAccounts() {
    return accounts;
  }

  public void setAccounts(AccountInfoCache aic) {
    accounts = aic;
  }

  public boolean isAllowsAnonymous() {
    return allowsAnonymous;
  }

  public void setAllowsAnonymous(final boolean anon) {
    allowsAnonymous = anon;
  }

  public boolean canAbandon() {
    return canAbandon;
  }

  public void setCanAbandon(final boolean a) {
    canAbandon = a;
  }

  public boolean canRestore() {
    return canRestore;
  }

  public void setCanRestore(final boolean a) {
    canRestore = a;
  }

  public boolean canRevert() {
    return canRevert;
  }

  public void setCanRevert(boolean a) {
      canRevert = a;
  }

  public boolean canSubmit() {
    return canSubmit;
  }

  public void setCanSubmit(boolean a) {
    canSubmit = a;
  }

  public Change getChange() {
    return change;
  }

  public void setChange(final Change change) {
    this.change = change;
    this.currentPatchSetId = change.currentPatchSetId();
  }

  public boolean isStarred() {
    return starred;
  }

  public void setStarred(final boolean s) {
    starred = s;
  }

  public List<ChangeInfo> getDependsOn() {
    return dependsOn;
  }

  public void setDependsOn(List<ChangeInfo> d) {
    dependsOn = d;
  }

  public List<ChangeInfo> getNeededBy() {
    return neededBy;
  }

  public void setNeededBy(List<ChangeInfo> d) {
    neededBy = d;
  }

  public List<ChangeMessage> getMessages() {
    return messages;
  }

  public void setMessages(List<ChangeMessage> m) {
    messages = m;
  }

  public List<PatchSet> getPatchSets() {
    return patchSets;
  }

  public void setPatchSets(List<PatchSet> s) {
    patchSets = s;
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

  public boolean isCurrentPatchSet(final PatchSetDetail detail) {
    return currentPatchSetId != null
        && detail.getPatchSet().getId().equals(currentPatchSetId);
  }

  public PatchSet getCurrentPatchSet() {
    if (currentPatchSetId != null) {
      // We search through the list backwards because its *very* likely
      // that the current patch set is also the last patch set.
      //
      for (int i = patchSets.size() - 1; i >= 0; i--) {
        final PatchSet ps = patchSets.get(i);
        if (ps.getId().equals(currentPatchSetId)) {
          return ps;
        }
      }
    }
    return null;
  }

  public PatchSetDetail getCurrentPatchSetDetail() {
    return currentDetail;
  }

  public void setCurrentPatchSetDetail(PatchSetDetail d) {
    currentDetail = d;
  }

  public void setCurrentPatchSetId(final PatchSet.Id id) {
    currentPatchSetId = id;
  }

  public String getDescription() {
    return currentDetail != null ? currentDetail.getInfo().getMessage() : "";
  }
}
