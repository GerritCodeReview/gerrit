// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/** Creates the current database schema and populates initial code rows. */
public class SchemaCreator {
  @SitePath private final Path site_path;

  private final GitRepositoryManager repoManager;
  private final AllProjectsCreator allProjectsCreator;
  private final AllUsersCreator allUsersCreator;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;
  private final DataSourceType dataSourceType;
  private final GroupIndexCollection indexCollection;
  private final boolean writeGroupsToNoteDb;

  @Inject
  public SchemaCreator(
      SitePaths site,
      GitRepositoryManager repoManager,
      AllProjectsCreator ap,
      AllUsersCreator auc,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent au,
      DataSourceType dst,
      GroupIndexCollection ic,
      @GerritServerConfig Config config) {
    this(site.site_path, repoManager, ap, auc, allUsersName, au, dst, ic, config);
  }

  public SchemaCreator(
      @SitePath Path site,
      GitRepositoryManager repoManager,
      AllProjectsCreator ap,
      AllUsersCreator auc,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent au,
      DataSourceType dst,
      GroupIndexCollection ic,
      Config config) {
    site_path = site;
    this.repoManager = repoManager;
    allProjectsCreator = ap;
    allUsersCreator = auc;
    this.allUsersName = allUsersName;
    serverUser = au;
    dataSourceType = dst;
    indexCollection = ic;
    // TODO(aliceks): Remove this flag when all other necessary TODOs for writing groups to NoteDb
    // have been addressed.
    // Don't flip this flag in a production setting! We only added it to spread the implementation
    // of groups in NoteDb among several changes which are gradually merged.
    writeGroupsToNoteDb = config.getBoolean("user", null, "writeGroupsToNoteDb", false);
  }

  public void create(ReviewDb db) throws OrmException, IOException, ConfigInvalidException {
    final JdbcSchema jdbc = (JdbcSchema) db;
    try (JdbcExecutor e = new JdbcExecutor(jdbc)) {
      jdbc.updateSchema(e);
    }

    final CurrentSchemaVersion sVer = CurrentSchemaVersion.create();
    sVer.versionNbr = SchemaVersion.getBinaryVersion();
    db.schemaVersion().insert(Collections.singleton(sVer));

    GroupReference admins = createGroupReference("Administrators");
    GroupReference batchUsers = createGroupReference("Non-Interactive Users");

    initSystemConfig(db);
    allProjectsCreator.setAdministrators(admins).setBatchUsers(batchUsers).create();
    // We have to create the All-Users repository before we can use it to store the groups in it.
    allUsersCreator.setAdministrators(admins).create();

    try (Repository repository = repoManager.openRepository(allUsersName)) {
      createAdminsGroup(db, repository, admins);
      createBatchUsersGroup(db, repository, batchUsers, admins.getUUID());
    }

    dataSourceType.getIndexScript().run(db);
  }

  private void createAdminsGroup(ReviewDb db, Repository repository, GroupReference groupReference)
      throws OrmException, IOException, ConfigInvalidException {
    InternalGroupCreation groupCreation = getGroupCreation(db, groupReference);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setDescription("Gerrit Site Administrators").build();

    createGroup(db, repository, groupCreation, groupUpdate);
  }

  private void createBatchUsersGroup(
      ReviewDb db,
      Repository repository,
      GroupReference groupReference,
      AccountGroup.UUID adminsGroupUuid)
      throws OrmException, IOException, ConfigInvalidException {
    InternalGroupCreation groupCreation = getGroupCreation(db, groupReference);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setDescription("Users who perform batch actions on Gerrit")
            .setOwnerGroupUUID(adminsGroupUuid)
            .build();

    createGroup(db, repository, groupCreation, groupUpdate);
  }

  private void createGroup(
      ReviewDb db,
      Repository repository,
      InternalGroupCreation groupCreation,
      InternalGroupUpdate groupUpdate)
      throws OrmException, ConfigInvalidException, IOException {
    InternalGroup groupInReviewDb = createGroupInReviewDb(db, groupCreation, groupUpdate);

    if (!writeGroupsToNoteDb) {
      index(groupInReviewDb);
      return;
    }

    InternalGroup createdGroup = createGroupInNoteDb(repository, groupCreation, groupUpdate);
    index(createdGroup);
  }

  private static InternalGroup createGroupInReviewDb(
      ReviewDb db, InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws OrmException {
    AccountGroup group = GroupsUpdate.createAccountGroup(groupCreation, groupUpdate);
    db.accountGroupNames().insert(ImmutableList.of(new AccountGroupName(group)));
    db.accountGroups().insert(ImmutableList.of(group));
    return InternalGroup.create(group, ImmutableSet.of(), ImmutableSet.of());
  }

  private InternalGroup createGroupInNoteDb(
      Repository repository, InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws ConfigInvalidException, IOException, OrmDuplicateKeyException {
    GroupConfig groupConfig = GroupConfig.createForNewGroup(repository, groupCreation);
    // We don't add any initial members or subgroups and hence the provided functions should never
    // be called. To be on the safe side, we specify some valid functions.
    groupConfig.setGroupUpdate(groupUpdate, Account.Id::toString, AccountGroup.UUID::get);
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate(repository)) {
      groupConfig.commit(metaDataUpdate);
    }
    return groupConfig
        .getLoadedGroup()
        .orElseThrow(() -> new IllegalStateException("Created group wasn't automatically loaded"));
  }

  private MetaDataUpdate createMetaDataUpdate(Repository repository) {
    MetaDataUpdate metaDataUpdate =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, repository);
    metaDataUpdate.getCommitBuilder().setAuthor(serverUser);
    metaDataUpdate.getCommitBuilder().setCommitter(serverUser);
    return metaDataUpdate;
  }

  private void index(InternalGroup group) throws IOException {
    for (GroupIndex groupIndex : indexCollection.getWriteIndexes()) {
      groupIndex.replace(group);
    }
  }

  private GroupReference createGroupReference(String name) {
    AccountGroup.UUID groupUuid = GroupUUID.make(name, serverUser);
    return new GroupReference(groupUuid, name);
  }

  private static InternalGroupCreation getGroupCreation(ReviewDb db, GroupReference groupReference)
      throws OrmException {
    return InternalGroupCreation.builder()
        .setNameKey(new AccountGroup.NameKey(groupReference.getName()))
        .setId(new AccountGroup.Id(db.nextAccountGroupId()))
        .setGroupUUID(groupReference.getUUID())
        .setCreatedOn(TimeUtil.nowTs())
        .build();
  }

  private SystemConfig initSystemConfig(ReviewDb db) throws OrmException {
    SystemConfig s = SystemConfig.create();
    try {
      s.sitePath = site_path.toRealPath().normalize().toString();
    } catch (IOException e) {
      s.sitePath = site_path.toAbsolutePath().normalize().toString();
    }
    db.systemConfig().insert(Collections.singleton(s));
    return s;
  }
}
