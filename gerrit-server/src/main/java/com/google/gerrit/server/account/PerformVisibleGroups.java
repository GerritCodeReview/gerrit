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

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.GroupList;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class PerformVisibleGroups {

  public interface Factory {
    PerformVisibleGroups create();
  }

  private final Provider<IdentifiedUser> identifiedUser;
  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;
  private final GroupDetailFactory.Factory groupDetailFactory;

  private Set<ProjectControl> projects;
  private boolean onlyVisibleToAll;

  @Inject
  PerformVisibleGroups(final Provider<IdentifiedUser> currentUser,
      final GroupCache groupCache,
      final GroupControl.Factory groupControlFactory,
      final GroupDetailFactory.Factory groupDetailFactory) {
    this.identifiedUser = currentUser;
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.groupDetailFactory = groupDetailFactory;
  }

  public void setProjects(final Set<ProjectControl> projects) {
    this.projects = projects;
  }

  public void setOnlyVisibleToAll(final boolean onlyVisibleToAll) {
    this.onlyVisibleToAll = onlyVisibleToAll;
  }

  public GroupList getVisibleGroups() throws OrmException, NoSuchGroupException {
    final Iterable<AccountGroup> groups;
    if (projects != null && !projects.isEmpty()) {
      groups = getGroupsForProjects();
    } else {
      groups = groupCache.all();
    }
    return createGroupList(filterGroups(groups));
  }

  private Set<AccountGroup> getGroupsForProjects() throws NoSuchGroupException {
    final SortedSet<AccountGroup> groups =
        new TreeSet<AccountGroup>(new GroupComparator());
    for (final ProjectControl projectControl : projects) {
      final Set<GroupReference> groupsRefs = projectControl.getAllGroups();
      for (final GroupReference groupRef : groupsRefs) {
        final AccountGroup group = groupCache.get(groupRef.getUUID());
        if (group == null) {
          throw new NoSuchGroupException(groupRef.getUUID());
        }
        groups.add(group);
      }
    }
    return groups;
  }

  private List<AccountGroup> filterGroups(final Iterable<AccountGroup> groups) {
    final List<AccountGroup> filteredGroups = new LinkedList<AccountGroup>();
    final boolean isAdmin =
        identifiedUser.get().getCapabilities().canAdministrateServer();
    for (final AccountGroup group : groups) {
      if (!isAdmin) {
        final GroupControl c = groupControlFactory.controlFor(group);
        if (!c.isVisible()) {
          continue;
        }
      }
      if (onlyVisibleToAll && !group.isVisibleToAll()) {
        continue;
      }
      filteredGroups.add(group);
    }
    return filteredGroups;
  }

  private GroupList createGroupList(final List<AccountGroup> groups)
      throws OrmException, NoSuchGroupException {
    final List<GroupDetail> groupDetailList = new ArrayList<GroupDetail>();
    for (final AccountGroup group : groups) {
      groupDetailList.add(groupDetailFactory.create(group.getId()).call());
    }
    final GroupList groupList = new GroupList();
    groupList.setGroups(groupDetailList);
    groupList.setCanCreateGroup(identifiedUser.get().getCapabilities()
        .canCreateGroup());
    return groupList;
  }
}
