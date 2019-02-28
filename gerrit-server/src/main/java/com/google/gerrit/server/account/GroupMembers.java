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

package com.google.gerrit.server.account;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.Sets;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.InternalGroupDescription;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class GroupMembers {
  public interface Factory {
    GroupMembers create(CurrentUser currentUser);
  }

  private final GroupCache groupCache;
  private final GroupControl.Factory groupControlFactory;
  private final AccountCache accountCache;
  private final ProjectControl.GenericFactory projectControl;
  private final CurrentUser currentUser;

  @Inject
  GroupMembers(
      GroupCache groupCache,
      GroupControl.Factory groupControlFactory,
      AccountCache accountCache,
      ProjectControl.GenericFactory projectControl,
      @Assisted CurrentUser currentUser) {
    this.groupCache = groupCache;
    this.groupControlFactory = groupControlFactory;
    this.accountCache = accountCache;
    this.projectControl = projectControl;
    this.currentUser = currentUser;
  }

  public Set<Account> listAccounts(AccountGroup.UUID groupUUID, Project.NameKey project)
      throws NoSuchGroupException, NoSuchProjectException, OrmException, IOException {
    return listAccounts(groupUUID, project, new HashSet<AccountGroup.UUID>());
  }

  private Set<Account> listAccounts(
      final AccountGroup.UUID groupUUID,
      final Project.NameKey project,
      final Set<AccountGroup.UUID> seen)
      throws NoSuchGroupException, OrmException, NoSuchProjectException, IOException {
    if (SystemGroupBackend.PROJECT_OWNERS.equals(groupUUID)) {
      return getProjectOwners(project, seen);
    }
    Optional<InternalGroup> group = groupCache.get(groupUUID);
    if (group.isPresent()) {
      return getGroupMembers(group.get(), project, seen);
    }
    return Collections.emptySet();
  }

  private Set<Account> getProjectOwners(final Project.NameKey project, Set<AccountGroup.UUID> seen)
      throws NoSuchProjectException, NoSuchGroupException, OrmException, IOException {
    seen.add(SystemGroupBackend.PROJECT_OWNERS);
    if (project == null) {
      return Collections.emptySet();
    }

    final Iterable<AccountGroup.UUID> ownerGroups =
        projectControl.controlFor(project, currentUser).getProjectState().getAllOwners();

    final HashSet<Account> projectOwners = new HashSet<>();
    for (AccountGroup.UUID ownerGroup : ownerGroups) {
      if (!seen.contains(ownerGroup)) {
        projectOwners.addAll(listAccounts(ownerGroup, project, seen));
      }
    }
    return projectOwners;
  }

  private Set<Account> getGroupMembers(
      InternalGroup group, Project.NameKey project, Set<AccountGroup.UUID> seen)
      throws NoSuchGroupException, OrmException, NoSuchProjectException, IOException {
    seen.add(group.getGroupUUID());
    GroupControl groupControl = groupControlFactory.controlFor(new InternalGroupDescription(group));

    Set<Account> directMembers =
        group.getMembers().stream()
            .filter(groupControl::canSeeMember)
            .map(accountCache::get)
            .map(AccountState::getAccount)
            .collect(toImmutableSet());

    Set<Account> indirectMembers = new HashSet<>();
    if (groupControl.canSeeGroup()) {
      for (AccountGroup.UUID subgroupUuid : group.getSubgroups()) {
        if (!seen.contains(subgroupUuid)) {
          indirectMembers.addAll(listAccounts(subgroupUuid, project, seen));
        }
      }
    }

    return Sets.union(directMembers, indirectMembers);
  }
}
