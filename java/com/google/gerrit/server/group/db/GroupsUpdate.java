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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupDescription;
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
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.audit.AuditService;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.RenameGroupOp;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.notedb.GroupsMigration;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
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
  private final GroupBackend groupBackend;
  private final GroupCache groupCache;
  private final GroupIncludeCache groupIncludeCache;
  private final AuditService auditService;
  private final AccountCache accountCache;
  private final RenameGroupOp.Factory renameGroupOpFactory;
  private final String serverId;
  @Nullable private final IdentifiedUser currentUser;
  private final PersonIdent authorIdent;
  private final MetaDataUpdateFactory metaDataUpdateFactory;
  private final GroupsMigration groupsMigration;
  private final GitReferenceUpdated gitRefUpdated;
  private final boolean reviewDbUpdatesAreBlocked;

  @Inject
  GroupsUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      GroupBackend groupBackend,
      GroupCache groupCache,
      GroupIncludeCache groupIncludeCache,
      AuditService auditService,
      AccountCache accountCache,
      RenameGroupOp.Factory renameGroupOpFactory,
      @GerritServerId String serverId,
      @GerritPersonIdent PersonIdent serverIdent,
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      GroupsMigration groupsMigration,
      @GerritServerConfig Config config,
      GitReferenceUpdated gitRefUpdated,
      @Assisted @Nullable IdentifiedUser currentUser) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.groupBackend = groupBackend;
    this.groupCache = groupCache;
    this.groupIncludeCache = groupIncludeCache;
    this.auditService = auditService;
    this.accountCache = accountCache;
    this.renameGroupOpFactory = renameGroupOpFactory;
    this.serverId = serverId;
    this.groupsMigration = groupsMigration;
    this.gitRefUpdated = gitRefUpdated;
    this.currentUser = currentUser;
    metaDataUpdateFactory =
        getMetaDataUpdateFactory(metaDataUpdateInternalFactory, currentUser, serverIdent, serverId);
    authorIdent = getAuthorIdent(serverIdent, currentUser);
    reviewDbUpdatesAreBlocked = config.getBoolean("user", null, "blockReviewDbGroupUpdates", false);
  }

  private static MetaDataUpdateFactory getMetaDataUpdateFactory(
      MetaDataUpdate.InternalFactory metaDataUpdateInternalFactory,
      @Nullable IdentifiedUser currentUser,
      PersonIdent serverIdent,
      String serverId) {
    return (projectName, repository, batchRefUpdate) -> {
      MetaDataUpdate metaDataUpdate =
          metaDataUpdateInternalFactory.create(projectName, repository, batchRefUpdate);
      metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
      PersonIdent authorIdent;
      if (currentUser != null) {
        metaDataUpdate.setAuthor(currentUser);
        authorIdent = getAuditLogAuthorIdent(currentUser.getAccount(), serverIdent, serverId);
      } else {
        authorIdent = serverIdent;
      }
      metaDataUpdate.getCommitBuilder().setAuthor(authorIdent);
      return metaDataUpdate;
    };
  }

  private static PersonIdent getAuditLogAuthorIdent(
      Account author, PersonIdent serverIdent, String serverId) {
    return new PersonIdent(
        author.getName(),
        getEmailForAuditLog(author.getId(), serverId),
        serverIdent.getWhen(),
        serverIdent.getTimeZone());
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
        return createdGroupInReviewDb;
      }
    }

    // TODO(aliceks): Add retry mechanism.
    InternalGroup createdGroup = createGroupInNoteDb(groupCreation, groupUpdate);
    updateCachesOnGroupCreation(createdGroup);
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
    UpdateResult result = updateGroupInDb(db, groupUuid, groupUpdate);
    updateCachesOnGroupUpdate(result);
  }

  @VisibleForTesting
  public UpdateResult updateGroupInDb(
      ReviewDb db, AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws OrmException, NoSuchGroupException, IOException, ConfigInvalidException {
    UpdateResult reviewDbUpdateResult = null;
    if (!groupsMigration.disableGroupReviewDb()) {
      if (!groupUpdate.getUpdatedOn().isPresent()) {
        // Set updatedOn to a specific value so that the same timestamp is used for ReviewDb and
        // NoteDb.
        groupUpdate = groupUpdate.toBuilder().setUpdatedOn(TimeUtil.nowTs()).build();
      }

      AccountGroup group = getExistingGroupFromReviewDb(ReviewDbUtil.unwrapDb(db), groupUuid);
      reviewDbUpdateResult = updateGroupInReviewDb(ReviewDbUtil.unwrapDb(db), group, groupUpdate);

      if (!groupsMigration.writeToNoteDb()) {
        return reviewDbUpdateResult;
      }
    }

    // TODO(aliceks): Add retry mechanism.
    Optional<UpdateResult> noteDbUpdateResult = updateGroupInNoteDb(groupUuid, groupUpdate);
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
        updateResult.getModifiedMembers(),
        updateResult.getModifiedSubgroups(),
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
    ImmutableSet<Account.Id> modifiedMembers =
        updateMembersInReviewDb(db, group.getId(), groupUpdate);
    ImmutableSet<AccountGroup.UUID> modifiedSubgroups =
        updateSubgroupsInReviewDb(db, group.getId(), groupUpdate);

    UpdateResult.Builder resultBuilder =
        UpdateResult.builder()
            .setGroupUuid(group.getGroupUUID())
            .setGroupId(group.getId())
            .setGroupName(group.getNameKey())
            .setModifiedMembers(modifiedMembers)
            .setModifiedSubgroups(modifiedSubgroups);
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

  private ImmutableSet<Account.Id> updateMembersInReviewDb(
      ReviewDb db, AccountGroup.Id groupId, InternalGroupUpdate groupUpdate) throws OrmException {
    Timestamp updatedOn = groupUpdate.getUpdatedOn().orElseGet(TimeUtil::nowTs);
    ImmutableSet<Account.Id> originalMembers =
        Groups.getMembersFromReviewDb(db, groupId).collect(toImmutableSet());
    ImmutableSet<Account.Id> updatedMembers =
        ImmutableSet.copyOf(groupUpdate.getMemberModification().apply(originalMembers));

    Set<Account.Id> addedMembers = Sets.difference(updatedMembers, originalMembers);
    if (!addedMembers.isEmpty()) {
      addGroupMembersInReviewDb(db, groupId, addedMembers, updatedOn);
    }

    Set<Account.Id> removedMembers = Sets.difference(originalMembers, updatedMembers);
    if (!removedMembers.isEmpty()) {
      removeGroupMembersInReviewDb(db, groupId, removedMembers, updatedOn);
    }

    return Sets.union(addedMembers, removedMembers).immutableCopy();
  }

  private void addGroupMembersInReviewDb(
      ReviewDb db, AccountGroup.Id groupId, Set<Account.Id> newMemberIds, Timestamp addedOn)
      throws OrmException {
    Set<AccountGroupMember> newMembers =
        newMemberIds
            .stream()
            .map(accountId -> new AccountGroupMember.Key(accountId, groupId))
            .map(AccountGroupMember::new)
            .collect(toImmutableSet());

    if (currentUser != null) {
      auditService.dispatchAddAccountsToGroup(currentUser.getAccountId(), newMembers, addedOn);
    }
    db.accountGroupMembers().insert(newMembers);
  }

  private void removeGroupMembersInReviewDb(
      ReviewDb db, AccountGroup.Id groupId, Set<Account.Id> accountIds, Timestamp removedOn)
      throws OrmException {
    Set<AccountGroupMember> membersToRemove =
        accountIds
            .stream()
            .map(accountId -> new AccountGroupMember.Key(accountId, groupId))
            .map(AccountGroupMember::new)
            .collect(toImmutableSet());

    if (currentUser != null) {
      auditService.dispatchDeleteAccountsFromGroup(
          currentUser.getAccountId(), membersToRemove, removedOn);
    }
    db.accountGroupMembers().delete(membersToRemove);
  }

  private ImmutableSet<AccountGroup.UUID> updateSubgroupsInReviewDb(
      ReviewDb db, AccountGroup.Id groupId, InternalGroupUpdate groupUpdate) throws OrmException {
    Timestamp updatedOn = groupUpdate.getUpdatedOn().orElseGet(TimeUtil::nowTs);
    ImmutableSet<AccountGroup.UUID> originalSubgroups =
        Groups.getSubgroupsFromReviewDb(db, groupId).collect(toImmutableSet());
    ImmutableSet<AccountGroup.UUID> updatedSubgroups =
        ImmutableSet.copyOf(groupUpdate.getSubgroupModification().apply(originalSubgroups));

    Set<AccountGroup.UUID> addedSubgroups = Sets.difference(updatedSubgroups, originalSubgroups);
    if (!addedSubgroups.isEmpty()) {
      addSubgroupsInReviewDb(db, groupId, addedSubgroups, updatedOn);
    }

    Set<AccountGroup.UUID> removedSubgroups = Sets.difference(originalSubgroups, updatedSubgroups);
    if (!removedSubgroups.isEmpty()) {
      removeSubgroupsInReviewDb(db, groupId, removedSubgroups, updatedOn);
    }

    return Sets.union(addedSubgroups, removedSubgroups).immutableCopy();
  }

  private void addSubgroupsInReviewDb(
      ReviewDb db,
      AccountGroup.Id parentGroupId,
      Set<AccountGroup.UUID> subgroupUuids,
      Timestamp addedOn)
      throws OrmException {
    Set<AccountGroupById> newSubgroups =
        subgroupUuids
            .stream()
            .map(subgroupUuid -> new AccountGroupById.Key(parentGroupId, subgroupUuid))
            .map(AccountGroupById::new)
            .collect(toImmutableSet());

    if (currentUser != null) {
      auditService.dispatchAddGroupsToGroup(currentUser.getAccountId(), newSubgroups, addedOn);
    }
    db.accountGroupById().insert(newSubgroups);
  }

  private void removeSubgroupsInReviewDb(
      ReviewDb db,
      AccountGroup.Id parentGroupId,
      Set<AccountGroup.UUID> subgroupUuids,
      Timestamp removedOn)
      throws OrmException {
    Set<AccountGroupById> subgroupsToRemove =
        subgroupUuids
            .stream()
            .map(subgroupUuid -> new AccountGroupById.Key(parentGroupId, subgroupUuid))
            .map(AccountGroupById::new)
            .collect(toImmutableSet());

    if (currentUser != null) {
      auditService.dispatchDeleteGroupsFromGroup(
          currentUser.getAccountId(), subgroupsToRemove, removedOn);
    }
    db.accountGroupById().delete(subgroupsToRemove);
  }

  private InternalGroup createGroupInNoteDb(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, OrmException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      AccountGroup.NameKey groupName = groupUpdate.getName().orElseGet(groupCreation::getNameKey);
      GroupNameNotes groupNameNotes =
          GroupNameNotes.loadForNewGroup(allUsersRepo, groupCreation.getGroupUUID(), groupName);

      GroupConfig groupConfig = GroupConfig.createForNewGroup(allUsersRepo, groupCreation);
      groupConfig.setGroupUpdate(groupUpdate, this::getAccountNameEmail, this::getGroupName);

      commit(allUsersRepo, groupConfig, groupNameNotes);

      return groupConfig
          .getLoadedGroup()
          .orElseThrow(
              () -> new IllegalStateException("Created group wasn't automatically loaded"));
    }
  }

  private Optional<UpdateResult> updateGroupInNoteDb(
      AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException, NoSuchGroupException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      GroupConfig groupConfig = GroupConfig.loadForGroup(allUsersRepo, groupUuid);
      groupConfig.setGroupUpdate(groupUpdate, this::getAccountNameEmail, this::getGroupName);
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
        groupNameNotes = GroupNameNotes.loadForRename(allUsersRepo, groupUuid, oldName, newName);
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
    Set<Account.Id> modifiedMembers =
        Sets.symmetricDifference(originalGroup.getMembers(), updatedGroup.getMembers());
    Set<AccountGroup.UUID> modifiedSubgroups =
        Sets.symmetricDifference(originalGroup.getSubgroups(), updatedGroup.getSubgroups());

    UpdateResult.Builder resultBuilder =
        UpdateResult.builder()
            .setGroupUuid(updatedGroup.getGroupUUID())
            .setGroupId(updatedGroup.getId())
            .setGroupName(updatedGroup.getNameKey())
            .setModifiedMembers(modifiedMembers)
            .setModifiedSubgroups(modifiedSubgroups)
            .setRefState(updatedGroup.getRefState());
    if (!Objects.equals(originalGroup.getNameKey(), updatedGroup.getNameKey())) {
      resultBuilder.setPreviousGroupName(originalGroup.getNameKey());
    }
    return resultBuilder.build();
  }

  static String getAccountName(AccountCache accountCache, Account.Id accountId) {
    AccountState accountState = accountCache.getOrNull(accountId);
    return Optional.ofNullable(accountState)
        .map(AccountState::getAccount)
        .map(account -> account.getName())
        // Historically, the database did not enforce relational integrity, so it is
        // possible for groups to have non-existing members.
        .orElse("No Account for Id #" + accountId);
  }

  static String getAccountNameEmail(
      AccountCache accountCache, Account.Id accountId, String serverId) {
    String accountName = getAccountName(accountCache, accountId);
    return formatNameEmail(accountName, getEmailForAuditLog(accountId, serverId));
  }

  static String getEmailForAuditLog(Account.Id accountId, String serverId) {
    return accountId.get() + "@" + serverId;
  }

  private String getAccountNameEmail(Account.Id accountId) {
    return getAccountNameEmail(accountCache, accountId, serverId);
  }

  static String getGroupName(GroupBackend groupBackend, AccountGroup.UUID groupUuid) {
    String uuid = groupUuid.get();
    GroupDescription.Basic desc = groupBackend.get(groupUuid);
    String name = desc != null ? desc.getName() : uuid;
    return formatNameEmail(name, uuid);
  }

  private String getGroupName(AccountGroup.UUID groupUuid) {
    return getGroupName(groupBackend, groupUuid);
  }

  private static String formatNameEmail(String name, String email) {
    StringBuilder formattedResult = new StringBuilder();
    PersonIdent.appendSanitized(formattedResult, name);
    formattedResult.append(" <");
    PersonIdent.appendSanitized(formattedResult, email);
    formattedResult.append(">");
    return formattedResult.toString();
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
        allUsersName, batchRefUpdate, currentUser != null ? currentUser.getAccount() : null);
  }

  private void updateCachesOnGroupCreation(InternalGroup createdGroup) throws IOException {
    groupCache.onCreateGroup(createdGroup.getGroupUUID());
    for (Account.Id modifiedMember : createdGroup.getMembers()) {
      accountCache.evict(modifiedMember);
    }
    for (AccountGroup.UUID modifiedSubgroup : createdGroup.getSubgroups()) {
      groupIncludeCache.evictParentGroupsOf(modifiedSubgroup);
    }
  }

  private void updateCachesOnGroupUpdate(UpdateResult result) throws IOException {
    if (result.getPreviousGroupName().isPresent()) {
      AccountGroup.NameKey previousName = result.getPreviousGroupName().get();
      groupCache.evictAfterRename(previousName);

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
    groupCache.evict(result.getGroupUuid(), result.getGroupId(), result.getGroupName());
    for (Account.Id modifiedMember : result.getModifiedMembers()) {
      groupIncludeCache.evictGroupsWithMember(modifiedMember);
    }
    for (AccountGroup.UUID modifiedSubgroup : result.getModifiedSubgroups()) {
      groupIncludeCache.evictParentGroupsOf(modifiedSubgroup);
    }
  }

  private void checkIfReviewDbUpdatesAreBlocked() throws OrmException {
    if (reviewDbUpdatesAreBlocked) {
      throw new OrmException("Updates to groups in ReviewDb are blocked");
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

    abstract ImmutableSet<Account.Id> getModifiedMembers();

    abstract ImmutableSet<AccountGroup.UUID> getModifiedSubgroups();

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

      abstract Builder setModifiedMembers(Set<Account.Id> modifiedMembers);

      abstract Builder setModifiedSubgroups(Set<AccountGroup.UUID> modifiedSubgroups);

      public abstract Builder setRefState(ObjectId refState);

      abstract UpdateResult build();
    }
  }
}
