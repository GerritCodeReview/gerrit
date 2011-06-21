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

import java.util.List;
import java.util.Set;

/** Common details necessary to display a topic or change. */
public abstract class CommonDetail {
  protected AccountInfoCache accounts;
  protected boolean allowsAnonymous;
  protected boolean canAbandon;
  protected boolean canRestore;
  protected boolean canRevert;
  protected boolean starred;
  protected List<ChangeInfo> dependsOn;
  protected List<ChangeInfo> neededBy;
  protected Set<ApprovalCategory.Id> missingApprovals;
  protected boolean canSubmit;
  protected List<SubmitRecord> submitRecords;

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

  public Set<ApprovalCategory.Id> getMissingApprovals() {
    return missingApprovals;
  }

  public void setMissingApprovals(Set<ApprovalCategory.Id> a) {
    missingApprovals = a;
  }


  public void setSubmitRecords(List<SubmitRecord> all) {
    submitRecords = all;
  }

  public List<SubmitRecord> getSubmitRecords() {
    return submitRecords;
  }
}
