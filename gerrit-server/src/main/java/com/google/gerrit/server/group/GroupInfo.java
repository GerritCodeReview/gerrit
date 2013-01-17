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

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.util.Url;

public class GroupInfo {
  final String kind = "gerritcodereview#group";
  public String id;
  public String name;
  Boolean visibleToAll;

  // These fields are only supplied for internal groups.
  String description;
  Integer groupId;
  String ownerId;

  public GroupInfo(GroupDescription.Basic group) {
    id = Url.encode(group.getGroupUUID().get());
    name = Strings.emptyToNull(group.getName());
    visibleToAll = group.isVisibleToAll() ? true : null;

    if (group instanceof GroupDescription.Internal) {
      set(((GroupDescription.Internal) group).getAccountGroup());
    }
  }

  private void set(AccountGroup d) {
    description = Strings.emptyToNull(d.getDescription());
    groupId = d.getId().get();
    ownerId = d.getOwnerGroupUUID() != null
        ? Url.encode(d.getOwnerGroupUUID().get())
        : null;
  }
}