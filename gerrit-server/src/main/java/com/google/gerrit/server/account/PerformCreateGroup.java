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
import com.google.gerrit.common.errors.PermissionDeniedException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupByIdAud;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.PersonIdent;

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
  private final PersonIdent serverIdent;
  private final GroupCache groupCache;

  @Inject
  PerformCreateGroup(final ReviewDb db, final AccountCache accountCache,
      final GroupIncludeCache groupIncludeCache,
      final IdentifiedUser currentUser,
      @GerritPersonIdent final PersonIdent serverIdent,
      final GroupCache groupCache) {
    this.db = db;
    this.accountCache = accountCache;
    this.groupIncludeCache = groupIncludeCache;
    this.currentUser = currentUser;
    this.serverIdent = serverIdent;
    this.groupCache = groupCache;
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
   * @throws PermissionDeniedException user cannot create a group.
   */
  public AccountGroup createGroup(final String groupName,
      final String groupDescription, final boolean visibleToAll,
      final AccountGroup.Id ownerGroupId,
      final Collection<? extends Account.Id> initialMembers,
      final Collection<? extends AccountGroup.UUID> initialGroups)
      throws OrmException, NameAlreadyUsedException, PermissionDeniedException {
    if (!currentUser.getCapabilities().canCreateGroup()) {
      throw new PermissionDeniedException(String.format(
        "%s does not have \"Create Group\" capability.",
        currentUser.getUserName()));
    }

    final AccountGroup.Id groupId =
        new AccountGroup.Id(db.nextAccountGroupId());
    final AccountGroup.NameKey nameKey = new AccountGroup.NameKey(groupName);
    final AccountGroup.UUID uuid = GroupUUID.make(groupName,
        currentUser.newCommitterIdent(
            serverIdent.getWhen(),
            serverIdent.getTimeZone()));
    final AccountGroup group = new AccountGroup(nameKey, groupId, uuid);
    group.setVisibleToAll(visibleToAll);
    if (ownerGroupId != null) {
      AccountGroup ownerGroup = groupCache.get(ownerGroupId);
      if (ownerGroup != null) {
        group.setOwnerGroupUUID(ownerGroup.getGroupUUID());
      }
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
      throw new NameAlreadyUsedException(groupName);
    }
    db.accountGroups().insert(Collections.singleton(group));

    addMembers(groupId, initialMembers);

    if (initialGroups != null) {
      addGroups(groupId, initialGroups);
      groupIncludeCache.evictMembersOf(uuid);
    }

    groupCache.onCreateGroup(nameKey);

    return group;
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
      final Collection<? extends AccountGroup.UUID> groups) throws OrmException {
    final List<AccountGroupById> includeList =
      new ArrayList<AccountGroupById>();
    final List<AccountGroupByIdAud> includesAudit =
      new ArrayList<AccountGroupByIdAud>();
    for (AccountGroup.UUID includeUUID : groups) {
      final AccountGroupById groupInclude =
        new AccountGroupById(new AccountGroupById.Key(groupId, includeUUID));
      includeList.add(groupInclude);

      final AccountGroupByIdAud audit =
        new AccountGroupByIdAud(groupInclude, currentUser.getAccountId());
      includesAudit.add(audit);
    }
    db.accountGroupById().insert(includeList);
    db.accountGroupByIdAud().insert(includesAudit);

    for (AccountGroup.UUID uuid : groups) {
      groupIncludeCache.evictMemberIn(uuid);
    }
  }
}
