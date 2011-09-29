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
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class PerformGroupMembers {
  public interface Factory {
    PerformGroupMembers create();
  }

  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final GroupControl.Factory groupControlFactory;
  private final AccountCache accountCache;
  private final ProjectControl.GenericFactory projectControl;
  private final IdentifiedUser currentUser;
  private final ReviewDb reviewDb;

  private boolean recursive = true;
  private ProjectControl project;

  @Inject
  PerformGroupMembers(final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory,
      final GroupControl.Factory groupControlFactory,
      final AccountCache accountCache,
      final ProjectControl.GenericFactory projectControl,
      final IdentifiedUser currentUser,
      final ReviewDb reviewDb) {
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.groupControlFactory = groupControlFactory;
    this.accountCache = accountCache;
    this.projectControl = projectControl;
    this.currentUser = currentUser;
    this.reviewDb = reviewDb;
  }

  public void setProject(final Project.NameKey projectName)
      throws NoSuchProjectException {
    this.project = projectControl.controlFor(projectName, currentUser);
  }

  public void setProject(final ProjectControl project) {
    this.project = project;
  }

  public void setRecursive(final boolean recursive) {
    this.recursive = recursive;
  }

  public Set<Account> listAccounts(final AccountGroup.UUID groupUUID)
      throws NoSuchGroupException, NoSuchProjectException, OrmException {
    return listMembers(groupUUID).getAllAccounts();
  }

  public GroupMembers listMembers(final AccountGroup.UUID groupUUID)
      throws NoSuchGroupException, NoSuchProjectException, OrmException {
    groupControlFactory.validateFor(groupUUID);
    return listMembers(groupUUID,
        new HashMap<AccountGroup.UUID, GroupMembers>());
  }

  private GroupMembers listMembers(final AccountGroup.UUID groupUUID,
      final Map<AccountGroup.UUID, GroupMembers> seen)
      throws NoSuchGroupException, OrmException, NoSuchProjectException {
    if (AccountGroup.PROJECT_OWNERS.equals(groupUUID)) {
      return getProjectOwners(seen);
    } if (AccountGroup.REGISTERED_USERS.equals(groupUUID)) {
      return getRegisteredUsers();
    } else {
      return getGroupMembers(groupCache.get(groupUUID), seen);
    }
  }

  private GroupMembers getProjectOwners(
      final Map<AccountGroup.UUID, GroupMembers> seen)
      throws NoSuchProjectException, NoSuchGroupException, OrmException {
    final SortedSet<GroupMembers> projectOwnerGroupMembers =
        new TreeSet<GroupMembers>();
    final AccountGroup projectOwnersGroup =
        groupCache.get(AccountGroup.PROJECT_OWNERS);
    final GroupMembers groupMembers =
        new GroupMembers(projectOwnersGroup, new TreeSet<Account>(),
            projectOwnerGroupMembers);
    seen.put(AccountGroup.PROJECT_OWNERS, groupMembers);
    final GroupControl groupControl =
        groupControlFactory.controlFor(projectOwnersGroup);
    if (groupControl.isVisible()) {
      if (project == null) {
        return groupMembers;
      }

      final Set<AccountGroup.UUID> ownerGroups =
          project.getProjectState().getOwners();
      for (final AccountGroup.UUID ownerGroup : ownerGroups) {
        if (recursive) {
          if (seen.keySet().contains(ownerGroup)) {
            projectOwnerGroupMembers.add(seen.get(ownerGroup));
          } else {
            projectOwnerGroupMembers.add(listMembers(ownerGroup, seen));
          }
        } else {
          projectOwnerGroupMembers.add(new GroupMembers(groupCache
              .get(ownerGroup), new TreeSet<Account>(),
              new TreeSet<GroupMembers>()));
        }
      }
    } else {
      groupMembers.setGroupVisible(false);
    }
    return groupMembers;
  }

  private GroupMembers getGroupMembers(final AccountGroup group,
      final Map<AccountGroup.UUID, GroupMembers> seen)
      throws NoSuchGroupException, OrmException, NoSuchProjectException {
    final SortedSet<Account> accounts =
        new TreeSet<Account>(new AccountComparator());
    final SortedSet<GroupMembers> includedGroupMembers = new TreeSet<GroupMembers>();
    final GroupMembers groupMembers =
        new GroupMembers(group, accounts, includedGroupMembers);
    seen.put(group.getGroupUUID(), groupMembers);
    final GroupControl groupControl = groupControlFactory.controlFor(group);
    if (groupControl.isVisible()) {
      final GroupDetail groupDetail =
          groupDetailFactory.create(group.getId()).call();

      if (groupDetail.members != null) {
        for (final AccountGroupMember member : groupDetail.members) {
          accounts.add(accountCache.get(member.getAccountId()).getAccount());
        }
      }

      if (groupDetail.includes != null) {
        for (final AccountGroupInclude groupInclude : groupDetail.includes) {
          final AccountGroup includedGroup =
              groupCache.get(groupInclude.getIncludeId());
          if (recursive) {
            if (seen.keySet().contains(includedGroup.getGroupUUID())) {
              includedGroupMembers.add(seen.get(includedGroup.getGroupUUID()));
            } else {
              includedGroupMembers.add(listMembers(includedGroup.getGroupUUID(),
                  seen));
            }
          } else {
            includedGroupMembers.add(new GroupMembers(includedGroup,
                new TreeSet<Account>(), new TreeSet<GroupMembers>()));
          }
        }
      }
    } else {
      groupMembers.setGroupVisible(false);
    }

    return groupMembers;
  }

  private GroupMembers getRegisteredUsers() throws OrmException,
      NoSuchGroupException {
    final GroupControl groupControl =
        groupControlFactory.controlFor(AccountGroup.REGISTERED_USERS);
    final AccountGroup registeredUsersGroup = groupControl.getAccountGroup();
    final TreeSet<Account> allAccounts =
        new TreeSet<Account>(new AccountComparator());
    final GroupMembers groupMembers =
        new GroupMembers(registeredUsersGroup, allAccounts,
            new TreeSet<GroupMembers>());
    if (groupControl.isOwner() || registeredUsersGroup.isVisibleToAll()) {
      allAccounts.addAll(reviewDb.accounts().all().toList());
    } else {
      groupMembers.setGroupVisible(false);
    }
    return groupMembers;
  }
}
