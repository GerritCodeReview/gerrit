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

import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A database accessor for read calls related to groups.
 *
 * <p>All calls which read group related details from the database (either ReviewDb or NoteDb) are
 * gathered here. Other classes should always use this class instead of accessing the database
 * directly. There are a few exceptions though: schema classes, wrapper classes, and classes
 * executed during init. The latter ones should use {@code GroupsOnInit} instead.
 *
 * <p>If not explicitly stated, all methods of this class refer to <em>internal</em> groups.
 */
@Singleton
public class Groups {

  /**
   * Returns the {@code AccountGroup} for the specified UUID.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupUuid the UUID of the group
   * @return the {@code AccountGroup} which has the specified UUID
   * @throws OrmDuplicateKeyException if multiple groups are found for the specified UUID
   * @throws OrmException if the group couldn't be retrieved from ReviewDb
   * @throws NoSuchGroupException if a group with such a UUID doesn't exist
   */
  public AccountGroup getExistingGroup(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException, NoSuchGroupException {
    Optional<AccountGroup> group = getGroup(db, groupUuid);
    return group.orElseThrow(() -> new NoSuchGroupException(groupUuid));
  }

  /**
   * Returns the {@code AccountGroup} for the specified ID if it exists.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupId the ID of the group
   * @return the found {@code AccountGroup} if it exists, or else an empty {@code Optional}
   * @throws OrmException if the group couldn't be retrieved from ReviewDb
   */
  public Optional<AccountGroup> getGroup(ReviewDb db, AccountGroup.Id groupId) throws OrmException {
    return Optional.ofNullable(db.accountGroups().get(groupId));
  }

  /**
   * Returns the {@code AccountGroup} for the specified UUID if it exists.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupUuid the UUID of the group
   * @return the found {@code AccountGroup} if it exists, or else an empty {@code Optional}
   * @throws OrmDuplicateKeyException if multiple groups are found for the specified UUID
   * @throws OrmException if the group couldn't be retrieved from ReviewDb
   */
  public Optional<AccountGroup> getGroup(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException {
    List<AccountGroup> accountGroups = db.accountGroups().byUUID(groupUuid).toList();
    if (accountGroups.size() == 1) {
      return Optional.of(Iterables.getOnlyElement(accountGroups));
    } else if (accountGroups.isEmpty()) {
      return Optional.empty();
    } else {
      throw new OrmDuplicateKeyException("Duplicate group UUID " + groupUuid);
    }
  }

  /**
   * Returns the {@code AccountGroup} for the specified name if it exists.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupName the name of the group
   * @return the found {@code AccountGroup} if it exists, or else an empty {@code Optional}
   * @throws OrmException if the group couldn't be retrieved from ReviewDb
   */
  public Optional<AccountGroup> getGroup(ReviewDb db, AccountGroup.NameKey groupName)
      throws OrmException {
    AccountGroupName accountGroupName = db.accountGroupNames().get(groupName);
    if (accountGroupName == null) {
      return Optional.empty();
    }

    AccountGroup.Id groupId = accountGroupName.getId();
    return Optional.ofNullable(db.accountGroups().get(groupId));
  }

  public Stream<AccountGroup> getAll(ReviewDb db) throws OrmException {
    return Streams.stream(db.accountGroups().all());
  }

  /**
   * Indicates whether the specified account is a member of the specified group.
   *
   * <p><strong>Note</strong>: This method doesn't check whether the account exists!
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupUuid the UUID of the group
   * @param accountId the ID of the account
   * @return {@code true} if the account is a member of the group, or else {@code false}
   * @throws OrmException if an error occurs while reading from ReviewDb
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public boolean isMember(ReviewDb db, AccountGroup.UUID groupUuid, Account.Id accountId)
      throws OrmException, NoSuchGroupException {
    AccountGroup group = getExistingGroup(db, groupUuid);
    AccountGroupMember.Key key = new AccountGroupMember.Key(accountId, group.getId());
    return db.accountGroupMembers().get(key) != null;
  }

  /**
   * Indicates whether the specified group is a subgroup of the specified parent group.
   *
   * <p>The parent group must be an internal group whereas the subgroup may either be an internal or
   * an external group.
   *
   * <p><strong>Note</strong>: This method doesn't check whether the subgroup exists!
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param parentGroupUuid the UUID of the parent group
   * @param includedGroupUuid the UUID of the subgroup
   * @return {@code true} if the group is a subgroup of the other group, or else {@code false}
   * @throws OrmException if an error occurs while reading from ReviewDb
   * @throws NoSuchGroupException if the specified parent group doesn't exist
   */
  public boolean isIncluded(
      ReviewDb db, AccountGroup.UUID parentGroupUuid, AccountGroup.UUID includedGroupUuid)
      throws OrmException, NoSuchGroupException {
    AccountGroup parentGroup = getExistingGroup(db, parentGroupUuid);
    AccountGroupById.Key key = new AccountGroupById.Key(parentGroup.getId(), includedGroupUuid);
    return db.accountGroupById().get(key) != null;
  }

  /**
   * Returns the members (accounts) of a group.
   *
   * <p><strong>Note</strong>: This method doesn't check whether the accounts exist!
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupUuid the UUID of the group
   * @return a stream of the IDs of the members
   * @throws OrmException if an error occurs while reading from ReviewDb
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public Stream<Account.Id> getMembers(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException, NoSuchGroupException {
    AccountGroup group = getExistingGroup(db, groupUuid);
    ResultSet<AccountGroupMember> accountGroupMembers =
        db.accountGroupMembers().byGroup(group.getId());
    return Streams.stream(accountGroupMembers).map(AccountGroupMember::getAccountId);
  }

  /**
   * Returns the subgroups of a group.
   *
   * <p>This parent group must be an internal group whereas the subgroups can either be internal or
   * external groups.
   *
   * <p><strong>Note</strong>: This method doesn't check whether the subgroups exist!
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupUuid the UUID of the parent group
   * @return a stream of the UUIDs of the subgroups
   * @throws OrmException if an error occurs while reading from ReviewDb
   * @throws NoSuchGroupException if the specified parent group doesn't exist
   */
  public Stream<AccountGroup.UUID> getIncludes(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException, NoSuchGroupException {
    AccountGroup group = getExistingGroup(db, groupUuid);
    ResultSet<AccountGroupById> accountGroupByIds = db.accountGroupById().byGroup(group.getId());
    return Streams.stream(accountGroupByIds).map(AccountGroupById::getIncludeUUID).distinct();
  }

  /**
   * Returns the groups of which the specified account is a member.
   *
   * <p><strong>Note</strong>: This method returns an empty stream if the account doesn't exist.
   * This method doesn't check whether the groups exist.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param accountId the ID of the account
   * @return a stream of the IDs of the groups of which the account is a member
   * @throws OrmException if an error occurs while reading from ReviewDb
   */
  public Stream<AccountGroup.Id> getGroupsWithMember(ReviewDb db, Account.Id accountId)
      throws OrmException {
    ResultSet<AccountGroupMember> accountGroupMembers =
        db.accountGroupMembers().byAccount(accountId);
    return Streams.stream(accountGroupMembers).map(AccountGroupMember::getAccountGroupId);
  }

  /**
   * Returns the parent groups of the specified (sub)group.
   *
   * <p>The subgroup may either be an internal or an external group whereas the returned parent
   * groups represent only internal groups.
   *
   * <p><strong>Note</strong>: This method returns an empty stream if the specified group doesn't
   * exist. This method doesn't check whether the parent groups exist.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param includedGroupUuid the UUID of the subgroup
   * @return a stream of the IDs of the parent groups
   * @throws OrmException if an error occurs while reading from ReviewDb
   */
  public Stream<AccountGroup.Id> getParentGroups(ReviewDb db, AccountGroup.UUID includedGroupUuid)
      throws OrmException {
    ResultSet<AccountGroupById> accountGroupByIds =
        db.accountGroupById().byIncludeUUID(includedGroupUuid);
    return Streams.stream(accountGroupByIds).map(AccountGroupById::getGroupId);
  }

  /**
   * Returns all known external groups. External groups are 'known' when they are specified as a
   * subgroup of an internal group.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @return a stream of the UUIDs of the known external groups
   * @throws OrmException if an error occurs while reading from ReviewDb
   */
  public Stream<AccountGroup.UUID> getExternalGroups(ReviewDb db) throws OrmException {
    return Streams.stream(db.accountGroupById().all())
        .map(AccountGroupById::getIncludeUUID)
        .distinct()
        .filter(groupUuid -> !AccountGroup.isInternalGroup(groupUuid));
  }
}
