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
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.inject.Inject;

public class GroupJson {
  private final GroupCache groupCache;

  @Inject
  GroupJson(GroupCache groupCache) {
    this.groupCache = groupCache;
  }

  public GroupInfo format(GroupDescription.Basic group) {
    GroupInfo info = new GroupInfo();
    info.id = Url.encode(group.getGroupUUID().get());
    info.name = Strings.emptyToNull(group.getName());
    info.url = Strings.emptyToNull(group.getUrl());
    info.options = new GroupOptionsInfo(group);

    AccountGroup g = GroupDescriptions.toAccountGroup(group);
    if (g != null) {
      info.description = Strings.emptyToNull(g.getDescription());
      info.groupId = g.getId().get();
      if (g.getOwnerGroupUUID() != null) {
        info.ownerId = Url.encode(g.getOwnerGroupUUID().get());
        AccountGroup o = groupCache.get(g.getOwnerGroupUUID());
        if (o != null) {
          info.owner = o.getName();
        }
      }
    }

    return info;
  }

  public static class GroupInfo {
    final String kind = "gerritcodereview#group";
    public String id;
    public String name;
    public String url;
    public GroupOptionsInfo options;

    // These fields are only supplied for internal groups.
    public String description;
    public Integer groupId;
    public String owner;
    public String ownerId;
  }
}
