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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.GroupAuditService;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/**
 * A database accessor for write calls related to groups.
 *
 * <p>All calls which write group related details to the database are gathered here. Other classes
 * should always use this class instead of accessing the database directly. There are a few
 * exceptions though: schema classes, wrapper classes, and classes executed during init. The latter
 * ones should use {@code GroupsOnInit} instead.
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
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.UserInitiated} annotation on the provider of a {@code GroupsUpdate}
     * instead.
     *
     * @param currentUser the user to which modifications should be attributed
     */
    GroupsUpdate create(IdentifiedUser currentUser);

    /**
     * Creates a {@code GroupsUpdate} which uses the server identity to mark database modifications
     * executed by it. For NoteDb, this identity is used as author and committer for all related
     * commits.
     *
     * <p><strong>Note</strong>: Please use this method with care and consider using the {@link
     * com.google.gerrit.server.ServerInitiated} annotation on the provider of a {@code
     * GroupsUpdate} instead.
     */
    GroupsUpdate createWithServerIdent();
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final GroupCache groupCache;
  private final GroupIncludeCache groupIncludeCache;
  private final Provider<GroupIndexer> indexer;
  private final GroupAuditService groupAuditService;
  private final RenameGroupOp.Factory renameGroupOpFactory;
  private final Optional<IdentifiedUser> currentUser;
  private final AuditLogFormatter auditLogFormatter;
  private final PersonIdent authorIdent;
  private final MetaDataUpdateFactory metaDataUpdateFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final RetryHelper retryHelper;

  @AssistedInject
  GroupsUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      GroupBackend groupBackend,
      GroupCache groupCache,
      GroupIncludeCache groupIncludeCache,
      Provider<GroupIndexer> indexer,
      GroupAuditService auditService,
      AccountCache accountCache,
      RenameGroupOp.Factory renameGroupOpFactory,
      @GerritServerId String serverId,
      @GerritPersonIdent PersonIdent serverIdent,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper) {
    this(
        repoManager,
        allUsersName,
        groupBackend,
        groupCache,
        groupIncludeCache,
        indexer,
        auditService,
        accountCache,
        renameGroupOpFactory,
        serverId,
        serverIdent,
        metaDataUpdateInternalFactory,
        gitRefUpdated,
        retryHelper,
        Optional.empty());
  }

  @AssistedInject
  GroupsUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      GroupBackend groupBackend,
      GroupCache groupCache,
      GroupIncludeCache groupIncludeCache,
      Provider<GroupIndexer> indexer,
      GroupAuditService auditService,
      AccountCache accountCache,
      RenameGroupOp.Factory renameGroupOpFactory,
      @GerritServerId String serverId,
      @GerritPersonIdent PersonIdent serverIdent,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      @Assisted IdentifiedUser currentUser) {
    this(
        repoManager,
        allUsersName,
        groupBackend,
        groupCache,
        groupIncludeCache,
        indexer,
        auditService,
        accountCache,
        renameGroupOpFactory,
        serverId,
        serverIdent,
        metaDataUpdateInternalFactory,
        gitRefUpdated,
        retryHelper,
        Optional.of(currentUser));
  }

  private GroupsUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      GroupBackend groupBackend,
      GroupCache groupCache,
      GroupIncludeCache groupIncludeCache,
      Provider<GroupIndexer> indexer,
      GroupAuditService auditService,
      AccountCache accountCache,
      RenameGroupOp.Factory renameGroupOpFactory,
      @GerritServerId String serverId,
      @GerritPersonIdent PersonIdent serverIdent,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      GitReferenceUpdated gitRefUpdated,
      RetryHelper retryHelper,
      Optional<IdentifiedUser> currentUser) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.groupCache = groupCache;
    this.groupIncludeCache = groupIncludeCache;
    this.indexer = indexer;
    this.groupAuditService = auditService;
    this.renameGroupOpFactory = renameGroupOpFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.retryHelper = retryHelper;
    this.currentUser = currentUser;

    auditLogFormatter = AuditLogFormatter.createBackedBy(accountCache, groupBackend, serverId);
    metaDataUpdateFactory =
        getMetaDataUpdateFactory(
            metaDataUpdateInternalFactory, currentUser, serverIdent, auditLogFormatter);
    authorIdent = getAuthorIdent(serverIdent, currentUser);
  }

  private static MetaDataUpdateFactory getMetaDataUpdateFactory(
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      Optional<IdentifiedUser> currentUser,
      PersonIdent serverIdent,
      AuditLogFormatter auditLogFormatter) {
    return (projectName, repository, batchRefUpdate) -> {
      MetaDataUpdate metaDataUpdate =
          metaDataUpdateInternalFactory.create(projectName, repository, batchRefUpdate);
      metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
      PersonIdent authorIdent;
      if (currentUser.isPresent()) {
        metaDataUpdate.setAuthor(currentUser.get());
        authorIdent =
            auditLogFormatter.getParsableAuthorIdent(currentUser.get().getAccount(), serverIdent);
      } else {
        authorIdent = serverIdent;
      }
      metaDataUpdate.getCommitBuilder().setAuthor(authorIdent);
      return metaDataUpdate;
    };
  }

  private static PersonIdent getAuthorIdent(
      PersonIdent serverIdent, Optional<IdentifiedUser> currentUser) {
    return currentUser.map(user -> createPersonIdent(serverIdent, user)).orElse(serverIdent);
  }

  private static PersonIdent createPersonIdent(PersonIdent ident, IdentifiedUser user) {
    return user.newCommitterIdent(ident.getWhen(), ident.getTimeZone());
  }

  /**
   * Creates the specified group for the specified members (accounts).
   *
   * @param groupCreation an {@code InternalGroupCreation} which specifies all mandatory properties
   *     of the group
   * @param groupUpdate an {@code InternalGroupUpdate} which specifies optional properties of the
   *     group. If this {@code InternalGroupUpdate} updates a property which was already specified
   *     by the {@code InternalGroupCreation}, the value of this {@code InternalGroupUpdate} wins.
   * @throws DuplicateKeyException if a group with the chosen name already exists
   * @throws IOException if indexing fails, or an error occurs while reading/writing from/to NoteDb
   * @return the created {@code InternalGroup}
   */
  public InternalGroup createGroup(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws DuplicateKeyException, IOException, ConfigInvalidException {
    try (TraceTimer timer =
        TraceContext.newTimer(
            "Creating group '%s'", groupUpdate.getName().orElseGet(groupCreation::getNameKey))) {
      InternalGroup createdGroup = createGroupInNoteDbWithRetry(groupCreation, groupUpdate);
      evictCachesOnGroupCreation(createdGroup);
      dispatchAuditEventsOnGroupCreation(createdGroup);
      return createdGroup;
    }
  }

  /**
   * Updates the specified group.
   *
   * @param groupUuid the UUID of the group to update
   * @param groupUpdate an {@code InternalGroupUpdate} which indicates the desired updates on the
   *     group
   * @throws DuplicateKeyException if the new name of the group is used by another group
   * @throws IOException if indexing fails, or an error occurs while reading/writing from/to NoteDb
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void updateGroup(AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws DuplicateKeyException, IOException, NoSuchGroupException, ConfigInvalidException {
    try (TraceTimer timer = TraceContext.newTimer("Updating group %s", groupUuid)) {
      Optional<Timestamp> updatedOn = groupUpdate.getUpdatedOn();
      if (!updatedOn.isPresent()) {
        updatedOn = Optional.of(TimeUtil.nowTs());
        groupUpdate = groupUpdate.toBuilder().setUpdatedOn(updatedOn.get()).build();
      }

      UpdateResult result = updateGroupInNoteDbWithRetry(groupUuid, groupUpdate);
      updateNameInProjectConfigsIfNecessary(result);
      evictCachesOnGroupUpdate(result);
      dispatchAuditEventsOnGroupUpdate(result, updatedOn.get());
    }
  }

  private InternalGroup createGroupInNoteDbWithRetry(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, DuplicateKeyException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.GROUP_UPDATE,
          () -> createGroupInNoteDb(groupCreation, groupUpdate),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      Throwables.throwIfInstanceOf(e, DuplicateKeyException.class);
      throw new IOException(e);
    }
  }

  @VisibleForTesting
  public InternalGroup createGroupInNoteDb(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, DuplicateKeyException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      AccountGroup.NameKey groupName = groupUpdate.getName().orElseGet(groupCreation::getNameKey);
      GroupNameNotes groupNameNotes =
          GroupNameNotes.forNewGroup(
              allUsersName, allUsersRepo, groupCreation.getGroupUUID(), groupName);

      GroupConfig groupConfig =
          GroupConfig.createForNewGroup(allUsersName, allUsersRepo, groupCreation);
      groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

      commit(allUsersRepo, groupConfig, groupNameNotes);

      return groupConfig
          .getLoadedGroup()
          .orElseThrow(
              () -> new IllegalStateException("Created group wasn't automatically loaded"));
    }
  }

  private UpdateResult updateGroupInNoteDbWithRetry(
      AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, DuplicateKeyException, NoSuchGroupException {
    try {
      return retryHelper.execute(
          RetryHelper.ActionType.GROUP_UPDATE,
          () -> updateGroupInNoteDb(groupUuid, groupUpdate),
          LockFailureException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      Throwables.throwIfInstanceOf(e, DuplicateKeyException.class);
      Throwables.throwIfInstanceOf(e, NoSuchGroupException.class);
      throw new IOException(e);
    }
  }

  @VisibleForTesting
  public UpdateResult updateGroupInNoteDb(
      AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, DuplicateKeyException, NoSuchGroupException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      GroupConfig groupConfig = GroupConfig.loadForGroup(allUsersName, allUsersRepo, groupUuid);
      groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);
      if (!groupConfig.getLoadedGroup().isPresent()) {
        throw new NoSuchGroupException(groupUuid);
      }

      InternalGroup originalGroup = groupConfig.getLoadedGroup().get();
      GroupNameNotes groupNameNotes = null;
      if (groupUpdate.getName().isPresent()) {
        AccountGroup.NameKey oldName = originalGroup.getNameKey();
        AccountGroup.NameKey newName = groupUpdate.getName().get();
        groupNameNotes =
            GroupNameNotes.forRename(allUsersName, allUsersRepo, groupUuid, oldName, newName);
      }

      commit(allUsersRepo, groupConfig, groupNameNotes);

      InternalGroup updatedGroup =
          groupConfig
              .getLoadedGroup()
              .orElseThrow(
                  () -> new IllegalStateException("Updated group wasn't automatically loaded"));
      return getUpdateResult(originalGroup, updatedGroup);
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
            .setDeletedSubgroups(deletedSubgroups);
    if (!Objects.equals(originalGroup.getNameKey(), updatedGroup.getNameKey())) {
      resultBuilder.setPreviousGroupName(originalGroup.getNameKey());
    }
    return resultBuilder.build();
  }

  private void commit(
      Repository allUsersRepo, GroupConfig groupConfig, @Nullable GroupNameNotes groupNameNotes)
      throws ConfigInvalidException, LockFailureException {
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

    RefUpdateUtil.execute(batchRefUpdate, allUsersRepo);
    gitRefUpdated.fire(
        allUsersName, batchRefUpdate, currentUser.map(user -> user.state()).orElse(null));
  }

  private void evictCachesOnGroupCreation(InternalGroup createdGroup) {
    logger.atFine().log("evict caches on creation of group %s", createdGroup.getGroupUUID());
    // By UUID is used for the index and hence should be evicted before refreshing the index.
    groupCache.evict(createdGroup.getGroupUUID());
    indexer.get().index(createdGroup.getGroupUUID());
    // These caches use the result from the index and hence must be evicted after refreshing the
    // index.
    groupCache.evict(createdGroup.getId());
    groupCache.evict(createdGroup.getNameKey());
    createdGroup.getMembers().forEach(groupIncludeCache::evictGroupsWithMember);
    createdGroup.getSubgroups().forEach(groupIncludeCache::evictParentGroupsOf);
  }

  private void evictCachesOnGroupUpdate(UpdateResult result) {
    logger.atFine().log("evict caches on update of group %s", result.getGroupUuid());
    // By UUID is used for the index and hence should be evicted before refreshing the index.
    groupCache.evict(result.getGroupUuid());
    indexer.get().index(result.getGroupUuid());
    // These caches use the result from the index and hence must be evicted after refreshing the
    // index.
    groupCache.evict(result.getGroupId());
    groupCache.evict(result.getGroupName());
    result.getPreviousGroupName().ifPresent(groupCache::evict);

    result.getAddedMembers().forEach(groupIncludeCache::evictGroupsWithMember);
    result.getDeletedMembers().forEach(groupIncludeCache::evictGroupsWithMember);
    result.getAddedSubgroups().forEach(groupIncludeCache::evictParentGroupsOf);
    result.getDeletedSubgroups().forEach(groupIncludeCache::evictParentGroupsOf);
  }

  private void updateNameInProjectConfigsIfNecessary(UpdateResult result) {
    if (result.getPreviousGroupName().isPresent()) {
      AccountGroup.NameKey previousName = result.getPreviousGroupName().get();

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
  }

  private void dispatchAuditEventsOnGroupCreation(InternalGroup createdGroup) {
    if (!currentUser.isPresent()) {
      return;
    }

    if (!createdGroup.getMembers().isEmpty()) {
      groupAuditService.dispatchAddMembers(
          currentUser.get().getAccountId(),
          createdGroup.getGroupUUID(),
          createdGroup.getMembers(),
          createdGroup.getCreatedOn());
    }
    if (!createdGroup.getSubgroups().isEmpty()) {
      groupAuditService.dispatchAddSubgroups(
          currentUser.get().getAccountId(),
          createdGroup.getGroupUUID(),
          createdGroup.getSubgroups(),
          createdGroup.getCreatedOn());
    }
  }

  private void dispatchAuditEventsOnGroupUpdate(UpdateResult result, Timestamp updatedOn) {
    if (!currentUser.isPresent()) {
      return;
    }

    if (!result.getAddedMembers().isEmpty()) {
      groupAuditService.dispatchAddMembers(
          currentUser.get().getAccountId(),
          result.getGroupUuid(),
          result.getAddedMembers(),
          updatedOn);
    }
    if (!result.getDeletedMembers().isEmpty()) {
      groupAuditService.dispatchDeleteMembers(
          currentUser.get().getAccountId(),
          result.getGroupUuid(),
          result.getDeletedMembers(),
          updatedOn);
    }
    if (!result.getAddedSubgroups().isEmpty()) {
      groupAuditService.dispatchAddSubgroups(
          currentUser.get().getAccountId(),
          result.getGroupUuid(),
          result.getAddedSubgroups(),
          updatedOn);
    }
    if (!result.getDeletedSubgroups().isEmpty()) {
      groupAuditService.dispatchDeleteSubgroups(
          currentUser.get().getAccountId(),
          result.getGroupUuid(),
          result.getDeletedSubgroups(),
          updatedOn);
    }
  }

  @FunctionalInterface
  private interface MetaDataUpdateFactory {
    MetaDataUpdate create(
        Project.NameKey projectName, Repository repository, BatchRefUpdate batchRefUpdate);
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

      abstract UpdateResult build();
    }
  }
}
