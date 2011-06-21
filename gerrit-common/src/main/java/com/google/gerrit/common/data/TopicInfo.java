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

import com.google.gerrit.reviewdb.AbstractEntity.Status;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Topic;

import java.sql.Timestamp;

public class TopicInfo {
  protected Topic.Id id;
  protected Topic.Key key;
  protected Account.Id owner;
  protected String subject;
  protected Status status;
  protected ProjectInfo project;
  protected String branch;
  protected String topic;
  protected boolean starred;
  protected Timestamp lastUpdatedOn;
  protected String sortKey;

  protected TopicInfo() {
  }

  public TopicInfo(final Topic t) {
    id = t.getId();
    key = t.getKey();
    owner = t.getOwner();
    subject = t.getSubject();
    status = t.getStatus();
    project = new ProjectInfo(t.getProject());
    branch = t.getDest().getShortName();
    topic = t.getTopic();
    lastUpdatedOn = t.getLastUpdatedOn();
    sortKey = t.getSortKey();
  }

  public Topic.Id getId() {
    return id;
  }

  public Topic.Key getKey() {
    return key;
  }

  public Account.Id getOwner() {
    return owner;
  }

  public String getSubject() {
    return subject;
  }

  public Status getStatus() {
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

  public boolean isStarred() {
    return starred;
  }

  public void setStarred(final boolean s) {
    starred = s;
  }

  public java.sql.Timestamp getLastUpdatedOn() {
    return lastUpdatedOn;
  }

  public String getSortKey() {
    return sortKey;
  }
}
