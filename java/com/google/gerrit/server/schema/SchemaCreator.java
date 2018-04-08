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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePath;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.AuditLogFormatter;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
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
  private final String serverId;

  private final Config config;
  private final MetricMaker metricMaker;
  private final NotesMigration migration;
  private final AllProjectsName allProjectsName;

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
      @GerritServerId String serverId,
      @GerritServerConfig Config config,
      MetricMaker metricMaker,
      NotesMigration migration,
      AllProjectsName apName) {
    this(
        site.site_path,
        repoManager,
        ap,
        auc,
        allUsersName,
        au,
        dst,
        ic,
        serverId,
        config,
        metricMaker,
        migration,
        apName);
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
      String serverId,
      Config config,
      MetricMaker metricMaker,
      NotesMigration migration,
      AllProjectsName apName) {
    site_path = site;
    this.repoManager = repoManager;
    allProjectsCreator = ap;
    allUsersCreator = auc;
    this.allUsersName = allUsersName;
    serverUser = au;
    dataSourceType = dst;
    indexCollection = ic;
    this.serverId = serverId;

    this.config = config;
    this.allProjectsName = apName;
    this.migration = migration;
    this.metricMaker = metricMaker;
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

    // Don't rely on injection to construct Sequences, as it requires ReviewDb.
    Sequences seqs =
        new Sequences(
            config,
            () -> db,
            migration,
            repoManager,
            GitReferenceUpdated.DISABLED,
            allProjectsName,
            allUsersName,
            metricMaker);
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      createAdminsGroup(seqs, allUsersRepo, admins);
      createBatchUsersGroup(seqs, allUsersRepo, batchUsers, admins.getUUID());
    }

    dataSourceType.getIndexScript().run(db);
  }

  private void createAdminsGroup(
      Sequences seqs, Repository allUsersRepo, GroupReference groupReference)
      throws OrmException, IOException, ConfigInvalidException {
    InternalGroupCreation groupCreation = getGroupCreation(seqs, groupReference);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder().setDescription("Gerrit Site Administrators").build();

    createGroup(allUsersRepo, groupCreation, groupUpdate);
  }

  private void createBatchUsersGroup(
      Sequences seqs,
      Repository allUsersRepo,
      GroupReference groupReference,
      AccountGroup.UUID adminsGroupUuid)
      throws OrmException, IOException, ConfigInvalidException {
    InternalGroupCreation groupCreation = getGroupCreation(seqs, groupReference);
    InternalGroupUpdate groupUpdate =
        InternalGroupUpdate.builder()
            .setDescription("Users who perform batch actions on Gerrit")
            .setOwnerGroupUUID(adminsGroupUuid)
            .build();

    createGroup(allUsersRepo, groupCreation, groupUpdate);
  }

  private void createGroup(
      Repository allUsersRepo, InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws OrmException, ConfigInvalidException, IOException {
    InternalGroup createdGroup = createGroupInNoteDb(allUsersRepo, groupCreation, groupUpdate);
    index(createdGroup);
  }

  private InternalGroup createGroupInNoteDb(
      Repository allUsersRepo, InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws ConfigInvalidException, IOException, OrmDuplicateKeyException {
    // This method is only executed on a new server which doesn't have any accounts or groups.
    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(ImmutableSet.of(), ImmutableSet.of(), serverId);

    GroupConfig groupConfig = GroupConfig.createForNewGroup(allUsersRepo, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

    AccountGroup.NameKey groupName = groupUpdate.getName().orElseGet(groupCreation::getNameKey);
    GroupNameNotes groupNameNotes =
        GroupNameNotes.forNewGroup(allUsersRepo, groupCreation.getGroupUUID(), groupName);

    commit(allUsersRepo, groupConfig, groupNameNotes);

    return groupConfig
        .getLoadedGroup()
        .orElseThrow(() -> new IllegalStateException("Created group wasn't automatically loaded"));
  }

  private void commit(
      Repository allUsersRepo, GroupConfig groupConfig, GroupNameNotes groupNameNotes)
      throws IOException {
    BatchRefUpdate batchRefUpdate = allUsersRepo.getRefDatabase().newBatchUpdate();
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate(allUsersRepo, batchRefUpdate)) {
      groupConfig.commit(metaDataUpdate);
    }
    // MetaDataUpdates unfortunately can't be reused. -> Create a new one.
    try (MetaDataUpdate metaDataUpdate = createMetaDataUpdate(allUsersRepo, batchRefUpdate)) {
      groupNameNotes.commit(metaDataUpdate);
    }
    RefUpdateUtil.executeChecked(batchRefUpdate, allUsersRepo);
  }

  private MetaDataUpdate createMetaDataUpdate(
      Repository allUsersRepo, @Nullable BatchRefUpdate batchRefUpdate) {
    MetaDataUpdate metaDataUpdate =
        new MetaDataUpdate(
            GitReferenceUpdated.DISABLED, allUsersName, allUsersRepo, batchRefUpdate);
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

  private InternalGroupCreation getGroupCreation(Sequences seqs, GroupReference groupReference)
      throws OrmException {
    int next = seqs.nextGroupId();
    return InternalGroupCreation.builder()
        .setNameKey(new AccountGroup.NameKey(groupReference.getName()))
        .setId(new AccountGroup.Id(next))
        .setGroupUUID(groupReference.getUUID())
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
