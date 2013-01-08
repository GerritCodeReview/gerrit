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
// limitations under the License

package com.google.gerrit.server.account;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class VisibleGroups {

  public interface Factory {
    VisibleGroups create();
  }

  private final Provider<IdentifiedUser> identifiedUser;
  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;

  private boolean onlyVisibleToAll;
  private AccountGroup.Type groupType;
  private String match;

  @Inject
  VisibleGroups(final Provider<IdentifiedUser> currentUser,
      final GroupCache groupCache,
      final GroupControl.Factory groupControlFactory) {
    this.identifiedUser = currentUser;
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
  }

  public void setOnlyVisibleToAll(final boolean onlyVisibleToAll) {
    this.onlyVisibleToAll = onlyVisibleToAll;
  }

  public void setGroupType(final AccountGroup.Type groupType) {
    this.groupType = groupType;
  }

  public void setMatch(final String match) {
    this.match = match;
  }

  public List<AccountGroup> get() {
    return filterGroups(groupCache.all());
  }

  public List<AccountGroup> get(final Collection<ProjectControl> projects)
      throws NoSuchGroupException {
    Map<AccountGroup.UUID, AccountGroup> groups = Maps.newHashMap();
    for (final ProjectControl projectControl : projects) {
      final Set<GroupReference> groupsRefs = projectControl.getAllGroups();
      for (final GroupReference groupRef : groupsRefs) {
        final AccountGroup group = groupCache.get(groupRef.getUUID());
        if (group == null) {
          throw new NoSuchGroupException(groupRef.getUUID());
        }
        groups.put(group.getGroupUUID(), group);
      }
    }
    return filterGroups(groups.values());
  }

  /**
   * Returns visible list of known groups for the user. Depending on the group
   * membership realms supported, this may only return a subset of the effective
   * groups.
   * @See GroupMembership#getKnownGroups()
   */
  public List<AccountGroup> get(final IdentifiedUser user) throws NoSuchGroupException {
    if (identifiedUser.get().getAccountId().equals(user.getAccountId())
        || identifiedUser.get().getCapabilities().canAdministrateServer()) {
      Set<AccountGroup.UUID> mine = user.getEffectiveGroups().getKnownGroups();
      Map<AccountGroup.UUID, AccountGroup> groups = Maps.newHashMap();
      for (final AccountGroup.UUID groupId : mine) {
        AccountGroup group = groupCache.get(groupId);
        if (group != null) {
          groups.put(groupId, group);
        }
      }
      return filterGroups(groups.values());
    } else {
      throw new NoSuchGroupException("Groups of user '" + user.getAccountId()
          + "' are not visible.");
    }
  }

  private List<AccountGroup> filterGroups(final Iterable<AccountGroup> groups) {
    final List<AccountGroup> filteredGroups = Lists.newArrayList();
    final boolean isAdmin =
        identifiedUser.get().getCapabilities().canAdministrateServer();
    for (final AccountGroup group : groups) {
      if (!Strings.isNullOrEmpty(match)) {
        if (!group.getName().toLowerCase(Locale.US)
            .contains(match.toLowerCase(Locale.US))) {
          continue;
        }
      }
      if (!isAdmin) {
        final GroupControl c = groupControlFactory.controlFor(group);
        if (!c.isVisible()) {
          continue;
        }
      }
      if ((onlyVisibleToAll && !group.isVisibleToAll())
          || (groupType != null && !groupType.equals(group.getType()))) {
        continue;
      }
      filteredGroups.add(group);
    }
    Collections.sort(filteredGroups, new GroupComparator());
    return filteredGroups;
  }
}
