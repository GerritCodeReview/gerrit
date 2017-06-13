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

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GroupMembers {
  public interface Factory {
    GroupMembers create(CurrentUser currentUser);
  }

  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final AccountCache accountCache;
  private final ProjectControl.GenericFactory projectControl;
  private final CurrentUser currentUser;

  @Inject
  GroupMembers(
      final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory,
      final AccountCache accountCache,
      final ProjectControl.GenericFactory projectControl,
      @Assisted final CurrentUser currentUser) {
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
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
    AccountGroup group = groupCache.get(groupUUID);
    if (group != null) {
      return getGroupMembers(group, project, seen);
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
      final AccountGroup group, Project.NameKey project, Set<AccountGroup.UUID> seen)
      throws NoSuchGroupException, OrmException, NoSuchProjectException, IOException {
    seen.add(group.getGroupUUID());
    final GroupDetail groupDetail = groupDetailFactory.create(group.getId()).call();

    final Set<Account> members = new HashSet<>();
    if (groupDetail.members != null) {
      for (AccountGroupMember member : groupDetail.members) {
        members.add(accountCache.get(member.getAccountId()).getAccount());
      }
    }
    if (groupDetail.includes != null) {
      for (AccountGroupById groupInclude : groupDetail.includes) {
        final AccountGroup includedGroup = groupCache.get(groupInclude.getIncludeUUID());
        if (includedGroup != null && !seen.contains(includedGroup.getGroupUUID())) {
          members.addAll(listAccounts(includedGroup.getGroupUUID(), project, seen));
        }
      }
    }
    return members;
  }
}
