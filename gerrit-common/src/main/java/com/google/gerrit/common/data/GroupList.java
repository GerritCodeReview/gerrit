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

import com.google.gerrit.reviewdb.AccountGroup;

import java.util.List;

public class GroupList {
  protected List<AccountGroup> groups;
  protected boolean canCreateGroup;

  public List<AccountGroup> getGroups() {
    return groups;
  }

  public void setGroups(List<AccountGroup> groups) {
    this.groups = groups;
  }

  public boolean isCanCreateGroup() {
    return canCreateGroup;
  }

  public void setCanCreateGroup(boolean set) {
    canCreateGroup = set;
  }
}
