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

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetInfo;

import java.util.List;

public class ChangeSetDetail {
  protected ChangeSet changeSet;
  protected ChangeSetInfo info;
  protected List<Change> changes;

  public ChangeSetDetail() {
  }

  public ChangeSet getChangeSet() {
    return changeSet;
  }

  public void setChangeSet(final ChangeSet cs) {
    changeSet = cs;
  }

  public ChangeSetInfo getInfo() {
    return info;
  }

  public void setInfo(final ChangeSetInfo i) {
    info = i;
  }

  public List<Change> getChanges() {
    return changes;
  }

  public void setChanges(final List<Change> c) {
    changes = c;
  }
}
