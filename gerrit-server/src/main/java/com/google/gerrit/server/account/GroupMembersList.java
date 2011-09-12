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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class represents an AccountGroup with its members. For included groups
 * other instances of GroupMembersList are contained, so that the structure of
 * the group is reflected. Within the group structure there can be recursions.
 */
public class GroupMembersList {

  private final AccountGroup group;
  private final SortedSet<Account> accounts;
  private final SortedSet<GroupMembersList> includedGroupMembers;
  private boolean isGroupVisible = true;
  private boolean isResolved = true;

  public GroupMembersList(final AccountGroup group,
      final SortedSet<Account> accounts,
      final SortedSet<GroupMembersList> includedGroupMembers) {
    this.group = group;
    this.accounts = accounts;
    this.includedGroupMembers = includedGroupMembers;
  }

  public AccountGroup getGroup() {
    return group;
  }

  public void setGroupVisible(boolean isGroupVisible) {
    this.isGroupVisible = isGroupVisible;
  }

  public boolean isGroupVisible() {
    return isGroupVisible;
  }

  public void setResolved(boolean isResolved) {
    this.isResolved = isResolved;
  }

  public boolean isResolved() {
    return isResolved;
  }

  public SortedSet<Account> getAllAccounts() {
    return getAllAccounts(new HashSet<AccountGroup.Id>());
  }

  private SortedSet<Account> getAllAccounts(final Set<AccountGroup.Id> seen) {
    seen.add(group.getId());
    final SortedSet<Account> allAccounts =
        new TreeSet<Account>(new AccountComparator());
    allAccounts.addAll(accounts);
    for (final GroupMembersList groupMembers : includedGroupMembers) {
      if (!seen.contains(groupMembers.getGroup().getId())) {
        allAccounts.addAll(groupMembers.getAllAccounts(seen));
      }
    }
    return allAccounts;
  }

  public SortedSet<GroupMembersList> getAllIncludedGroups() {
    return getAllIncludedGroups(new HashSet<AccountGroup.Id>());
  }

  private SortedSet<GroupMembersList> getAllIncludedGroups(
      final Set<AccountGroup.Id> seen) {
    final SortedSet<GroupMembersList> allIncludedGroups =
        new TreeSet<GroupMembersList>(new GroupMembersListComparator());
    for (final GroupMembersList groupMembers : includedGroupMembers) {
      if (!seen.contains(groupMembers.getGroup().getId())) {
        seen.add(groupMembers.getGroup().getId());
        allIncludedGroups.add(groupMembers);
        allIncludedGroups.addAll(groupMembers.getAllIncludedGroups(seen));
      }
    }
    return allIncludedGroups;
  }

  public Map<AccountGroup.UUID, GroupMembersList> getUnresolvedGroups(
      final boolean recursive) {
    if (!recursive) {
      if (isResolved()) {
        return Collections.emptyMap();
      }
      return Collections.singletonMap(getGroup().getGroupUUID(), this);
    }
    return getAllUnresolvedGroups(new HashSet<AccountGroup.Id>());
  }

  private Map<AccountGroup.UUID, GroupMembersList> getAllUnresolvedGroups(
      final Set<AccountGroup.Id> seen) {
    final Map<AccountGroup.UUID, GroupMembersList> allGroups =
        new HashMap<AccountGroup.UUID, GroupMembersList>();
    if (!isResolved) {
      allGroups.put(group.getGroupUUID(), this);
    }
    seen.add(group.getId());
    for (final GroupMembersList groupMembers : includedGroupMembers) {
      if (!seen.contains(groupMembers.getGroup().getId())) {
        allGroups.putAll(groupMembers.getAllUnresolvedGroups(seen));
      }
    }
    return allGroups;
  }

  /**
   * Checks if there are included groups that are not visible to the calling
   * user.
   *
   * @return <code>true</code> if there are groups that are not visible to the
   *         calling user, otherwise <code>false</code>
   */
  public boolean containsNonVisibleGroup() {
    return containsNonVisibleGroup(new HashSet<AccountGroup.Id>());
  }

  private boolean containsNonVisibleGroup(final Set<AccountGroup.Id> seen) {
    if (!isGroupVisible) {
      return true;
    }
    seen.add(group.getId());
    for (final GroupMembersList groupMembers : includedGroupMembers) {
      if (!seen.contains(groupMembers.getGroup().getId())) {
        if (groupMembers.containsNonVisibleGroup(seen)) {
          return true;
        }
      }
    }
    return false;
  }
}
