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
import static com.google.gerrit.server.group.db.Groups.getExistingGroupFromReviewDb;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.audit.AuditService;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.RenameGroupOp;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.notedb.GroupsMigration;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

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
    /**
     * Creates a {@code GroupsUpdate} which uses the identity of the specified user to mark database
     * modifications executed by it. For NoteDb, this identity is used as author and committer for
     * all related commits.
     *
     * <p><strong>Note</strong>: Please use this method with care and rather consider to use the
     * correct annotation on the provider of a {@code GroupsUpdate} instead.
     *
     * @param currentUser the user to which modifications should be attributed, or {@code null} if
     *     the Gerrit server identity should be used
     */
    GroupsUpdate create(@Nullable IdentifiedUser currentUser);
  }

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final GroupCache groupCache;
  private final GroupIncludeCache groupIncludeCache;
  private final Provider<GroupIndexer> indexer;
  private final AuditService auditService;
  private final RenameGroupOp.Factory renameGroupOpFactory;
  @Nullable private final IdentifiedUser currentUser;
  private final AuditLogFormatter auditLogFormatter;
  private final PersonIdent authorIdent;
  private final MetaDataUpdateFactory metaDataUpdateFactory;
  private final GroupsMigration groupsMigration;
  private final GitReferenceUpdated gitRefUpdated;
  private final RetryHelper retryHelper;
  private final boolean reviewDbUpdatesAreBlocked;

  @Inject
  GroupsUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      GroupBackend groupBackend,
      GroupCache groupCache,
      GroupIncludeCache groupIncludeCache,
      Provider<GroupIndexer> indexer,
      AuditService auditService,
      AccountCache accountCache,
      RenameGroupOp.Factory renameGroupOpFactory,
      @GerritServerId String serverId,
      @GerritPersonIdent PersonIdent serverIdent,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      GroupsMigration groupsMigration,
      @GerritServerConfig Config config,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      @Assisted @Nullable IdentifiedUser currentUser) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.groupCache = groupCache;
    this.groupIncludeCache = groupIncludeCache;
    this.indexer = indexer;
    this.auditService = auditService;
    this.renameGroupOpFactory = renameGroupOpFactory;
    this.groupsMigration = groupsMigration;
    this.gitRefUpdated = gitRefUpdated;
    this.retryHelper = retryHelper;
    this.currentUser = currentUser;

    auditLogFormatter = AuditLogFormatter.createBackedBy(accountCache, groupBackend, serverId);
    metaDataUpdateFactory =
        getMetaDataUpdateFactory(
            metaDataUpdateInternalFactory, currentUser, serverIdent, auditLogFormatter);
    authorIdent = getAuthorIdent(serverIdent, currentUser);
    reviewDbUpdatesAreBlocked = config.getBoolean("user", null, "blockReviewDbGroupUpdates", false);
  }

  private static MetaDataUpdateFactory getMetaDataUpdateFactory(
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      @Nullable IdentifiedUser currentUser,
      PersonIdent serverIdent,
      AuditLogFormatter auditLogFormatter) {
    return (projectName, repository, batchRefUpdate) -> {
      MetaDataUpdate metaDataUpdate =
          metaDataUpdateInternalFactory.create(projectName, repository, batchRefUpdate);
      metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
      PersonIdent authorIdent;
      if (currentUser != null) {
        metaDataUpdate.setAuthor(currentUser);
        authorIdent =
            auditLogFormatter.getParsableAuthorIdent(currentUser.getAccount(), serverIdent);
      } else {
        authorIdent = serverIdent;
      }
      metaDataUpdate.getCommitBuilder().setAuthor(authorIdent);
      return metaDataUpdate;
    };
  }

  private static PersonIdent getAuthorIdent(
      PersonIdent serverIdent, @Nullable IdentifiedUser currentUser) {
    return currentUser != null ? createPersonIdent(serverIdent, currentUser) : serverIdent;
  }

  private static PersonIdent createPersonIdent(PersonIdent ident, IdentifiedUser user) {
    return user.newCommitterIdent(ident.getWhen(), ident.getTimeZone());
  }

  /**
   * Creates the specified group for the specified members (accounts).
   *
   * @param db the {@code ReviewDb} instance to update
   * @param groupCreation an {@code InternalGroupCreation} which specifies all mandatory properties
   *     of the group
   * @param groupUpdate an {@code InternalGroupUpdate} which specifies optional properties of the
   *     group. If this {@code InternalGroupUpdate} updates a property which was already specified
   *     by the {@code InternalGroupCreation}, the value of this {@code InternalGroupUpdate} wins.
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws OrmDuplicateKeyException if a group with the chosen name already exists
   * @throws IOException if indexing fails, or an error occurs while reading/writing from/to NoteDb
   * @return the created {@code InternalGroup}
   */
  public InternalGroup createGroup(
      ReviewDb db, InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws OrmException, IOException, ConfigInvalidException {
    if (!groupsMigration.disableGroupReviewDb()) {
      if (!groupUpdate.getUpdatedOn().isPresent()) {
        // Set updatedOn to a specific value so that the same timestamp is used for ReviewDb and
        // NoteDb.
        groupUpdate = groupUpdate.toBuilder().setUpdatedOn(TimeUtil.nowTs()).build();
      }

      InternalGroup createdGroupInReviewDb =
          createGroupInReviewDb(ReviewDbUtil.unwrapDb(db), groupCreation, groupUpdate);

      if (!groupsMigration.writeToNoteDb()) {
        updateCachesOnGroupCreation(createdGroupInReviewDb);
        dispatchAuditEventsOnGroupCreation(createdGroupInReviewDb);
        return createdGroupInReviewDb;
      }
    }

    InternalGroup createdGroup = createGroupInNoteDbWithRetry(groupCreation, groupUpdate);
    updateCachesOnGroupCreation(createdGroup);
    dispatchAuditEventsOnGroupCreation(createdGroup);
    return createdGroup;
  }

  /**
   * Updates the specified group.
   *
   * @param db the {@code ReviewDb} instance to update
   * @param groupUuid the UUID of the group to update
   * @param groupUpdate an {@code InternalGroupUpdate} which indicates the desired updates on the
   *     group
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws com.google.gwtorm.server.OrmDuplicateKeyException if the new name of the group is used
   *     by another group
   * @throws IOException if indexing fails, or an error occurs while reading/writing from/to NoteDb
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void updateGroup(ReviewDb db, AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws OrmException, IOException, NoSuchGroupException, ConfigInvalidException {
    Optional<Timestamp> updatedOn = groupUpdate.getUpdatedOn();
    if (!updatedOn.isPresent()) {
      // Set updatedOn to a specific value so that the same timestamp is used for ReviewDb and
      // NoteDb. This timestamp is also used by audit events.
      updatedOn = Optional.of(TimeUtil.nowTs());
      groupUpdate = groupUpdate.toBuilder().setUpdatedOn(updatedOn.get()).build();
    }

    UpdateResult result = updateGroupInDb(db, groupUuid, groupUpdate);
    updateCachesOnGroupUpdate(result);
    dispatchAuditEventsOnGroupUpdate(result, updatedOn.get());
  }

  @VisibleForTesting
  public UpdateResult updateGroupInDb(
      ReviewDb db, AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws OrmException, NoSuchGroupException, IOException, ConfigInvalidException {
    UpdateResult reviewDbUpdateResult = null;
    if (!groupsMigration.disableGroupReviewDb()) {
      AccountGroup group = getExistingGroupFromReviewDb(ReviewDbUtil.unwrapDb(db), groupUuid);
      reviewDbUpdateResult = updateGroupInReviewDb(ReviewDbUtil.unwrapDb(db), group, groupUpdate);

      if (!groupsMigration.writeToNoteDb()) {
        return reviewDbUpdateResult;
      }
    }

    Optional<UpdateResult> noteDbUpdateResult =
        updateGroupInNoteDbWithRetry(groupUuid, groupUpdate);
    return noteDbUpdateResult.orElse(reviewDbUpdateResult);
  }

  private InternalGroup createGroupInReviewDb(
      ReviewDb db, InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws OrmException {
    checkIfReviewDbUpdatesAreBlocked();

    AccountGroupName gn = new AccountGroupName(groupCreation.getNameKey(), groupCreation.getId());
    // first insert the group name to validate that the group name hasn't
    // already been used to create another group
    db.accountGroupNames().insert(ImmutableList.of(gn));

    Timestamp createdOn = groupUpdate.getUpdatedOn().orElseGet(TimeUtil::nowTs);
    AccountGroup group = createAccountGroup(groupCreation, createdOn);
    UpdateResult updateResult = updateGroupInReviewDb(db, group, groupUpdate);
    return InternalGroup.create(
        group,
        updateResult.getAddedMembers(),
        updateResult.getAddedSubgroups(),
        updateResult.getRefState());
  }

  public static AccountGroup createAccountGroup(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate) {
    Timestamp createdOn = groupUpdate.getUpdatedOn().orElseGet(TimeUtil::nowTs);
    AccountGroup group = createAccountGroup(groupCreation, createdOn);
    applyUpdate(group, groupUpdate);
    return group;
  }

  private static AccountGroup createAccountGroup(
      InternalGroupCreation groupCreation, Timestamp createdOn) {
    return new AccountGroup(
        groupCreation.getNameKey(), groupCreation.getId(), groupCreation.getGroupUUID(), createdOn);
  }

  private static void applyUpdate(AccountGroup group, InternalGroupUpdate groupUpdate) {
    groupUpdate.getName().ifPresent(group::setNameKey);
    groupUpdate.getDescription().ifPresent(d -> group.setDescription(Strings.emptyToNull(d)));
    groupUpdate.getOwnerGroupUUID().ifPresent(group::setOwnerGroupUUID);
    groupUpdate.getVisibleToAll().ifPresent(group::setVisibleToAll);
  }

  private UpdateResult updateGroupInReviewDb(
      ReviewDb db, AccountGroup group, InternalGroupUpdate groupUpdate) throws OrmException {
    checkIfReviewDbUpdatesAreBlocked();

    AccountGroup.NameKey originalName = group.getNameKey();
    applyUpdate(group, groupUpdate);
    AccountGroup.NameKey updatedName = group.getNameKey();

    // The name must be inserted first so that we stop early for already used names.
    updateNameInReviewDb(db, group.getId(), originalName, updatedName);
    db.accountGroups().upsert(ImmutableList.of(group));

    ImmutableSet<Account.Id> originalMembers =
        Groups.getMembersFromReviewDb(db, group.getId()).collect(toImmutableSet());
    ImmutableSet<Account.Id> updatedMembers =
        ImmutableSet.copyOf(groupUpdate.getMemberModification().apply(originalMembers));
    ImmutableSet<AccountGroup.UUID> originalSubgroups =
        Groups.getSubgroupsFromReviewDb(db, group.getId()).collect(toImmutableSet());
    ImmutableSet<AccountGroup.UUID> updatedSubgroups =
        ImmutableSet.copyOf(groupUpdate.getSubgroupModification().apply(originalSubgroups));

    Set<Account.Id> addedMembers =
        addGroupMembersInReviewDb(db, group.getId(), originalMembers, updatedMembers);
    Set<Account.Id> deletedMembers =
        deleteGroupMembersInReviewDb(db, group.getId(), originalMembers, updatedMembers);
    Set<AccountGroup.UUID> addedSubgroups =
        addSubgroupsInReviewDb(db, group.getId(), originalSubgroups, updatedSubgroups);
    Set<AccountGroup.UUID> deletedSubgroups =
        deleteSubgroupsInReviewDb(db, group.getId(), originalSubgroups, updatedSubgroups);

    UpdateResult.Builder resultBuilder =
        UpdateResult.builder()
            .setGroupUuid(group.getGroupUUID())
            .setGroupId(group.getId())
            .setGroupName(group.getNameKey())
            .setAddedMembers(addedMembers)
            .setDeletedMembers(deletedMembers)
            .setAddedSubgroups(addedSubgroups)
            .setDeletedSubgroups(deletedSubgroups);
    if (!Objects.equals(originalName, updatedName)) {
      resultBuilder.setPreviousGroupName(originalName);
    }
    return resultBuilder.build();
  }

  private static void updateNameInReviewDb(
      ReviewDb db,
      AccountGroup.Id groupId,
      AccountGroup.NameKey originalName,
      AccountGroup.NameKey updatedName)
      throws OrmException {
    try {
      AccountGroupName id = new AccountGroupName(updatedName, groupId);
      db.accountGroupNames().insert(ImmutableList.of(id));
    } catch (OrmException e) {
      AccountGroupName other = db.accountGroupNames().get(updatedName);
      if (other != null) {
        // If we are using this identity, don't report the exception.
        if (other.getId().equals(groupId)) {
          return;
        }
      }
      throw e;
    }
    db.accountGroupNames().deleteKeys(ImmutableList.of(originalName));
  }

  private static Set<Account.Id> addGroupMembersInReviewDb(
      ReviewDb db,
      AccountGroup.Id groupId,
      ImmutableSet<Account.Id> originalMembers,
      ImmutableSet<Account.Id> updatedMembers)
      throws OrmException {
    Set<Account.Id> accountIds = Sets.difference(updatedMembers, originalMembers);
    if (accountIds.isEmpty()) {
      return accountIds;
    }

    ImmutableSet<AccountGroupMember> newMembers = toAccountGroupMembers(groupId, accountIds);
    db.accountGroupMembers().insert(newMembers);
    return accountIds;
  }

  private static Set<Account.Id> deleteGroupMembersInReviewDb(
      ReviewDb db,
      AccountGroup.Id groupId,
      ImmutableSet<Account.Id> originalMembers,
      ImmutableSet<Account.Id> updatedMembers)
      throws OrmException {
    Set<Account.Id> accountIds = Sets.difference(originalMembers, updatedMembers);
    if (accountIds.isEmpty()) {
      return accountIds;
    }

    ImmutableSet<AccountGroupMember> membersToRemove = toAccountGroupMembers(groupId, accountIds);
    db.accountGroupMembers().delete(membersToRemove);
    return accountIds;
  }

  private static ImmutableSet<AccountGroupMember> toAccountGroupMembers(
      AccountGroup.Id groupId, Set<Account.Id> accountIds) {
    return accountIds
        .stream()
        .map(accountId -> new AccountGroupMember.Key(accountId, groupId))
        .map(AccountGroupMember::new)
        .collect(toImmutableSet());
  }

  private static Set<AccountGroup.UUID> addSubgroupsInReviewDb(
      ReviewDb db,
      AccountGroup.Id parentGroupId,
      ImmutableSet<AccountGroup.UUID> originalSubgroups,
      ImmutableSet<AccountGroup.UUID> updatedSubgroups)
      throws OrmException {
    Set<AccountGroup.UUID> subgroupUuids = Sets.difference(updatedSubgroups, originalSubgroups);
    if (subgroupUuids.isEmpty()) {
      return subgroupUuids;
    }

    ImmutableSet<AccountGroupById> newSubgroups = toAccountGroupByIds(parentGroupId, subgroupUuids);
    db.accountGroupById().insert(newSubgroups);
    return subgroupUuids;
  }

  private static Set<AccountGroup.UUID> deleteSubgroupsInReviewDb(
      ReviewDb db,
      AccountGroup.Id parentGroupId,
      ImmutableSet<AccountGroup.UUID> originalSubgroups,
      ImmutableSet<AccountGroup.UUID> updatedSubgroups)
      throws OrmException {
    Set<AccountGroup.UUID> subgroupUuids = Sets.difference(originalSubgroups, updatedSubgroups);
    if (subgroupUuids.isEmpty()) {
      return subgroupUuids;
    }

    ImmutableSet<AccountGroupById> subgroupsToRemove =
        toAccountGroupByIds(parentGroupId, subgroupUuids);
    db.accountGroupById().delete(subgroupsToRemove);
    return subgroupUuids;
  }

  private static ImmutableSet<AccountGroupById> toAccountGroupByIds(
      AccountGroup.Id parentGroupId, Set<AccountGroup.UUID> subgroupUuids) {
    return subgroupUuids
        .stream()
        .map(subgroupUuid -> new AccountGroupById.Key(parentGroupId, subgroupUuid))
        .map(AccountGroupById::new)
        .collect(toImmutableSet());
  }

  private InternalGroup createGroupInNoteDbWithRetry(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, OrmException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.GROUP_UPDATE,
          () -> createGroupInNoteDb(groupCreation, groupUpdate),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      Throwables.throwIfInstanceOf(e, OrmDuplicateKeyException.class);
      throw new IOException(e);
    }
  }

  private InternalGroup createGroupInNoteDb(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      AccountGroup.NameKey groupName = groupUpdate.getName().orElseGet(groupCreation::getNameKey);
      GroupNameNotes groupNameNotes =
          GroupNameNotes.forNewGroup(allUsersRepo, groupCreation.getGroupUUID(), groupName);

      GroupConfig groupConfig = GroupConfig.createForNewGroup(allUsersRepo, groupCreation);
      groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

      commit(allUsersRepo, groupConfig, groupNameNotes);

      return groupConfig
          .getLoadedGroup()
          .orElseThrow(
              () -> new IllegalStateException("Created group wasn't automatically loaded"));
    }
  }

  private Optional<UpdateResult> updateGroupInNoteDbWithRetry(
      AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException, NoSuchGroupException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.GROUP_UPDATE,
          () -> updateGroupInNoteDb(groupUuid, groupUpdate),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      Throwables.throwIfInstanceOf(e, OrmDuplicateKeyException.class);
      Throwables.throwIfInstanceOf(e, NoSuchGroupException.class);
      throw new IOException(e);
    }
  }

  private Optional<UpdateResult> updateGroupInNoteDb(
      AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException, NoSuchGroupException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      GroupConfig groupConfig = GroupConfig.loadForGroup(allUsersRepo, groupUuid);
      groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);
      if (!groupConfig.getLoadedGroup().isPresent()) {
        if (groupsMigration.readFromNoteDb()) {
          throw new NoSuchGroupException(groupUuid);
        }
        return Optional.empty();
      }

      InternalGroup originalGroup = groupConfig.getLoadedGroup().get();
      GroupNameNotes groupNameNotes = null;
      if (groupUpdate.getName().isPresent()) {
        AccountGroup.NameKey oldName = originalGroup.getNameKey();
        AccountGroup.NameKey newName = groupUpdate.getName().get();
        groupNameNotes = GroupNameNotes.forRename(allUsersRepo, groupUuid, oldName, newName);
      }

      commit(allUsersRepo, groupConfig, groupNameNotes);

      InternalGroup updatedGroup =
          groupConfig
              .getLoadedGroup()
              .orElseThrow(
                  () -> new IllegalStateException("Updated group wasn't automatically loaded"));
      return Optional.of(getUpdateResult(originalGroup, updatedGroup));
    }
  }

  private static UpdateResult getUpdateResult(
      InternalGroup originalGroup, InternalGroup updatedGroup) {
    Set<Account.Id> addedMembers =
        Sets.difference(updatedGroup.getMembers(), originalGroup.getMembers());
    Set<Account.Id> deletedMembers =
        Sets.difference(originalGroup.getMembers(), updatedGroup.getMembers());
    Set<AccountGroup.UUID> addedSubgroups =
        Sets.difference(updatedGroup.getSubgroups(), originalGroup.getSubgroups());
    Set<AccountGroup.UUID> deletedSubgroups =
        Sets.difference(originalGroup.getSubgroups(), updatedGroup.getSubgroups());

    UpdateResult.Builder resultBuilder =
        UpdateResult.builder()
            .setGroupUuid(updatedGroup.getGroupUUID())
            .setGroupId(updatedGroup.getId())
            .setGroupName(updatedGroup.getNameKey())
            .setAddedMembers(addedMembers)
            .setDeletedMembers(deletedMembers)
            .setAddedSubgroups(addedSubgroups)
            .setDeletedSubgroups(deletedSubgroups)
            .setRefState(updatedGroup.getRefState());
    if (!Objects.equals(originalGroup.getNameKey(), updatedGroup.getNameKey())) {
      resultBuilder.setPreviousGroupName(originalGroup.getNameKey());
    }
    return resultBuilder.build();
  }

  private void commit(
      Repository allUsersRepo, GroupConfig groupConfig, @Nullable GroupNameNotes groupNameNotes)
      throws IOException {
    BatchRefUpdate batchRefUpdate = allUsersRepo.getRefDatabase().newBatchUpdate();
    try (MetaDataUpdate metaDataUpdate =
        metaDataUpdateFactory.create(allUsersName, allUsersRepo, batchRefUpdate)) {
      groupConfig.commit(metaDataUpdate);
    }
    if (groupNameNotes != null) {
      // MetaDataUpdates unfortunately can't be reused. -> Create a new one.
      try (MetaDataUpdate metaDataUpdate =
          metaDataUpdateFactory.create(allUsersName, allUsersRepo, batchRefUpdate)) {
        groupNameNotes.commit(metaDataUpdate);
      }
    }

    RefUpdateUtil.executeChecked(batchRefUpdate, allUsersRepo);
    gitRefUpdated.fire(
        allUsersName, batchRefUpdate, currentUser != null ? currentUser.state() : null);
  }

  private void updateCachesOnGroupCreation(InternalGroup createdGroup) throws IOException {
    indexer.get().index(createdGroup.getGroupUUID());
    for (Account.Id modifiedMember : createdGroup.getMembers()) {
      groupIncludeCache.evictGroupsWithMember(modifiedMember);
    }
    for (AccountGroup.UUID modifiedSubgroup : createdGroup.getSubgroups()) {
      groupIncludeCache.evictParentGroupsOf(modifiedSubgroup);
    }
  }

  private void updateCachesOnGroupUpdate(UpdateResult result) throws IOException {
    if (result.getPreviousGroupName().isPresent()) {
      AccountGroup.NameKey previousName = result.getPreviousGroupName().get();
      groupCache.evict(previousName);

      // TODO(aliceks): After switching to NoteDb, consider to use a BatchRefUpdate.
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError =
          renameGroupOpFactory
              .create(
                  authorIdent,
                  result.getGroupUuid(),
                  previousName.get(),
                  result.getGroupName().get())
              .start(0, TimeUnit.MILLISECONDS);
    }
    groupCache.evict(result.getGroupUuid());
    groupCache.evict(result.getGroupId());
    groupCache.evict(result.getGroupName());
    indexer.get().index(result.getGroupUuid());

    result.getAddedMembers().forEach(groupIncludeCache::evictGroupsWithMember);
    result.getDeletedMembers().forEach(groupIncludeCache::evictGroupsWithMember);
    result.getAddedSubgroups().forEach(groupIncludeCache::evictParentGroupsOf);
    result.getDeletedSubgroups().forEach(groupIncludeCache::evictParentGroupsOf);
  }

  private void checkIfReviewDbUpdatesAreBlocked() throws OrmException {
    if (reviewDbUpdatesAreBlocked) {
      throw new OrmException("Updates to groups in ReviewDb are blocked");
    }
  }

  private void dispatchAuditEventsOnGroupCreation(InternalGroup createdGroup) {
    if (currentUser == null) {
      return;
    }

    if (!createdGroup.getMembers().isEmpty()) {
      auditService.dispatchAddMembers(
          currentUser.getAccountId(),
          createdGroup.getGroupUUID(),
          createdGroup.getMembers(),
          createdGroup.getCreatedOn());
    }
    if (!createdGroup.getSubgroups().isEmpty()) {
      auditService.dispatchAddSubgroups(
          currentUser.getAccountId(),
          createdGroup.getGroupUUID(),
          createdGroup.getSubgroups(),
          createdGroup.getCreatedOn());
    }
  }

  private void dispatchAuditEventsOnGroupUpdate(UpdateResult result, Timestamp updatedOn) {
    if (currentUser == null) {
      return;
    }

    if (!result.getAddedMembers().isEmpty()) {
      auditService.dispatchAddMembers(
          currentUser.getAccountId(), result.getGroupUuid(), result.getAddedMembers(), updatedOn);
    }
    if (!result.getDeletedMembers().isEmpty()) {
      auditService.dispatchDeleteMembers(
          currentUser.getAccountId(), result.getGroupUuid(), result.getDeletedMembers(), updatedOn);
    }
    if (!result.getAddedSubgroups().isEmpty()) {
      auditService.dispatchAddSubgroups(
          currentUser.getAccountId(), result.getGroupUuid(), result.getAddedSubgroups(), updatedOn);
    }
    if (!result.getDeletedSubgroups().isEmpty()) {
      auditService.dispatchDeleteSubgroups(
          currentUser.getAccountId(),
          result.getGroupUuid(),
          result.getDeletedSubgroups(),
          updatedOn);
    }
  }

  @FunctionalInterface
  private interface MetaDataUpdateFactory {
    MetaDataUpdate create(
        Project.NameKey projectName, Repository repository, BatchRefUpdate batchRefUpdate)
        throws IOException;
  }

  @AutoValue
  abstract static class UpdateResult {
    abstract AccountGroup.UUID getGroupUuid();

    abstract AccountGroup.Id getGroupId();

    abstract AccountGroup.NameKey getGroupName();

    abstract Optional<AccountGroup.NameKey> getPreviousGroupName();

    abstract ImmutableSet<Account.Id> getAddedMembers();

    abstract ImmutableSet<Account.Id> getDeletedMembers();

    abstract ImmutableSet<AccountGroup.UUID> getAddedSubgroups();

    abstract ImmutableSet<AccountGroup.UUID> getDeletedSubgroups();

    @Nullable
    public abstract ObjectId getRefState();

    static Builder builder() {
      return new AutoValue_GroupsUpdate_UpdateResult.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setGroupUuid(AccountGroup.UUID groupUuid);

      abstract Builder setGroupId(AccountGroup.Id groupId);

      abstract Builder setGroupName(AccountGroup.NameKey name);

      abstract Builder setPreviousGroupName(AccountGroup.NameKey previousName);

      abstract Builder setAddedMembers(Set<Account.Id> addedMembers);

      abstract Builder setDeletedMembers(Set<Account.Id> deletedMembers);

      abstract Builder setAddedSubgroups(Set<AccountGroup.UUID> addedSubgroups);

      abstract Builder setDeletedSubgroups(Set<AccountGroup.UUID> deletedSubgroups);

      public abstract Builder setRefState(ObjectId refState);

      abstract UpdateResult build();
    }
  }
}
