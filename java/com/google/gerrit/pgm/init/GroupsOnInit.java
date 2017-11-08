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

package com.google.gerrit.pgm.init;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.pgm.init.api.AllUsersNameOnInitProvider;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;

/**
 * A database accessor for calls related to groups.
 *
 * <p>All calls which read or write group related details to the database <strong>during
 * init</strong> (either ReviewDb or NoteDb) are gathered here. For non-init cases, use {@code
 * Groups} or {@code GroupsUpdate} instead.
 *
 * <p>All methods of this class refer to <em>internal</em> groups.
 */
public class GroupsOnInit {

  private final InitFlags flags;
  private final SitePaths site;
  private final String allUsers;
  private final boolean writeGroupsToNoteDb;

  @Inject
  public GroupsOnInit(InitFlags flags, SitePaths site, AllUsersNameOnInitProvider allUsers) {
    this.flags = flags;
    this.site = site;
    this.allUsers = allUsers.get();
    // TODO(aliceks): Remove this flag when all other necessary TODOs for writing groups to NoteDb
    // have been addressed.
    // Don't flip this flag in a production setting! We only added it to spread the implementation
    // of groups in NoteDb among several changes which are gradually merged.
    writeGroupsToNoteDb = flags.cfg.getBoolean("user", null, "writeGroupsToNoteDb", false);
  }

  /**
   * Returns the {@code AccountGroup} for the specified name.
   *
   * @param db the {@code ReviewDb} instance to use for lookups
   * @param groupName the name of the group
   * @return the {@code AccountGroup} which has the specified name
   * @throws OrmException if the group couldn't be retrieved from ReviewDb
   * @throws NoSuchGroupException if a group with such a name doesn't exist
   */
  public AccountGroup getExistingGroup(ReviewDb db, AccountGroup.NameKey groupName)
      throws OrmException, NoSuchGroupException {
    AccountGroupName accountGroupName = db.accountGroupNames().get(groupName);
    if (accountGroupName == null) {
      throw new NoSuchGroupException(groupName.toString());
    }

    AccountGroup.Id groupId = accountGroupName.getId();
    AccountGroup group = db.accountGroups().get(groupId);
    if (group == null) {
      throw new NoSuchGroupException(groupName.toString());
    }
    return group;
  }

  /**
   * Adds an account as member to a group. The account is only added as a new member if it isn't
   * already a member of the group.
   *
   * <p><strong>Note</strong>: This method doesn't check whether the account exists! It also doesn't
   * update the account index!
   *
   * @param db the {@code ReviewDb} instance to update
   * @param groupUuid the UUID of the group
   * @param account the account to add
   * @throws OrmException if an error occurs while reading/writing from/to ReviewDb
   * @throws NoSuchGroupException if the specified group doesn't exist
   */
  public void addGroupMember(ReviewDb db, AccountGroup.UUID groupUuid, Account account)
      throws OrmException, NoSuchGroupException, IOException, ConfigInvalidException {
    addGroupMemberInReviewDb(db, groupUuid, account.getId());
    if (!writeGroupsToNoteDb) {
      return;
    }
    addGroupMemberInNoteDb(groupUuid, account);
  }

  private static void addGroupMemberInReviewDb(
      ReviewDb db, AccountGroup.UUID groupUuid, Account.Id accountId)
      throws OrmException, NoSuchGroupException {
    AccountGroup group = getExistingGroup(db, groupUuid);
    AccountGroup.Id groupId = group.getId();

    if (isMember(db, groupId, accountId)) {
      return;
    }

    db.accountGroupMembers()
        .insert(
            ImmutableList.of(
                new AccountGroupMember(new AccountGroupMember.Key(accountId, groupId))));
  }

  private static AccountGroup getExistingGroup(ReviewDb db, AccountGroup.UUID groupUuid)
      throws OrmException, NoSuchGroupException {
    List<AccountGroup> accountGroups = db.accountGroups().byUUID(groupUuid).toList();
    if (accountGroups.size() == 1) {
      return Iterables.getOnlyElement(accountGroups);
    } else if (accountGroups.isEmpty()) {
      throw new NoSuchGroupException(groupUuid);
    } else {
      throw new OrmDuplicateKeyException("Duplicate group UUID " + groupUuid);
    }
  }

  private static boolean isMember(ReviewDb db, AccountGroup.Id groupId, Account.Id accountId)
      throws OrmException {
    AccountGroupMember.Key key = new AccountGroupMember.Key(accountId, groupId);
    return db.accountGroupMembers().get(key) != null;
  }

  private void addGroupMemberInNoteDb(AccountGroup.UUID groupUuid, Account account)
      throws IOException, ConfigInvalidException, NoSuchGroupException {
    File allUsersRepoPath = getPathToAllUsersRepository();
    if (allUsersRepoPath != null) {
      try (Repository repository = new FileRepository(allUsersRepoPath)) {
        addGroupMemberInNoteDb(repository, groupUuid, account);
      }
    }
  }

  private void addGroupMemberInNoteDb(
      Repository repository, AccountGroup.UUID groupUuid, Account account)
      throws IOException, ConfigInvalidException, NoSuchGroupException {
    GroupConfig groupConfig = GroupConfig.loadForGroup(repository, groupUuid);
    InternalGroup group =
        groupConfig.getLoadedGroup().orElseThrow(() -> new NoSuchGroupException(groupUuid));

    InternalGroupUpdate groupUpdate = getMemberAdditionUpdate(account);
    groupConfig.setGroupUpdate(
        groupUpdate, accountId -> getAccountNameEmail(account, accountId), AccountGroup.UUID::get);

    commit(repository, groupConfig, group.getCreatedOn());
  }

  @Nullable
  private File getPathToAllUsersRepository() {
    Path basePath = site.resolve(flags.cfg.getString("gerrit", null, "basePath"));
    checkArgument(basePath != null, "gerrit.basePath must be configured");
    return RepositoryCache.FileKey.resolve(basePath.resolve(allUsers).toFile(), FS.DETECTED);
  }

  private static InternalGroupUpdate getMemberAdditionUpdate(Account account) {
    return InternalGroupUpdate.builder()
        .setMemberModification(members -> Sets.union(members, ImmutableSet.of(account.getId())))
        .build();
  }

  private String getAccountNameEmail(Account knownAccount, Account.Id someAccountId) {
    if (knownAccount.getId().equals(someAccountId)) {
      String anonymousCowardName = new AnonymousCowardNameProvider(flags.cfg).get();
      return knownAccount.getNameEmail(anonymousCowardName);
    }
    return String.valueOf(someAccountId);
  }

  private void commit(Repository repository, GroupConfig groupConfig, Timestamp groupCreatedOn)
      throws IOException {
    PersonIdent personIdent =
        new PersonIdent(new GerritPersonIdentProvider(flags.cfg).get(), groupCreatedOn);
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate(repository, personIdent)) {
      groupConfig.commit(metaDataUpdate);
    }
  }

  private MetaDataUpdate createMetaDataUpdate(Repository repository, PersonIdent personIdent) {
    MetaDataUpdate metaDataUpdate =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, new Project.NameKey(allUsers), repository);
    metaDataUpdate.getCommitBuilder().setAuthor(personIdent);
    metaDataUpdate.getCommitBuilder().setCommitter(personIdent);
    return metaDataUpdate;
  }
}
