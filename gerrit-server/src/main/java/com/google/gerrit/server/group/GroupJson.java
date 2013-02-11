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

import static com.google.gerrit.common.groups.ListGroupsOption.INCLUDES;
import static com.google.gerrit.common.groups.ListGroupsOption.MEMBERS;
import static com.google.gerrit.common.groups.ListGroupsOption.OWNER;

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.common.groups.ListGroupsOption;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.group.MembersCollection.MemberInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class GroupJson {
  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;
  private final Provider<ListMembers> listMembers;
  private final Provider<ListIncludedGroups> listIncludes;
  private EnumSet<ListGroupsOption> options;

  @Inject
  GroupJson(GroupCache groupCache, GroupControl.Factory groupControlFactory,
      Provider<ListMembers> listMembers,
      Provider<ListIncludedGroups> listIncludes) {
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.listMembers = listMembers;
    this.listIncludes = listIncludes;

    options = EnumSet.noneOf(ListGroupsOption.class);
  }

  public GroupJson addOption(ListGroupsOption o) {
    options.add(o);
    return this;
  }

  public GroupJson addOptions(Collection<ListGroupsOption> o) {
    options.addAll(o);
    return this;
  }

  public GroupInfo format(GroupDescription.Basic group)
      throws ResourceNotFoundException, OrmException {
    GroupInfo info = new GroupInfo();
    info.id = Url.encode(group.getGroupUUID().get());
    info.name = Strings.emptyToNull(group.getName());
    info.url = Strings.emptyToNull(group.getUrl());
    info.options = new GroupOptionsInfo(group);

    AccountGroup g = GroupDescriptions.toAccountGroup(group);
    if (g != null) {
      info.description = Strings.emptyToNull(g.getDescription());
      info.groupId = g.getId().get();
      if (options.contains(OWNER) && g.getOwnerGroupUUID() != null) {
        info.ownerId = Url.encode(g.getOwnerGroupUUID().get());
        AccountGroup o = groupCache.get(g.getOwnerGroupUUID());
        if (o != null) {
          info.owner = o.getName();
        }
      }

      if (options.contains(MEMBERS) || options.contains(INCLUDES)) {
        GroupResource r = new GroupResource(groupControlFactory.controlFor(group));
        if (options.contains(MEMBERS)) {
          info.members = listMembers.get().apply(r);
        }

        if (options.contains(INCLUDES)) {
          info.includes = listIncludes.get().apply(r);
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
    public List<MemberInfo> members;
    public List<GroupInfo> includes;
  }
}
