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
public class ChangeDetail extends CommonDetail {
  protected Change change;
  protected List<PatchSet> patchSets;
  protected boolean canSubmit;
  protected List<PatchSetApprovalDetail> approvals;
  protected List<ChangeMessage> messages;
  protected PatchSet.Id currentPatchSetId;
  protected PatchSetDetail currentDetail;
  protected int topicId;

  public ChangeDetail() {
  }

  public Change getChange() {
    return change;
  }

  public void setChange(final Change change) {
    this.change = change;
    this.currentPatchSetId = change.currentPatchSetId();
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

  public List<PatchSetApprovalDetail> getApprovals() {
    return approvals;
  }

  public void setApprovals(Collection<PatchSetApprovalDetail> list) {
    approvals = new ArrayList<PatchSetApprovalDetail>(list);
    Collections.sort(approvals, PatchSetApprovalDetail.SORT);
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

  public String getDescription() {
    return currentDetail != null ? currentDetail.getInfo().getMessage() : "";
  }

  public int getTopicId() {
    return topicId;
  }

  public void setTopicId(int tid) {
    topicId = tid;
  }
}
