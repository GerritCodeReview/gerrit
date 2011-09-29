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
import com.google.gerrit.reviewdb.client.AccountGroupInclude;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class GroupMembers {
  public interface Factory {
    GroupMembers create();
  }

  private final GroupCache groupCache;
  private final GroupDetailFactory.Factory groupDetailFactory;
  private final GroupControl.Factory groupControlFactory;
  private final AccountCache accountCache;
  private final ReviewDb reviewDb;

  @Inject
  GroupMembers(final GroupCache groupCache,
      final GroupDetailFactory.Factory groupDetailFactory,
      final GroupControl.Factory groupControlFactory,
      final AccountCache accountCache,
      final ReviewDb reviewDb) {
    this.groupCache = groupCache;
    this.groupDetailFactory = groupDetailFactory;
    this.groupControlFactory = groupControlFactory;
    this.accountCache = accountCache;
    this.reviewDb = reviewDb;
  }

  public Set<Account> listAccounts(final AccountGroup.UUID groupUUID,
      final ProjectControl project, final boolean recursive)
      throws NoSuchGroupException, NoSuchProjectException, OrmException {
    return listMembers(groupUUID, project, recursive).getAllAccounts();
  }

  public GroupMembersList listMembers(final AccountGroup.UUID groupUUID,
      final ProjectControl project, final boolean recursive)
      throws NoSuchGroupException, NoSuchProjectException, OrmException {
    groupControlFactory.validateFor(groupUUID);
    return listMembers(groupUUID, project, recursive,
        new HashMap<AccountGroup.UUID, GroupMembersList>());
  }

  private GroupMembersList listMembers(final AccountGroup.UUID groupUUID,
      final ProjectControl project, final boolean recursive,
      final Map<AccountGroup.UUID, GroupMembersList> seen)
      throws NoSuchGroupException, OrmException, NoSuchProjectException {
    if (AccountGroup.PROJECT_OWNERS.equals(groupUUID)) {
      return getProjectOwners(project, recursive, seen);
    } else if (AccountGroup.REGISTERED_USERS.equals(groupUUID)) {
      return getRegisteredUsers();
    } else {
      return getGroupMembers(groupCache.get(groupUUID), project, recursive,
          seen);
    }
  }

  private GroupMembersList getProjectOwners(final ProjectControl project,
      final boolean recursive,
      final Map<AccountGroup.UUID, GroupMembersList> seen)
      throws NoSuchProjectException, NoSuchGroupException, OrmException {
    final SortedSet<GroupMembersList> projectOwnerGroupMembers =
        new TreeSet<GroupMembersList>(new GroupMembersListComparator());
    final AccountGroup projectOwnersGroup =
        groupCache.get(AccountGroup.PROJECT_OWNERS);
    final GroupMembersList groupMembers =
        new GroupMembersList(projectOwnersGroup, new TreeSet<Account>(),
            projectOwnerGroupMembers);
    seen.put(AccountGroup.PROJECT_OWNERS, groupMembers);
    final GroupControl groupControl =
        groupControlFactory.controlFor(projectOwnersGroup);
    if (groupControl.isVisible()) {
      if (project == null) {
        groupMembers.setResolved(false);
        return groupMembers;
      }
      final Set<AccountGroup.UUID> ownerGroups =
          project.getProjectState().getOwners();
      for (final AccountGroup.UUID ownerGroup : ownerGroups) {
        if (recursive) {
          if (seen.keySet().contains(ownerGroup)) {
            projectOwnerGroupMembers.add(seen.get(ownerGroup));
          } else {
            projectOwnerGroupMembers.add(listMembers(ownerGroup, project,
                recursive, seen));
          }
        } else {
          projectOwnerGroupMembers.add(new GroupMembersList(groupCache
              .get(ownerGroup), new TreeSet<Account>(),
              new TreeSet<GroupMembersList>()));
        }
      }
    } else {
      groupMembers.setGroupVisible(false);
    }
    return groupMembers;
  }

  private GroupMembersList getGroupMembers(final AccountGroup group,
      final ProjectControl project, final boolean recursive,
      final Map<AccountGroup.UUID, GroupMembersList> seen)
      throws NoSuchGroupException, OrmException, NoSuchProjectException {
    final SortedSet<Account> accounts =
        new TreeSet<Account>(new AccountComparator());
    final SortedSet<GroupMembersList> includedGroupMembers =
        new TreeSet<GroupMembersList>(new GroupMembersListComparator());
    final GroupMembersList groupMembers =
        new GroupMembersList(group, accounts, includedGroupMembers);
    seen.put(group.getGroupUUID(), groupMembers);
    final GroupControl groupControl = groupControlFactory.controlFor(group);

    if (groupControl.isVisible()) {
      if (AccountGroup.Type.SYSTEM.equals(groupControl.getAccountGroup().getType())) {
        groupMembers.setResolved(false);
        return groupMembers;
      }

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
              includedGroupMembers.add(listMembers(
                  includedGroup.getGroupUUID(), project, recursive, seen));
            }
          } else {
            includedGroupMembers.add(new GroupMembersList(includedGroup,
                new TreeSet<Account>(), new TreeSet<GroupMembersList>()));
          }
        }
      }
    } else {
      groupMembers.setGroupVisible(false);
    }

    return groupMembers;
  }

  private GroupMembersList getRegisteredUsers() throws OrmException,
      NoSuchGroupException {
    final GroupControl groupControl =
        groupControlFactory.controlFor(AccountGroup.REGISTERED_USERS);
    final AccountGroup registeredUsersGroup = groupControl.getAccountGroup();
    final TreeSet<Account> allAccounts =
        new TreeSet<Account>(new AccountComparator());
    final GroupMembersList groupMembers =
        new GroupMembersList(registeredUsersGroup, allAccounts,
            new TreeSet<GroupMembersList>());
    if (groupControl.isOwner() || registeredUsersGroup.isVisibleToAll()) {
      allAccounts.addAll(reviewDb.accounts().all().toList());
    } else {
      groupMembers.setGroupVisible(false);
    }
    return groupMembers;
  }
}
