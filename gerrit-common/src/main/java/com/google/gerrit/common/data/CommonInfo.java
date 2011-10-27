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

import com.google.gerrit.reviewdb.AbstractEntity;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Topic;

import java.sql.Timestamp;

public abstract class CommonInfo {
  protected Account.Id owner;
  protected String subject;
  protected AbstractEntity.Status status;
  protected ProjectInfo project;
  protected String branch;
  protected String topic;
  protected Topic.Id topicId;
  protected boolean starred;
  protected Timestamp lastUpdatedOn;
  protected String sortKey;
  protected boolean latest;

  protected CommonInfo() {
  }

  public Account.Id getOwner() {
    return owner;
  }

  public String getSubject() {
    return subject;
  }

  public AbstractEntity.Status getStatus() {
    return status;
  }

  public ProjectInfo getProject() {
    return project;
  }

  public String getBranch() {
    return branch;
  }

  public String getTopic() {
    return topic;
  }

  public Topic.Id getTopicId() {
    return topicId;
  }

  public boolean isStarred() {
    return starred;
  }

  public void setStarred(final boolean s) {
    starred = s;
  }

  public boolean isLatest() {
    return latest;
  }

  public java.sql.Timestamp getLastUpdatedOn() {
    return lastUpdatedOn;
  }

  public String getSortKey() {
    return sortKey;
  }
}
