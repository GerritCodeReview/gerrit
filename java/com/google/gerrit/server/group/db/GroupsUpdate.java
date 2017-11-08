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
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupById;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.account.GroupIncludeCache;
import com.google.gerrit.server.audit.AuditService;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.RenameGroupOp;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
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
  private final AuditService auditService;
  private final AccountCache accountCache;
  private final String anonymousCowardName;
  private final RenameGroupOp.Factory renameGroupOpFactory;
  private final String serverId;
  @Nullable private final IdentifiedUser currentUser;
  private final PersonIdent authorIdent;
  private final MetaDataUpdateFactory metaDataUpdateFactory;
  private final boolean writeGroupsToNoteDb;

  @Inject
  GroupsUpdate(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      GroupCache groupCache,
      GroupIncludeCache groupIncludeCache,
      AuditService auditService,
      AccountCache accountCache,
      @AnonymousCowardName String anonymousCowardName,
      RenameGroupOp.Factory renameGroupOpFactory,
      @GerritServerId String serverId,
      @GerritPersonIdent PersonIdent serverIdent,
      MetaDataUpdate.User metaDataUpdateUserFactory,
      MetaDataUpdate.Server metaDataUpdateServerFactory,
      @GerritServerConfig Config config,
      @Assisted @Nullable IdentifiedUser currentUser) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.groupCache = groupCache;
    this.groupIncludeCache = groupIncludeCache;
    this.auditService = auditService;
    this.accountCache = accountCache;
    this.anonymousCowardName = anonymousCowardName;
    this.renameGroupOpFactory = renameGroupOpFactory;
    this.serverId = serverId;
    this.currentUser = currentUser;
    metaDataUpdateFactory =
        getMetaDataUpdateFactory(
            metaDataUpdateUserFactory,
            metaDataUpdateServerFactory,
            currentUser,
            serverIdent,
            serverId,
            anonymousCowardName);
    authorIdent = getAuthorIdent(serverIdent, currentUser);
    // TODO(aliceks): Remove this flag when all other necessary TODOs for writing groups to NoteDb
    // have been addressed.
    // Don't flip this flag in a production setting! We only added it to spread the implementation
    // of groups in NoteDb among several changes which are gradually merged.
    writeGroupsToNoteDb = config.getBoolean("user", null, "writeGroupsToNoteDb", false);
  }

  // TODO(aliceks): Introduce a common class for MetaDataUpdate.User and MetaDataUpdate.Server which
  // doesn't require this ugly code. In addition, allow to pass in the repository and to use another
  // author ident.
  private static MetaDataUpdateFactory getMetaDataUpdateFactory(
      MetaDataUpdate.User metaDataUpdateUserFactory,
      MetaDataUpdate.Server metaDataUpdateServerFactory,
      @Nullable IdentifiedUser currentUser,
      PersonIdent serverIdent,
      String serverId,
      String anonymousCowardName) {
    return currentUser != null
        ? projectName -> {
          MetaDataUpdate metaDataUpdate =
              metaDataUpdateUserFactory.create(projectName, currentUser);
          PersonIdent authorIdent =
              getAuditLogAuthorIdent(
                  currentUser.getAccount(), serverIdent, serverId, anonymousCowardName);
          metaDataUpdate.getCommitBuilder().setAuthor(authorIdent);
          return metaDataUpdate;
        }
        : metaDataUpdateServerFactory::create;
  }

  private static PersonIdent getAuditLogAuthorIdent(
      Account author, PersonIdent serverIdent, String serverId, String anonymousCowardName) {
    return new PersonIdent(
        author.getName(anonymousCowardName),
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
    InternalGroup createdGroupInReviewDb = createGroupInReviewDb(db, groupCreation, groupUpdate);

    if (!writeGroupsToNoteDb) {
      updateCachesOnGroupCreation(createdGroupInReviewDb);
      return createdGroupInReviewDb;
    }

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
    AccountGroup group = getExistingGroupFromReviewDb(db, groupUuid);
    UpdateResult reviewDbUpdateResult = updateGroupInReviewDb(db, group, groupUpdate);

    if (!writeGroupsToNoteDb) {
      return reviewDbUpdateResult;
    }

    Optional<UpdateResult> noteDbUpdateResult = updateGroupInNoteDb(groupUuid, groupUpdate);
    return noteDbUpdateResult.orElse(reviewDbUpdateResult);
  }

  private InternalGroup createGroupInReviewDb(
      ReviewDb db, InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws OrmException {

    AccountGroupName gn = new AccountGroupName(groupCreation.getNameKey(), groupCreation.getId());
    // first insert the group name to validate that the group name hasn't
    // already been used to create another group
    db.accountGroupNames().insert(ImmutableList.of(gn));

    AccountGroup group = createAccountGroup(groupCreation);
    UpdateResult updateResult = updateGroupInReviewDb(db, group, groupUpdate);
    return InternalGroup.create(
        group, updateResult.getModifiedMembers(), updateResult.getModifiedSubgroups());
  }

  public static AccountGroup createAccountGroup(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate) {
    AccountGroup group = createAccountGroup(groupCreation);
    applyUpdate(group, groupUpdate);
    return group;
  }

  private static AccountGroup createAccountGroup(InternalGroupCreation groupCreation) {
    return new AccountGroup(
        groupCreation.getNameKey(),
        groupCreation.getId(),
        groupCreation.getGroupUUID(),
        groupCreation.getCreatedOn());
  }

  private static void applyUpdate(AccountGroup group, InternalGroupUpdate groupUpdate) {
    groupUpdate.getName().ifPresent(group::setNameKey);
    groupUpdate.getDescription().ifPresent(d -> group.setDescription(Strings.emptyToNull(d)));
    groupUpdate.getOwnerGroupUUID().ifPresent(group::setOwnerGroupUUID);
    groupUpdate.getVisibleToAll().ifPresent(group::setVisibleToAll);
  }

  private UpdateResult updateGroupInReviewDb(
      ReviewDb db, AccountGroup group, InternalGroupUpdate groupUpdate) throws OrmException {
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
    ImmutableSet<Account.Id> originalMembers =
        Groups.getMembersFromReviewDb(db, groupId).collect(toImmutableSet());
    ImmutableSet<Account.Id> updatedMembers =
        ImmutableSet.copyOf(groupUpdate.getMemberModification().apply(originalMembers));

    Set<Account.Id> addedMembers = Sets.difference(updatedMembers, originalMembers);
    if (!addedMembers.isEmpty()) {
      addGroupMembersInReviewDb(db, groupId, addedMembers);
    }

    Set<Account.Id> removedMembers = Sets.difference(originalMembers, updatedMembers);
    if (!removedMembers.isEmpty()) {
      removeGroupMembersInReviewDb(db, groupId, removedMembers);
    }

    return Sets.union(addedMembers, removedMembers).immutableCopy();
  }

  private void addGroupMembersInReviewDb(
      ReviewDb db, AccountGroup.Id groupId, Set<Account.Id> newMemberIds) throws OrmException {
    Set<AccountGroupMember> newMembers =
        newMemberIds
            .stream()
            .map(accountId -> new AccountGroupMember.Key(accountId, groupId))
            .map(AccountGroupMember::new)
            .collect(toImmutableSet());

    if (currentUser != null) {
      auditService.dispatchAddAccountsToGroup(currentUser.getAccountId(), newMembers);
    }
    db.accountGroupMembers().insert(newMembers);
  }

  private void removeGroupMembersInReviewDb(
      ReviewDb db, AccountGroup.Id groupId, Set<Account.Id> accountIds) throws OrmException {
    Set<AccountGroupMember> membersToRemove =
        accountIds
            .stream()
            .map(accountId -> new AccountGroupMember.Key(accountId, groupId))
            .map(AccountGroupMember::new)
            .collect(toImmutableSet());

    if (currentUser != null) {
      auditService.dispatchDeleteAccountsFromGroup(currentUser.getAccountId(), membersToRemove);
    }
    db.accountGroupMembers().delete(membersToRemove);
  }

  private ImmutableSet<AccountGroup.UUID> updateSubgroupsInReviewDb(
      ReviewDb db, AccountGroup.Id groupId, InternalGroupUpdate groupUpdate) throws OrmException {
    ImmutableSet<AccountGroup.UUID> originalSubgroups =
        Groups.getSubgroupsFromReviewDb(db, groupId).collect(toImmutableSet());
    ImmutableSet<AccountGroup.UUID> updatedSubgroups =
        ImmutableSet.copyOf(groupUpdate.getSubgroupModification().apply(originalSubgroups));

    Set<AccountGroup.UUID> addedSubgroups = Sets.difference(updatedSubgroups, originalSubgroups);
    if (!addedSubgroups.isEmpty()) {
      addSubgroupsInReviewDb(db, groupId, addedSubgroups);
    }

    Set<AccountGroup.UUID> removedSubgroups = Sets.difference(originalSubgroups, updatedSubgroups);
    if (!removedSubgroups.isEmpty()) {
      removeSubgroupsInReviewDb(db, groupId, removedSubgroups);
    }

    return Sets.union(addedSubgroups, removedSubgroups).immutableCopy();
  }

  private void addSubgroupsInReviewDb(
      ReviewDb db, AccountGroup.Id parentGroupId, Set<AccountGroup.UUID> subgroupUuids)
      throws OrmException {
    Set<AccountGroupById> newSubgroups =
        subgroupUuids
            .stream()
            .map(subgroupUuid -> new AccountGroupById.Key(parentGroupId, subgroupUuid))
            .map(AccountGroupById::new)
            .collect(toImmutableSet());

    if (currentUser != null) {
      auditService.dispatchAddGroupsToGroup(currentUser.getAccountId(), newSubgroups);
    }
    db.accountGroupById().insert(newSubgroups);
  }

  private void removeSubgroupsInReviewDb(
      ReviewDb db, AccountGroup.Id parentGroupId, Set<AccountGroup.UUID> subgroupUuids)
      throws OrmException {
    Set<AccountGroupById> subgroupsToRemove =
        subgroupUuids
            .stream()
            .map(subgroupUuid -> new AccountGroupById.Key(parentGroupId, subgroupUuid))
            .map(AccountGroupById::new)
            .collect(toImmutableSet());

    if (currentUser != null) {
      auditService.dispatchDeleteGroupsFromGroup(currentUser.getAccountId(), subgroupsToRemove);
    }
    db.accountGroupById().delete(subgroupsToRemove);
  }

  private InternalGroup createGroupInNoteDb(
      InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    GroupConfig groupConfig = createFor(groupCreation);
    updateGroupInNoteDb(groupConfig, groupUpdate);
    return groupConfig
        .getLoadedGroup()
        .orElseThrow(() -> new IllegalStateException("Created group wasn't automatically loaded"));
  }

  private GroupConfig createFor(InternalGroupCreation groupCreation)
      throws IOException, ConfigInvalidException, OrmDuplicateKeyException {
    try (Repository repository = repoManager.openRepository(allUsersName)) {
      return GroupConfig.createForNewGroup(repository, groupCreation);
    }
  }

  private Optional<UpdateResult> updateGroupInNoteDb(
      AccountGroup.UUID groupUuid, InternalGroupUpdate groupUpdate)
      throws IOException, ConfigInvalidException {
    GroupConfig groupConfig = loadFor(groupUuid);
    if (!groupConfig.getLoadedGroup().isPresent()) {
      // TODO(aliceks): Throw a NoSuchGroupException here when all groups are stored in NoteDb.
      return Optional.empty();
    }

    return updateGroupInNoteDb(groupConfig, groupUpdate);
  }

  private GroupConfig loadFor(AccountGroup.UUID groupUuid)
      throws IOException, ConfigInvalidException {
    try (Repository repository = repoManager.openRepository(allUsersName)) {
      return GroupConfig.loadForGroup(repository, groupUuid);
    }
  }

  private Optional<UpdateResult> updateGroupInNoteDb(
      GroupConfig groupConfig, InternalGroupUpdate groupUpdate) throws IOException {
    Optional<InternalGroup> originalGroup = groupConfig.getLoadedGroup();

    // TODO(aliceks): Find a way to ensure unique names with NoteDb.
    groupConfig.setGroupUpdate(groupUpdate, this::getAccountNameEmail, this::getGroupName);
    commit(groupConfig);
    InternalGroup updatedGroup =
        groupConfig
            .getLoadedGroup()
            .orElseThrow(
                () -> new IllegalStateException("Updated group wasn't automatically loaded"));

    Set<Account.Id> modifiedMembers = getModifiedMembers(originalGroup, updatedGroup);
    Set<AccountGroup.UUID> modifiedSubgroups = getModifiedSubgroups(originalGroup, updatedGroup);
    Optional<AccountGroup.NameKey> previousName =
        getPreviousNameIfModified(originalGroup, updatedGroup);

    UpdateResult.Builder resultBuilder =
        UpdateResult.builder()
            .setGroupUuid(updatedGroup.getGroupUUID())
            .setGroupId(updatedGroup.getId())
            .setGroupName(updatedGroup.getNameKey())
            .setModifiedMembers(modifiedMembers)
            .setModifiedSubgroups(modifiedSubgroups);
    previousName.ifPresent(resultBuilder::setPreviousGroupName);
    return Optional.of(resultBuilder.build());
  }

  private String getAccountNameEmail(Account.Id accountId) {
    AccountState accountState = accountCache.getOrNull(accountId);
    String accountName =
        Optional.ofNullable(accountState)
            .map(AccountState::getAccount)
            .map(account -> account.getName(anonymousCowardName))
            .orElse(anonymousCowardName);
    String email = getEmailForAuditLog(accountId, serverId);

    StringBuilder formattedResult = new StringBuilder();
    PersonIdent.appendSanitized(formattedResult, accountName);
    formattedResult.append(" <");
    PersonIdent.appendSanitized(formattedResult, email);
    formattedResult.append(">");
    return formattedResult.toString();
  }

  private static String getEmailForAuditLog(Account.Id accountId, String serverId) {
    return accountId.get() + "@" + serverId;
  }

  private String getGroupName(AccountGroup.UUID groupUuid) {
    return groupCache
        .get(groupUuid)
        .map(InternalGroup::getName)
        .map(name -> String.format("%s <%s>", name, groupUuid))
        .orElse(groupUuid.get());
  }

  private void commit(GroupConfig groupConfig) throws IOException {
    try (MetaDataUpdate metaDataUpdate = metaDataUpdateFactory.create(allUsersName)) {
      groupConfig.commit(metaDataUpdate);
    }
  }

  private static Set<Account.Id> getModifiedMembers(
      Optional<InternalGroup> originalGroup, InternalGroup updatedGroup) {
    ImmutableSet<Account.Id> originalMembers =
        originalGroup.map(InternalGroup::getMembers).orElseGet(ImmutableSet::of);
    return Sets.symmetricDifference(originalMembers, updatedGroup.getMembers());
  }

  private static Set<AccountGroup.UUID> getModifiedSubgroups(
      Optional<InternalGroup> originalGroup, InternalGroup updatedGroup) {
    ImmutableSet<AccountGroup.UUID> originalSubgroups =
        originalGroup.map(InternalGroup::getSubgroups).orElseGet(ImmutableSet::of);
    return Sets.symmetricDifference(originalSubgroups, updatedGroup.getSubgroups());
  }

  private static Optional<AccountGroup.NameKey> getPreviousNameIfModified(
      Optional<InternalGroup> originalGroup, InternalGroup updatedGroup) {
    return originalGroup
        .map(InternalGroup::getNameKey)
        .filter(originalName -> !Objects.equals(originalName, updatedGroup.getNameKey()));
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
      throws OrmException, IOException, NoSuchGroupException, ConfigInvalidException {
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
   * @throws IOException if the group or one of the new members couldn't be indexed
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void addGroupMembers(ReviewDb db, AccountGroup.UUID groupUuid, Set<Account.Id> accountIds)
      throws OrmException, IOException, NoSuchGroupException, ConfigInvalidException {
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(memberIds -> Sets.union(memberIds, accountIds))
            .build();
    updateGroup(db, groupUuid, groupUpdate);
  }

  /**
   * Removes several members (accounts) from a group. Only accounts which currently are members of
   * the group are removed.
   *
   * @param db the {@code ReviewDb} instance to update
   * @param groupUuid the UUID of the group
   * @param accountIds a set of IDs of accounts to remove
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws IOException if the group or one of the removed members couldn't be indexed
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void removeGroupMembers(
      ReviewDb db, AccountGroup.UUID groupUuid, Set<Account.Id> accountIds)
      throws OrmException, IOException, NoSuchGroupException, ConfigInvalidException {
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setMemberModification(memberIds -> Sets.difference(memberIds, accountIds))
            .build();
    updateGroup(db, groupUuid, groupUpdate);
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
   * @param newSubgroupUuids a set of IDs of the groups to add as subgroups
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws IOException if the parent group couldn't be indexed
   * @throws NoSuchGroupException if the specified parent group doesn't exist
   */
  public void addSubgroups(
      ReviewDb db, AccountGroup.UUID parentGroupUuid, Set<AccountGroup.UUID> newSubgroupUuids)
      throws OrmException, NoSuchGroupException, IOException, ConfigInvalidException {
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setSubgroupModification(subgroupUuids -> Sets.union(subgroupUuids, newSubgroupUuids))
            .build();
    updateGroup(db, parentGroupUuid, groupUpdate);
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
   * @param removedSubgroupUuids a set of IDs of the subgroups to remove from the parent group
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws IOException if the parent group couldn't be indexed
   * @throws NoSuchGroupException if the specified parent group doesn't exist
   */
  public void removeSubgroups(
      ReviewDb db, AccountGroup.UUID parentGroupUuid, Set<AccountGroup.UUID> removedSubgroupUuids)
      throws OrmException, NoSuchGroupException, IOException, ConfigInvalidException {
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setSubgroupModification(
                subgroupUuids -> Sets.difference(subgroupUuids, removedSubgroupUuids))
            .build();
    updateGroup(db, parentGroupUuid, groupUpdate);
  }

  @FunctionalInterface
  private interface MetaDataUpdateFactory {
    MetaDataUpdate create(Project.NameKey projectName) throws IOException;
  }

  @AutoValue
  abstract static class UpdateResult {
    abstract AccountGroup.UUID getGroupUuid();

    abstract AccountGroup.Id getGroupId();

    abstract AccountGroup.NameKey getGroupName();

    abstract Optional<AccountGroup.NameKey> getPreviousGroupName();

    abstract ImmutableSet<Account.Id> getModifiedMembers();

    abstract ImmutableSet<AccountGroup.UUID> getModifiedSubgroups();

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

      abstract UpdateResult build();
    }
  }
}
