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

import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class GroupMembers implements Comparable<GroupMembers> {

  private final AccountGroup group;
  private final SortedSet<Account> accounts;
  private final SortedSet<GroupMembers> includedGroupMembers;
  private boolean isGroupVisible = true;

  public GroupMembers(final AccountGroup group,
      final SortedSet<Account> accounts,
      final SortedSet<GroupMembers> includedGroupMembers) {
    this.group = group;
    this.accounts = accounts;
    this.includedGroupMembers = includedGroupMembers;
  }

  public void setGroupVisible(boolean isGroupVisible) {
    this.isGroupVisible = isGroupVisible;
  }

  public boolean isGroupVisible() {
    return isGroupVisible;
  }

  public AccountGroup getGroup() {
    return group;
  }

  public SortedSet<Account> getAllAccounts() {
    return getAllAccounts(new HashSet<AccountGroup.Id>());
  }

  private SortedSet<Account> getAllAccounts(final Set<AccountGroup.Id> seen) {
    seen.add(group.getId());
    final SortedSet<Account> allAccounts =
        new TreeSet<Account>(new AccountComparator());
    allAccounts.addAll(accounts);
    for (final GroupMembers groupMembers : includedGroupMembers) {
      if (!seen.contains(groupMembers.getGroup().getId())) {
        allAccounts.addAll(groupMembers.getAllAccounts(seen));
      }
    }
    return allAccounts;
  }

  public SortedSet<GroupMembers> getAllIncludedGroups() {
    return getAllIncludedGroups(new HashSet<AccountGroup.Id>());
  }

  private SortedSet<GroupMembers> getAllIncludedGroups(
      final Set<AccountGroup.Id> seen) {
    final SortedSet<GroupMembers> allIncludedGroups =
        new TreeSet<GroupMembers>();
    for (final GroupMembers groupMembers : includedGroupMembers) {
      if (!seen.contains(groupMembers.getGroup().getId())) {
        seen.add(groupMembers.getGroup().getId());
        allIncludedGroups.add(groupMembers);
        allIncludedGroups.addAll(groupMembers.getAllIncludedGroups(seen));
      }
    }
    return allIncludedGroups;
  }

  /**
   * Returns all groups of this GroupMembers that are of the given group type.
   *
   * @param groupType the group type
   * @return all groups of this GroupMembers that are of the given group type
   */
  public Map<AccountGroup.UUID, GroupMembers> getGroups(
      final AccountGroup.Type groupType) {
    return getGroups(groupType, new HashSet<AccountGroup.Id>());
  }

  private Map<AccountGroup.UUID, GroupMembers> getGroups(
      final AccountGroup.Type groupType, final Set<AccountGroup.Id> seen) {
    final Map<AccountGroup.UUID, GroupMembers> allGroups =
        new HashMap<AccountGroup.UUID, GroupMembers>();
    if (groupType.equals(group.getType())) {
      allGroups.put(group.getGroupUUID(), this);
    }
    seen.add(group.getId());
    for (final GroupMembers groupMembers : includedGroupMembers) {
      if (!seen.contains(groupMembers.getGroup().getId())) {
        allGroups.putAll(groupMembers.getGroups(groupType, seen));
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
    for (final GroupMembers groupMembers : includedGroupMembers) {
      if (!seen.contains(groupMembers.getGroup().getId())) {
        if (groupMembers.containsNonVisibleGroup(seen)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public int compareTo(final GroupMembers other) {
    return group.getName().compareTo(other.group.getName());
  }

  @Override
  public int hashCode() {
    return group.getName().hashCode();
  }

  @Override
  public boolean equals(final Object other) {
    if (other instanceof GroupMembers) {
      return group.getName().equals(((GroupMembers) other).group.getName());
    }
    return false;
  }
}
