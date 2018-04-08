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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.GerritServerId;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.audit.AuditService;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gwtorm.server.OrmDuplicateKeyException;
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
  private final GitReferenceUpdated gitRefUpdated;
  private final RetryHelper retryHelper;

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
   * @param groupCreation an {@code InternalGroupCreation} which specifies all mandatory properties
   *     of the group
   * @param groupUpdate an {@code InternalGroupUpdate} which specifies optional properties of the
   *     group. If this {@code InternalGroupUpdate} updates a property which was already specified
   *     by the {@code InternalGroupCreation}, the value of this {@code InternalGroupUpdate} wins.
   * @throws OrmDuplicateKeyException if a group with the chosen name already exists
   * @throws IOException if indexing fails, or an error occurs while reading/writing from/to NoteDb
   * @return the created {@code InternalGroup}
   */
  public InternalGroup createGroup(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws OrmDuplicateKeyException, IOException, ConfigInvalidException {
    InternalGroup createdGroup = createGroupInNoteDbWithRetry(groupCreation, groupUpdate);
    updateCachesOnGroupCreation(createdGroup);
    dispatchAuditEventsOnGroupCreation(createdGroup);
    return createdGroup;
  }

  /**
   * Updates the specified group.
   *
   * @param groupUuid the UUID of the group to update
   * @param groupUpdate an {@code InternalGroupUpdate} which indicates the desired updates on the
   *     group
   * @throws OrmDuplicateKeyException if the new name of the group is used by another group
   * @throws IOException if indexing fails, or an error occurs while reading/writing from/to NoteDb
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void updateGroup(AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws OrmDuplicateKeyException, IOException, NoSuchGroupException, ConfigInvalidException {
    Optional<Timestamp> updatedOn = groupUpdate.getUpdatedOn();
    if (!updatedOn.isPresent()) {
      updatedOn = Optional.of(TimeUtil.nowTs());
      groupUpdate = groupUpdate.toBuilder().setUpdatedOn(updatedOn.get()).build();
    }

    UpdateResult result = updateGroupInNoteDbWithRetry(groupUuid, groupUpdate);
    updateCachesOnGroupUpdate(result);
    dispatchAuditEventsOnGroupUpdate(result, updatedOn.get());
  }

  private InternalGroup createGroupInNoteDbWithRetry(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
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

  @VisibleForTesting
  public InternalGroup createGroupInNoteDb(
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

  private UpdateResult updateGroupInNoteDbWithRetry(
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

  @VisibleForTesting
  public UpdateResult updateGroupInNoteDb(
      AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException, NoSuchGroupException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      GroupConfig groupConfig = GroupConfig.loadForGroup(allUsersRepo, groupUuid);
      groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);
      if (!groupConfig.getLoadedGroup().isPresent()) {
        throw new NoSuchGroupException(groupUuid);
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
