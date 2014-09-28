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
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.PersonIdent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PerformCreateGroup {

  public interface Factory {
    PerformCreateGroup create(CreateGroupArgs createGroupArgs);
  }

  private final ReviewDb db;
  private final AccountCache accountCache;
  private final GroupIncludeCache groupIncludeCache;
  private final IdentifiedUser currentUser;
  private final PersonIdent serverIdent;
  private final GroupCache groupCache;
  private final CreateGroupArgs createGroupArgs;

  @Inject
  PerformCreateGroup(ReviewDb db, AccountCache accountCache,
      GroupIncludeCache groupIncludeCache, IdentifiedUser currentUser,
      @GerritPersonIdent PersonIdent serverIdent, GroupCache groupCache,
      @Assisted CreateGroupArgs createGroupArgs) {
    this.db = db;
    this.accountCache = accountCache;
    this.groupIncludeCache = groupIncludeCache;
    this.currentUser = currentUser;
    this.serverIdent = serverIdent;
    this.groupCache = groupCache;
    this.createGroupArgs = createGroupArgs;
  }

  /**
   * Creates a new group.

   * @return the new group
   * @throws OrmException is thrown in case of any data store read or write
   *         error
   * @throws NameAlreadyUsedException is thrown in case a group with the given
   *         name already exists
   * @throws PermissionDeniedException user cannot create a group.
   */
  public AccountGroup createGroup() throws OrmException,
      NameAlreadyUsedException, PermissionDeniedException {
    if (!currentUser.getCapabilities().canCreateGroup()) {
      throw new PermissionDeniedException(String.format(
        "%s does not have \"Create Group\" capability.",
        currentUser.getUserName()));
    }
    AccountGroup.Id groupId = new AccountGroup.Id(db.nextAccountGroupId());
    AccountGroup.UUID uuid = GroupUUID.make(
        createGroupArgs.getGroupName(),
        currentUser.newCommitterIdent(
            serverIdent.getWhen(),
            serverIdent.getTimeZone()));
    AccountGroup group =
        new AccountGroup(createGroupArgs.getGroup(), groupId, uuid);
    group.setVisibleToAll(createGroupArgs.visibleToAll);
    if (createGroupArgs.ownerGroupId != null) {
      AccountGroup ownerGroup = groupCache.get(createGroupArgs.ownerGroupId);
      if (ownerGroup != null) {
        group.setOwnerGroupUUID(ownerGroup.getGroupUUID());
      }
    }
    if (createGroupArgs.groupDescription != null) {
      group.setDescription(createGroupArgs.groupDescription);
    }
    AccountGroupName gn = new AccountGroupName(group);
    // first insert the group name to validate that the group name hasn't
    // already been used to create another group
    try {
      db.accountGroupNames().insert(Collections.singleton(gn));
    } catch (OrmDuplicateKeyException e) {
      throw new NameAlreadyUsedException(createGroupArgs.getGroupName());
    }
    db.accountGroups().insert(Collections.singleton(group));

    addMembers(groupId, createGroupArgs.initialMembers);

    if (createGroupArgs.initialGroups != null) {
      addGroups(groupId, createGroupArgs.initialGroups);
      groupIncludeCache.evictSubgroupsOf(uuid);
    }

    groupCache.onCreateGroup(createGroupArgs.getGroup());

    return group;
  }

  private void addMembers(final AccountGroup.Id groupId,
      final Collection<? extends Account.Id> members) throws OrmException {
    List<AccountGroupMember> memberships = new ArrayList<>();
    List<AccountGroupMemberAudit> membershipsAudit = new ArrayList<>();
    for (Account.Id accountId : members) {
      final AccountGroupMember membership =
          new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId));
      memberships.add(membership);

      final AccountGroupMemberAudit audit = new AccountGroupMemberAudit(
          membership, currentUser.getAccountId(), TimeUtil.nowTs());
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
    List<AccountGroupById> includeList = new ArrayList<>();
    List<AccountGroupByIdAud> includesAudit = new ArrayList<>();
    for (AccountGroup.UUID includeUUID : groups) {
      final AccountGroupById groupInclude =
        new AccountGroupById(new AccountGroupById.Key(groupId, includeUUID));
      includeList.add(groupInclude);

      final AccountGroupByIdAud audit = new AccountGroupByIdAud(
          groupInclude, currentUser.getAccountId(), TimeUtil.nowTs());
      includesAudit.add(audit);
    }
    db.accountGroupById().insert(includeList);
    db.accountGroupByIdAud().insert(includesAudit);

    for (AccountGroup.UUID uuid : groups) {
      groupIncludeCache.evictParentGroupsOf(uuid);
    }
  }
}
