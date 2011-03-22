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

import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupInclude;
import com.google.gerrit.reviewdb.AccountGroupIncludeAudit;
import com.google.gerrit.reviewdb.AccountGroupMember;
import com.google.gerrit.reviewdb.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.AccountGroupName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.client.OrmDuplicateKeyException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PerformCreateGroup {

  public interface Factory {
    PerformCreateGroup create();
  }

  private final ReviewDb db;
  private final AccountCache accountCache;
  private final GroupIncludeCache groupIncludeCache;
  private final IdentifiedUser currentUser;

  @Inject
  PerformCreateGroup(final ReviewDb db, final AccountCache accountCache,
      final GroupIncludeCache groupIncludeCache, final IdentifiedUser currentUser) {
    this.db = db;
    this.accountCache = accountCache;
    this.groupIncludeCache = groupIncludeCache;
    this.currentUser = currentUser;
  }

  /**
   * Creates a new group.
   *
   * @param groupName the name for the new group
   * @param groupDescription the description of the new group, <code>null</code>
   *        if no description
   * @param visibleToAll <code>true</code> to make the group visible to all
   *        registered users, if <code>false</code> the group is only visible to
   *        the group owners and Gerrit administrators
   * @param ownerGroupId the group that should own the new group, if
   *        <code>null</code> the new group will own itself
   * @param initialMembers initial members to be added to the new group
   * @param initialGroups initial groups to include in the new group
   * @return the id of the new group
   * @throws OrmException is thrown in case of any data store read or write
   *         error
   * @throws NameAlreadyUsedException is thrown in case a group with the given
   *         name already exists
   */
  public AccountGroup.Id createGroup(final String groupName,
      final String groupDescription, final boolean visibleToAll,
      final AccountGroup.Id ownerGroupId,
      final Collection<? extends Account.Id> initialMembers,
      final Collection<? extends AccountGroup.Id> initialGroups)
      throws OrmException, NameAlreadyUsedException {
    final AccountGroup.Id groupId =
        new AccountGroup.Id(db.nextAccountGroupId());
    final AccountGroup.NameKey nameKey = new AccountGroup.NameKey(groupName);
    final AccountGroup group = new AccountGroup(nameKey, groupId);
    group.setVisibleToAll(visibleToAll);
    if (ownerGroupId != null) {
      group.setOwnerGroupId(ownerGroupId);
    }
    if (groupDescription != null) {
      group.setDescription(groupDescription);
    }
    final AccountGroupName gn = new AccountGroupName(group);
    // first insert the group name to validate that the group name hasn't
    // already been used to create another group
    try {
      db.accountGroupNames().insert(Collections.singleton(gn));
    } catch (OrmDuplicateKeyException e) {
      throw new NameAlreadyUsedException();
    }
    db.accountGroups().insert(Collections.singleton(group));

    addMembers(groupId, initialMembers);

    if (initialGroups != null) {
      addGroups(groupId, initialGroups);
    }

    return groupId;
  }

  private void addMembers(final AccountGroup.Id groupId,
      final Collection<? extends Account.Id> members) throws OrmException {
    final List<AccountGroupMember> memberships =
        new ArrayList<AccountGroupMember>();
    final List<AccountGroupMemberAudit> membershipsAudit =
        new ArrayList<AccountGroupMemberAudit>();
    for (Account.Id accountId : members) {
      final AccountGroupMember membership =
          new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId));
      memberships.add(membership);

      final AccountGroupMemberAudit audit =
          new AccountGroupMemberAudit(membership, currentUser.getAccountId());
      membershipsAudit.add(audit);
    }
    db.accountGroupMembers().insert(memberships);
    db.accountGroupMembersAudit().insert(membershipsAudit);

    for (Account.Id accountId : members) {
      accountCache.evict(accountId);
    }
  }

  private void addGroups(final AccountGroup.Id groupId,
      final Collection<? extends AccountGroup.Id> groups) throws OrmException {
    final List<AccountGroupInclude> includeList =
      new ArrayList<AccountGroupInclude>();
    final List<AccountGroupIncludeAudit> includesAudit =
      new ArrayList<AccountGroupIncludeAudit>();
    for (AccountGroup.Id includeId : groups) {
      final AccountGroupInclude groupInclude =
        new AccountGroupInclude(new AccountGroupInclude.Key(groupId, includeId));
      includeList.add(groupInclude);

      final AccountGroupIncludeAudit audit =
        new AccountGroupIncludeAudit(groupInclude, currentUser.getAccountId());
      includesAudit.add(audit);
    }
    db.accountGroupIncludes().insert(includeList);
    db.accountGroupIncludesAudit().insert(includesAudit);

    for (AccountGroup.Id includeId : groups) {
      groupIncludeCache.evictInclude(includeId);
    }
  }
}
