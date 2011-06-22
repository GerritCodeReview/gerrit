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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;

import java.sql.Timestamp;

public class ChangeInfo {
  protected Change.Id id;
  protected Change.Key key;
  protected Account.Id owner;
  protected String subject;
  protected Change.Status status;
  protected ProjectInfo project;
  protected String branch;
  protected String topic;
  protected boolean starred;
  protected Timestamp lastUpdatedOn;
  protected String sortKey;
  protected PatchSet.Id patchSetId;
  protected boolean latest;

  protected ChangeInfo() {
  }

  public ChangeInfo(final Change c, final PatchSet.Id patchId) {
    id = c.getId();
    key = c.getKey();
    owner = c.getOwner();
    subject = c.getSubject();
    status = c.getStatus();
    project = new ProjectInfo(c.getProject());
    branch = c.getDest().getShortName();
    topic = c.getTopic();
    lastUpdatedOn = c.getLastUpdatedOn();
    sortKey = c.getSortKey();
    patchSetId = patchId;
    latest = patchSetId == null || c.currPatchSetId().equals(patchSetId);
  }

  public ChangeInfo(final Change c) {
    this(c, null);
  }

  public Change.Id getId() {
    return id;
  }

  public Change.Key getKey() {
    return key;
  }

  public Account.Id getOwner() {
    return owner;
  }

  public String getSubject() {
    return subject;
  }

  public Change.Status getStatus() {
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

  public PatchSet.Id getPatchSetId() {
    return patchSetId;
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
