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

package com.google.gerrit.server.account;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.group.GroupInfo;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

import java.util.List;
import java.util.Set;

public class GetGroups implements RestReadView<AccountResource> {
  @Option(name = "--owned", usage = "to list only groups that are owned by the user")
  private boolean owned;

  private Set<AccountGroup.UUID> groupsToInspect = Sets.newHashSet();

  @Option(name = "--group", usage = "group to inspect")
  void addGroup(final AccountGroup.UUID id) {
    groupsToInspect.add(id);
  }

  private final GroupControl.Factory groupControlFactory;
  private final GroupControl.GenericFactory genericGroupControlFactory;
  private final GroupCache groupCache;

  @Inject
  GetGroups(GroupControl.Factory groupControlFactory,
      GroupControl.GenericFactory genericGroupControlFactory,
      GroupCache groupCache) {
    this.groupControlFactory = groupControlFactory;
    this.genericGroupControlFactory = genericGroupControlFactory;
    this.groupCache = groupCache;
  }

  @Override
  public List<GroupInfo> apply(AccountResource resource) {
    IdentifiedUser user = resource.getUser();
    if (owned) {
      return getGroupsOwnedBy(user);
    } else {
      return getGroupsOf(user);
    }
  }

  private List<GroupInfo> getGroupsOf(IdentifiedUser user) {
    Account.Id userId = user.getAccountId();
    List<GroupInfo> groups = Lists.newArrayList();

    for (AccountGroup.UUID uuid : user.getEffectiveGroups().getKnownGroups()) {
      GroupControl ctl;
      try {
        ctl = groupControlFactory.controlFor(uuid);
      } catch (NoSuchGroupException e) {
        continue;
      }
      if (ctl.isVisible() && ctl.canSeeMember(userId)
          && (groupsToInspect.isEmpty() || groupsToInspect.contains(uuid))) {
        groups.add(new GroupInfo(ctl.getGroup()));
      }
    }
    return groups;
  }

  private List<GroupInfo> getGroupsOwnedBy(IdentifiedUser user) {
    Account.Id userId = user.getAccountId();
    List<GroupInfo> groups = Lists.newArrayList();

    for (AccountGroup g : groupCache.all()) {
      GroupControl ctl = groupControlFactory.controlFor(g);
      if (ctl.isVisible() && ctl.canSeeMember(userId)
          && (groupsToInspect.isEmpty() || groupsToInspect.contains(g.getGroupUUID()))) {
        try {
          if (genericGroupControlFactory.controlFor(user, g.getGroupUUID()).isOwner()) {
            groups.add(new GroupInfo(ctl.getGroup()));
          }
        } catch (NoSuchGroupException e) {
          continue;
        }
      }
    }
    return groups;
  }
}
