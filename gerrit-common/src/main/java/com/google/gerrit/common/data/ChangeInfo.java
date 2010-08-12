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

public class ChangeInfo {
  protected Change change;
  protected ProjectInfo project;
  protected boolean starred;

  protected ChangeInfo() {
  }

  public ChangeInfo(final Change c, final ProjectInfo projectInfo) {
    change = c;
    project = projectInfo;
  }

  public Change getChange() {
    return change;
  }

  public Change.Id getId() {
    return change.getId();
  }

  public Change.Key getKey() {
    return change.getKey();
  }

  public Account.Id getOwner() {
    return change.getOwner();
  }

  public String getSubject() {
    return change.getSubject();
  }

  public Change.Status getStatus() {
    return change.getStatus();
  }

  public ProjectInfo getProject() {
    return project;
  }

  public String getBranch() {
    return change.getDest().getShortName();
  }

  public String getTopic() {
    return change.getTopic();
  }

  public boolean isStarred() {
    return starred;
  }

  public void setStarred(final boolean s) {
    starred = s;
  }

  public java.sql.Timestamp getLastUpdatedOn() {
    return change.getLastUpdatedOn();
  }

  public String getSortKey() {
    return change.getSortKey();
  }
}
