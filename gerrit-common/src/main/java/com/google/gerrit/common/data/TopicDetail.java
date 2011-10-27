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

import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.reviewdb.TopicMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Detail necessary to display a topic. */
public class TopicDetail extends CommonDetail {
  protected List<ChangeSet> changeSets;
  protected List<ChangeSetApprovalDetail> approvals;
  protected List<TopicMessage> messages;
  protected ChangeSet.Id currentChangeSetId;
  protected ChangeSetDetail currentDetail;
  protected Topic topic;

  public TopicDetail() {
  }

  public Topic getTopic() {
    return topic;
  }

  public void setTopic(final Topic topic) {
    this.topic = topic;
    this.currentChangeSetId = topic.currentChangeSetId();
  }

  public List<TopicMessage> getMessages() {
    return messages;
  }

  public void setMessages(List<TopicMessage> m) {
    messages = m;
  }

  public List<ChangeSet> getChangeSets() {
    return changeSets;
  }

  public void setChangeSets(List<ChangeSet> s) {
    changeSets = s;
  }

  public List<ChangeSetApprovalDetail> getApprovals() {
    return approvals;
  }

  public void setApprovals(Collection<ChangeSetApprovalDetail> list) {
    approvals = new ArrayList<ChangeSetApprovalDetail>(list);
    Collections.sort(approvals, ChangeSetApprovalDetail.SORT);
  }

  public boolean isCurrentChangeSet(final ChangeSetDetail detail) {
    return currentChangeSetId != null
        && detail.getChangeSet().getId().equals(currentChangeSetId);
  }

  public ChangeSet getCurrentChangeSet() {
    if (currentChangeSetId != null) {
      for (int i = changeSets.size() - 1; i >= 0; i--) {
        final ChangeSet cs = changeSets.get(i);
        if (cs.getId().equals(currentChangeSetId)) {
          return cs;
        }
      }
    }
    return null;
  }

  public ChangeSetDetail getCurrentChangeSetDetail() {
    return currentDetail;
  }

  public void setCurrentChangeSetDetail(ChangeSetDetail d) {
    currentDetail = d;
  }

  public String getDescription() {
    return currentDetail != null ? currentDetail.getInfo().getMessage() : "";
  }
}
