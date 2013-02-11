// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.group;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.reviewdb.client.AccountGroup;

public class GroupOptionsInfo {
  public Boolean visibleToAll;

  public GroupOptionsInfo(GroupDescription.Basic group) {
    AccountGroup ag = GroupDescriptions.toAccountGroup(group);
    visibleToAll = ag != null && ag.isVisibleToAll() ? true : null;
  }

  public GroupOptionsInfo(AccountGroup group) {
    visibleToAll = group.isVisibleToAll() ? true : null;
  }
}
