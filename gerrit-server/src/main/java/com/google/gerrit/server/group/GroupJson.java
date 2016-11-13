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

import static com.google.gerrit.extensions.client.ListGroupsOption.INCLUDES;
import static com.google.gerrit.extensions.client.ListGroupsOption.MEMBERS;

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.extensions.client.ListGroupsOption;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.GroupOptionsInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Collection;
import java.util.EnumSet;

public class GroupJson {
  public static GroupOptionsInfo createOptions(GroupDescription.Basic group) {
    GroupOptionsInfo options = new GroupOptionsInfo();
    AccountGroup ag = GroupDescriptions.toAccountGroup(group);
    if (ag != null && ag.isVisibleToAll()) {
      options.visibleToAll = true;
    }
    return options;
  }

  private final GroupBackend groupBackend;
  private final GroupControl.Factory groupControlFactory;
  private final Provider<ListMembers> listMembers;
  private final Provider<ListIncludedGroups> listIncludes;
  private EnumSet<ListGroupsOption> options;

  @Inject
  GroupJson(
      GroupBackend groupBackend,
      GroupControl.Factory groupControlFactory,
      Provider<ListMembers> listMembers,
      Provider<ListIncludedGroups> listIncludes) {
    this.groupBackend = groupBackend;
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

  public GroupInfo format(GroupResource rsrc) throws OrmException {
    GroupInfo info = init(rsrc.getGroup());
    initMembersAndIncludes(rsrc, info);
    return info;
  }

  public GroupInfo format(GroupDescription.Basic group) throws OrmException {
    GroupInfo info = init(group);
    if (options.contains(MEMBERS) || options.contains(INCLUDES)) {
      GroupResource rsrc = new GroupResource(groupControlFactory.controlFor(group));
      initMembersAndIncludes(rsrc, info);
    }
    return info;
  }

  private GroupInfo init(GroupDescription.Basic group) {
    GroupInfo info = new GroupInfo();
    info.id = Url.encode(group.getGroupUUID().get());
    info.name = Strings.emptyToNull(group.getName());
    info.url = Strings.emptyToNull(group.getUrl());
    info.options = createOptions(group);

    AccountGroup g = GroupDescriptions.toAccountGroup(group);
    if (g != null) {
      info.description = Strings.emptyToNull(g.getDescription());
      info.groupId = g.getId().get();
      if (g.getOwnerGroupUUID() != null) {
        info.ownerId = Url.encode(g.getOwnerGroupUUID().get());
        GroupDescription.Basic o = groupBackend.get(g.getOwnerGroupUUID());
        if (o != null) {
          info.owner = o.getName();
        }
      }
    }

    return info;
  }

  private GroupInfo initMembersAndIncludes(GroupResource rsrc, GroupInfo info) throws OrmException {
    if (rsrc.toAccountGroup() == null) {
      return info;
    }
    try {
      if (options.contains(MEMBERS)) {
        info.members = listMembers.get().apply(rsrc);
      }

      if (options.contains(INCLUDES)) {
        info.includes = listIncludes.get().apply(rsrc);
      }
      return info;
    } catch (MethodNotAllowedException e) {
      // should never happen, this exception is only thrown if we would try to
      // list members/includes of an external group, but in case of an external
      // group we return before
      throw new IllegalStateException(e);
    }
  }
}
