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
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupInclude;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PerformGroupMembers {
  public interface Factory {
    PerformGroupMembers create();
  }

  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final AccountCache accountCache;
  private final ProjectControl.GenericFactory projectControl;
  private final IdentifiedUser currentUser;

  private Project.NameKey project;

  @Inject
  PerformGroupMembers(final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory,
      final AccountCache accountCache,
      final ProjectControl.GenericFactory projectControl,
      final IdentifiedUser currentUser) {
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.accountCache = accountCache;
    this.projectControl = projectControl;
    this.currentUser = currentUser;
  }

  public void setProject(final Project.NameKey project) {
    this.project = project;
  }

  public Set<Account> listAccounts(final AccountGroup.UUID groupUUID)
      throws NoSuchGroupException, NoSuchProjectException, OrmException {
    return listAccounts(groupUUID, new HashSet<AccountGroup.UUID>());
  }

  private Set<Account> listAccounts(final AccountGroup.UUID groupUUID,
      final Set<AccountGroup.UUID> seen)
      throws NoSuchGroupException, OrmException, NoSuchProjectException {
  if (AccountGroup.PROJECT_OWNERS.equals(groupUUID)) {
      return getProjectOwners(seen);
    } else {
      return getGroupMembers(groupCache.get(groupUUID), seen);
    }
  }

  private Set<Account> getProjectOwners(final Set<AccountGroup.UUID> seen)
      throws NoSuchProjectException, NoSuchGroupException, OrmException {
    seen.add(AccountGroup.PROJECT_OWNERS);
    if (project == null) {
      return Collections.emptySet();
    }

    final Set<AccountGroup.UUID> ownerGroups =
        projectControl.controlFor(project, currentUser).getProjectState()
            .getOwners();

    final HashSet<Account> projectOwners = new HashSet<Account>();
    for (final AccountGroup.UUID ownerGroup : ownerGroups) {
      if (!seen.contains(ownerGroup)) {
        projectOwners.addAll(listAccounts(ownerGroup, seen));
      }
    }
    return projectOwners;
  }

  private Set<Account> getGroupMembers(final AccountGroup group,
      final Set<AccountGroup.UUID> seen) throws NoSuchGroupException,
      OrmException, NoSuchProjectException {
    seen.add(group.getGroupUUID());
    final GroupDetail groupDetail =
        groupDetailFactory.create(group.getId()).call();

    final Set<Account> members = new HashSet<Account>();
    if (groupDetail.members != null) {
      for (final AccountGroupMember member : groupDetail.members) {
        members.add(accountCache.get(member.getAccountId()).getAccount());
      }
    }
    if (groupDetail.includes != null) {
      for (final AccountGroupInclude groupInclude : groupDetail.includes) {
        final AccountGroup includedGroup =
            groupCache.get(groupInclude.getIncludeId());
        if (!seen.contains(includedGroup.getGroupUUID())) {
          members.addAll(listAccounts(includedGroup.getGroupUUID(), seen));
        }
      }
    }
    return members;
  }
}
