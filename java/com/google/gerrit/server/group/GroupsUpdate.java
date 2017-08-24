// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.group;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.errors.NameAlreadyUsedException;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.git.RenameGroupOp;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * A database accessor for write calls related to groups.
 *
 * <p>All calls which write group related details to the database (either ReviewDb or NoteDb) are
 * gathered here. Other classes should always use this class instead of accessing the database
 * directly. There are a few exceptions though: schema classes, wrapper classes, and classes
 * executed during init. The latter ones should use {@code GroupsOnInit} instead.
 *
 * <p>If not explicitly stated, all methods of this class refer to <em>internal</em> groups.
 */
public class GroupsUpdate {
  public interface Factory {
    GroupsUpdate create(@Nullable IdentifiedUser currentUser);
  }

  private final Groups groups;
  private final GroupCache groupCache;
  private final GroupIncludeCache groupIncludeCache;
  private final AuditService auditService;
  private final AccountCache accountCache;
  private final RenameGroupOp.Factory renameGroupOpFactory;
  private final PersonIdent serverIdent;

  @Nullable private IdentifiedUser currentUser;
  private PersonIdent committerIdent;

  @Inject
  GroupsUpdate(
      Groups groups,
      GroupCache groupCache,
      GroupIncludeCache groupIncludeCache,
      AuditService auditService,
      AccountCache accountCache,
      RenameGroupOp.Factory renameGroupOpFactory,
      @GerritPersonIdent PersonIdent serverIdent,
      @Assisted @Nullable IdentifiedUser currentUser) {
    this.groups = groups;
    this.groupCache = groupCache;
    this.groupIncludeCache = groupIncludeCache;
    this.auditService = auditService;
    this.accountCache = accountCache;
    this.renameGroupOpFactory = renameGroupOpFactory;
    this.serverIdent = serverIdent;

    setCurrentUser(currentUser);
  }

  /**
   * Uses the identity of the specified user to mark database modifications executed by this {@code
   * GroupsUpdate}. For NoteDb, this identity is used as author and committer for all related
   * commits.
   *
   * <p><strong>Note</strong>: Please use this method with care and rather consider to use the
   * correct annotation on the provider of this class instead.
   *
   * @param currentUser the user to which modifications should be attributed, or {@code null} if the
   *     Gerrit server identity should be used
   */
  public void setCurrentUser(@Nullable IdentifiedUser currentUser) {
    this.currentUser = currentUser;
    setCommitterIdent(currentUser);
  }

  private void setCommitterIdent(@Nullable IdentifiedUser currentUser) {
    if (currentUser != null) {
      committerIdent = createPersonIdent(serverIdent, currentUser);
    } else {
      committerIdent = serverIdent;
    }
  }

  private static PersonIdent createPersonIdent(PersonIdent ident, IdentifiedUser user) {
    return user.newCommitterIdent(ident.getWhen(), ident.getTimeZone());
  }

  /**
   * Adds/Creates the specified group for the specified members (accounts).
   *
   * @param db the {@code ReviewDb} instance to update
   * @param group the group to add
   * @param memberIds the IDs of the accounts which should be members of the created group
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws IOException if the cache entry of one of the new members couldn't be invalidated, or
   *     the new group couldn't be indexed
   */
  public void addGroup(ReviewDb db, AccountGroup group, Set<Account.Id> memberIds)
      throws OrmException, IOException {
    addNewGroup(db, group);
    addNewGroupMembers(db, group, memberIds);
    groupCache.onCreateGroup(group.getNameKey());
  }

  /**
   * Adds the specified group.
   *
   * <p><strong>Note</strong>: This method doesn't update the index! It just adds the group to the
   * database. Use this method with care.
   *
   * @param db the {@code ReviewDb} instance to update
   * @param group the group to add
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   */
  public static void addNewGroup(ReviewDb db, AccountGroup group) throws OrmException {
    AccountGroupName gn = new AccountGroupName(group);
    // first insert the group name to validate that the group name hasn't
    // already been used to create another group
    db.accountGroupNames().insert(ImmutableList.of(gn));
    db.accountGroups().insert(ImmutableList.of(group));
  }

  /**
   * Updates the specified group.
   *
   * @param db the {@code ReviewDb} instance to update
   * @param groupUuid the UUID of the group to update
   * @param groupConsumer a {@code Consumer} which performs the desired updates on the group
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws IOException if the cache entry for the group couldn't be invalidated
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void updateGroup(
      ReviewDb db, AccountGroup.UUID groupUuid, Consumer<AccountGroup> groupConsumer)
      throws OrmException, IOException, NoSuchGroupException {
    AccountGroup updatedGroup = updateGroupInDb(db, groupUuid, groupConsumer);
    groupCache.evict(updatedGroup);
  }

  @VisibleForTesting
  public AccountGroup updateGroupInDb(
      ReviewDb db, AccountGroup.UUID groupUuid, Consumer<AccountGroup> groupConsumer)
      throws OrmException, NoSuchGroupException {
    AccountGroup group = groups.getExistingGroup(db, groupUuid);
    groupConsumer.accept(group);
    db.accountGroups().update(ImmutableList.of(group));
    return group;
  }

  /**
   * Renames the specified group.
   *
   * @param db the {@code ReviewDb} instance to update
   * @param groupUuid the UUID of the group to rename
   * @param newName the new name of the group
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws IOException if the cache entry for the group couldn't be invalidated
   * @throws NoSuchGroupException if the specified group doesn't exist
   * @throws NameAlreadyUsedException if another group has the name {@code newName}
   */
  public void renameGroup(ReviewDb db, AccountGroup.UUID groupUuid, AccountGroup.NameKey newName)
      throws OrmException, IOException, NameAlreadyUsedException, NoSuchGroupException {
    AccountGroup group = groups.getExistingGroup(db, groupUuid);
    AccountGroup.NameKey oldName = group.getNameKey();

    try {
      AccountGroupName id = new AccountGroupName(newName, group.getId());
      db.accountGroupNames().insert(ImmutableList.of(id));
    } catch (OrmException e) {
      AccountGroupName other = db.accountGroupNames().get(newName);
      if (other != null) {
        // If we are using this identity, don't report the exception.
        if (other.getId().equals(group.getId())) {
          return;
        }

        // Otherwise, someone else has this identity.
        throw new NameAlreadyUsedException("group with name " + newName + " already exists");
      }
      throw e;
    }

    group.setNameKey(newName);
    db.accountGroups().update(ImmutableList.of(group));

    db.accountGroupNames().deleteKeys(ImmutableList.of(oldName));

    groupCache.evict(group);
    groupCache.evictAfterRename(oldName, newName);

    @SuppressWarnings("unused")
    Future<?> possiblyIgnoredError =
        renameGroupOpFactory
            .create(committerIdent, groupUuid, oldName.get(), newName.get())
            .start(0, TimeUnit.MILLISECONDS);
  }

  /**
   * Adds an account as member to a group. The account is only added as a new member if it isn't
   * already a member of the group.
   *
   * <p><strong>Note</strong>: This method doesn't check whether the account exists!
   *
   * @param db the {@code ReviewDb} instance to update
   * @param groupUuid the UUID of the group
   * @param accountId the ID of the account to add
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws IOException if the cache entry of the new member couldn't be invalidated
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void addGroupMember(ReviewDb db, AccountGroup.UUID groupUuid, Account.Id accountId)
      throws OrmException, IOException, NoSuchGroupException {
    addGroupMembers(db, groupUuid, ImmutableSet.of(accountId));
  }

  /**
   * Adds several accounts as members to a group. Only accounts which currently aren't members of
   * the group are added.
   *
   * <p><strong>Note</strong>: This method doesn't check whether the accounts exist!
   *
   * @param db the {@code ReviewDb} instance to update
   * @param groupUuid the UUID of the group
   * @param accountIds a set of IDs of accounts to add
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws IOException if the cache entry of one of the new members couldn't be invalidated
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void addGroupMembers(ReviewDb db, AccountGroup.UUID groupUuid, Set<Account.Id> accountIds)
      throws OrmException, IOException, NoSuchGroupException {
    AccountGroup group = groups.getExistingGroup(db, groupUuid);
    Set<Account.Id> newMemberIds = new HashSet<>();
    for (Account.Id accountId : accountIds) {
      boolean isMember = groups.isMember(db, groupUuid, accountId);
      if (!isMember) {
        newMemberIds.add(accountId);
      }
    }

    if (newMemberIds.isEmpty()) {
      return;
    }

    addNewGroupMembers(db, group, newMemberIds);
  }

  private void addNewGroupMembers(ReviewDb db, AccountGroup group, Set<Account.Id> newMemberIds)
      throws OrmException, IOException {
    Set<AccountGroupMember> newMembers =
        newMemberIds
            .stream()
            .map(accountId -> new AccountGroupMember.Key(accountId, group.getId()))
            .map(AccountGroupMember::new)
            .collect(toImmutableSet());

    if (currentUser != null) {
      auditService.dispatchAddAccountsToGroup(currentUser.getAccountId(), newMembers);
    }
    db.accountGroupMembers().insert(newMembers);
    for (AccountGroupMember newMember : newMembers) {
      accountCache.evict(newMember.getAccountId());
    }
  }

  /**
   * Removes several members (accounts) from a group. Only accounts which currently are members of
   * the group are removed.
   *
   * @param db the {@code ReviewDb} instance to update
   * @param groupUuid the UUID of the group
   * @param accountIds a set of IDs of accounts to remove
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws IOException if the cache entry of one of the removed members couldn't be invalidated
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void removeGroupMembers(
      ReviewDb db, AccountGroup.UUID groupUuid, Set<Account.Id> accountIds)
      throws OrmException, IOException, NoSuchGroupException {
    AccountGroup group = groups.getExistingGroup(db, groupUuid);
    AccountGroup.Id groupId = group.getId();
    Set<AccountGroupMember> membersToRemove = new HashSet<>();
    for (Account.Id accountId : accountIds) {
      boolean isMember = groups.isMember(db, groupUuid, accountId);
      if (isMember) {
        AccountGroupMember.Key key = new AccountGroupMember.Key(accountId, groupId);
        membersToRemove.add(new AccountGroupMember(key));
      }
    }

    if (membersToRemove.isEmpty()) {
      return;
    }

    if (currentUser != null) {
      auditService.dispatchDeleteAccountsFromGroup(currentUser.getAccountId(), membersToRemove);
    }
    db.accountGroupMembers().delete(membersToRemove);
    for (AccountGroupMember member : membersToRemove) {
      accountCache.evict(member.getAccountId());
    }
  }

  /**
   * Adds several groups as subgroups to a group. Only groups which currently aren't subgroups of
   * the group are added.
   *
   * <p>The parent group must be an internal group whereas the subgroups can either be internal or
   * external groups.
   *
   * <p><strong>Note</strong>: This method doesn't check whether the subgroups exist!
   *
   * @param db the {@code ReviewDb} instance to update
   * @param parentGroupUuid the UUID of the parent group
   * @param includedGroupUuids a set of IDs of the groups to add as subgroups
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws NoSuchGroupException if the specified parent group doesn't exist
   */
  public void addIncludedGroups(
      ReviewDb db, AccountGroup.UUID parentGroupUuid, Set<AccountGroup.UUID> includedGroupUuids)
      throws OrmException, NoSuchGroupException {
    AccountGroup parentGroup = groups.getExistingGroup(db, parentGroupUuid);
    AccountGroup.Id parentGroupId = parentGroup.getId();
    Set<AccountGroupById> newIncludedGroups = new HashSet<>();
    for (AccountGroup.UUID includedGroupUuid : includedGroupUuids) {
      boolean isIncluded = groups.isIncluded(db, parentGroupUuid, includedGroupUuid);
      if (!isIncluded) {
        AccountGroupById.Key key = new AccountGroupById.Key(parentGroupId, includedGroupUuid);
        newIncludedGroups.add(new AccountGroupById(key));
      }
    }

    if (newIncludedGroups.isEmpty()) {
      return;
    }

    if (currentUser != null) {
      auditService.dispatchAddGroupsToGroup(currentUser.getAccountId(), newIncludedGroups);
    }
    db.accountGroupById().insert(newIncludedGroups);
    for (AccountGroupById newIncludedGroup : newIncludedGroups) {
      groupIncludeCache.evictParentGroupsOf(newIncludedGroup.getIncludeUUID());
    }
    groupIncludeCache.evictSubgroupsOf(parentGroupUuid);
  }

  /**
   * Removes several subgroups from a parent group. Only groups which currently are subgroups of the
   * group are removed.
   *
   * <p>The parent group must be an internal group whereas the subgroups can either be internal or
   * external groups.
   *
   * @param db the {@code ReviewDb} instance to update
   * @param parentGroupUuid the UUID of the parent group
   * @param includedGroupUuids a set of IDs of the subgroups to remove from the parent group
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws NoSuchGroupException if the specified parent group doesn't exist
   */
  public void deleteIncludedGroups(
      ReviewDb db, AccountGroup.UUID parentGroupUuid, Set<AccountGroup.UUID> includedGroupUuids)
      throws OrmException, NoSuchGroupException {
    AccountGroup parentGroup = groups.getExistingGroup(db, parentGroupUuid);
    AccountGroup.Id parentGroupId = parentGroup.getId();
    Set<AccountGroupById> includedGroupsToRemove = new HashSet<>();
    for (AccountGroup.UUID includedGroupUuid : includedGroupUuids) {
      boolean isIncluded = groups.isIncluded(db, parentGroupUuid, includedGroupUuid);
      if (isIncluded) {
        AccountGroupById.Key key = new AccountGroupById.Key(parentGroupId, includedGroupUuid);
        includedGroupsToRemove.add(new AccountGroupById(key));
      }
    }

    if (includedGroupsToRemove.isEmpty()) {
      return;
    }

    if (currentUser != null) {
      auditService.dispatchDeleteGroupsFromGroup(
          currentUser.getAccountId(), includedGroupsToRemove);
    }
    db.accountGroupById().delete(includedGroupsToRemove);
    for (AccountGroupById groupToRemove : includedGroupsToRemove) {
      groupIncludeCache.evictParentGroupsOf(groupToRemove.getIncludeUUID());
    }
    groupIncludeCache.evictSubgroupsOf(parentGroupUuid);
  }
}
