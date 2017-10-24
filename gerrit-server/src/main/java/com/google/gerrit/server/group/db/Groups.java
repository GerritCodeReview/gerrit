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

package com.google.gerrit.server.group.db;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  private static final Logger log = LoggerFactory.getLogger(Groups.class);

  /**
   * Returns the {@code AccountGroup} for the specified ID if it exists.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupId the ID of the group
   * @return the found {@code AccountGroup} if it exists, or else an empty {@code Optional}
   * @throws OrmException if the group couldn't be retrieved from ReviewDb
   */
  public Optional<InternalGroup> getGroup(ReviewDb db, AccountGroup.Id groupId)
      throws OrmException {
    AccountGroup accountGroup = db.accountGroups().get(groupId);
    if (accountGroup == null) {
      return Optional.empty();
    }
    return Optional.of(asInternalGroup(db, accountGroup));
  }

  /**
   * Returns the {@code InternalGroup} for the specified UUID if it exists.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupUuid the UUID of the group
   * @return the found {@code InternalGroup} if it exists, or else an empty {@code Optional}
   * @throws OrmDuplicateKeyException if multiple groups are found for the specified UUID
   * @throws OrmException if the group couldn't be retrieved from ReviewDb
   */
  public Optional<InternalGroup> getGroup(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException {
    Optional<AccountGroup> accountGroup = getGroupFromReviewDb(db, groupUuid);
    if (!accountGroup.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(asInternalGroup(db, accountGroup.get()));
  }

  private static InternalGroup asInternalGroup(ReviewDb db, AccountGroup accountGroup)
      throws OrmException {
    ImmutableSet<Account.Id> members =
        getMembersFromReviewDb(db, accountGroup.getId()).collect(toImmutableSet());
    ImmutableSet<AccountGroup.UUID> subgroups =
        getSubgroupsFromReviewDb(db, accountGroup.getId()).collect(toImmutableSet());
    return InternalGroup.create(accountGroup, members, subgroups);
  }

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
  static AccountGroup getExistingGroupFromReviewDb(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException, NoSuchGroupException {
    Optional<AccountGroup> group = getGroupFromReviewDb(db, groupUuid);
    return group.orElseThrow(() -> new NoSuchGroupException(groupUuid));
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
  private static Optional<AccountGroup> getGroupFromReviewDb(
      ReviewDb db, AccountGroup.UUID groupUuid) throws OrmException {
    List<AccountGroup> accountGroups = db.accountGroups().byUUID(groupUuid).toList();
    if (accountGroups.size() == 1) {
      return Optional.of(Iterables.getOnlyElement(accountGroups));
    } else if (accountGroups.isEmpty()) {
      return Optional.empty();
    } else {
      throw new OrmDuplicateKeyException("Duplicate group UUID " + groupUuid);
    }
  }

  public Stream<InternalGroup> getAll(ReviewDb db) throws OrmException {
    return getAllUuids(db)
        .map(groupUuid -> getGroupIfPossible(db, groupUuid))
        .flatMap(Streams::stream);
  }

  public Stream<AccountGroup.UUID> getAllUuids(ReviewDb db) throws OrmException {
    return Streams.stream(db.accountGroups().all()).map(AccountGroup::getGroupUUID);
  }

  private Optional<InternalGroup> getGroupIfPossible(ReviewDb db, AccountGroup.UUID groupUuid) {
    try {
      return getGroup(db, groupUuid);
    } catch (OrmException e) {
      log.warn(String.format("Cannot look up group %s by uuid", groupUuid.get()), e);
    }
    return Optional.empty();
  }

  /**
   * Returns the members (accounts) of a group.
   *
   * <p><strong>Note</strong>: This method doesn't check whether the accounts exist!
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupId the ID of the group
   * @return a stream of the IDs of the members
   * @throws OrmException if an error occurs while reading from ReviewDb
   */
  public static Stream<Account.Id> getMembersFromReviewDb(ReviewDb db, AccountGroup.Id groupId)
      throws OrmException {
    ResultSet<AccountGroupMember> accountGroupMembers = db.accountGroupMembers().byGroup(groupId);
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
   * @param groupId the ID of the group
   * @return a stream of the UUIDs of the subgroups
   * @throws OrmException if an error occurs while reading from ReviewDb
   */
  public static Stream<AccountGroup.UUID> getSubgroupsFromReviewDb(
      ReviewDb db, AccountGroup.Id groupId) throws OrmException {
    ResultSet<AccountGroupById> accountGroupByIds = db.accountGroupById().byGroup(groupId);
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
  public static Stream<AccountGroup.Id> getGroupsWithMemberFromReviewDb(
      ReviewDb db, Account.Id accountId) throws OrmException {
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
   * @param subgroupUuid the UUID of the subgroup
   * @return a stream of the IDs of the parent groups
   * @throws OrmException if an error occurs while reading from ReviewDb
   */
  public static Stream<AccountGroup.Id> getParentGroupsFromReviewDb(
      ReviewDb db, AccountGroup.UUID subgroupUuid) throws OrmException {
    ResultSet<AccountGroupById> accountGroupByIds =
        db.accountGroupById().byIncludeUUID(subgroupUuid);
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
