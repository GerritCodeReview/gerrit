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
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
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
import com.google.gerrit.server.notedb.Sequences;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

// TODO(dborowitz): The current NoteDb implementation mirrors the old ReviewDb code: this class is
// called to create the site early on in NoteDbSchemaUpdater#update. This logic is a little
// confusing and could stand to be reworked. Another smell is that this is an interface only for
// testing purposes.
public class SchemaCreatorImpl implements SchemaCreator {
  private final GitRepositoryManager repoManager;
  private final AllProjectsCreator allProjectsCreator;
  private final AllUsersCreator allUsersCreator;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;
  private final GroupIndexCollection indexCollection;
  private final String serverId;

  private final Config config;
  private final MetricMaker metricMaker;
  private final AllProjectsName allProjectsName;

  @Inject
  public SchemaCreatorImpl(
      GitRepositoryManager repoManager,
      AllProjectsCreator ap,
      AllUsersCreator auc,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent au,
      GroupIndexCollection ic,
      String serverId,
      Config config,
      MetricMaker metricMaker,
      AllProjectsName apName) {
    this.repoManager = repoManager;
    allProjectsCreator = ap;
    allUsersCreator = auc;
    this.allUsersName = allUsersName;
    serverUser = au;
    indexCollection = ic;
    this.serverId = serverId;

    this.config = config;
    this.allProjectsName = apName;
    this.metricMaker = metricMaker;
  }

  @Override
  public void create() throws IOException, ConfigInvalidException {
    GroupReference admins = createGroupReference("Administrators");
    GroupReference batchUsers = createGroupReference("Non-Interactive Users");

    AllProjectsInput allProjectsInput =
        AllProjectsInput.builder().administratorsGroup(admins).batchUsersGroup(batchUsers).build();
    allProjectsCreator.create(allProjectsInput);
    // We have to create the All-Users repository before we can use it to store the groups in it.
    allUsersCreator.setAdministrators(admins).create();

    // Don't rely on injection to construct Sequences, as the default GitReferenceUpdated has a
    // thick dependency stack which may not all be available at schema creation time.
    Sequences seqs =
        new Sequences(
            config,
            repoManager,
            GitReferenceUpdated.DISABLED,
            allProjectsName,
            allUsersName,
            metricMaker);
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      createAdminsGroup(seqs, allUsersRepo, admins);
      createBatchUsersGroup(seqs, allUsersRepo, batchUsers, admins.getUUID());
    }
  }

  @Override
  public void ensureCreated() throws IOException, ConfigInvalidException {
    try {
      repoManager.openRepository(allProjectsName).close();
    } catch (RepositoryNotFoundException e) {
      create();
    }
  }

  private void createAdminsGroup(
      Sequences seqs, Repository allUsersRepo, GroupReference groupReference)
      throws IOException, ConfigInvalidException {
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
      throws IOException, ConfigInvalidException {
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
      throws ConfigInvalidException, IOException {
    InternalGroup createdGroup = createGroupInNoteDb(allUsersRepo, groupCreation, groupUpdate);
    index(createdGroup);
  }

  private InternalGroup createGroupInNoteDb(
      Repository allUsersRepo, InternalGroupCreation groupCreation, InternalGroupUpdate groupUpdate)
      throws ConfigInvalidException, IOException, DuplicateKeyException {
    // This method is only executed on a new server which doesn't have any accounts or groups.
    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(ImmutableSet.of(), ImmutableSet.of(), serverId);

    GroupConfig groupConfig =
        GroupConfig.createForNewGroup(allUsersName, allUsersRepo, groupCreation);
    groupConfig.setGroupUpdate(groupUpdate, auditLogFormatter);

    AccountGroup.NameKey groupName = groupUpdate.getName().orElseGet(groupCreation::getNameKey);
    GroupNameNotes groupNameNotes =
        GroupNameNotes.forNewGroup(
            allUsersName, allUsersRepo, groupCreation.getGroupUUID(), groupName);

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

  private void index(InternalGroup group) {
    for (GroupIndex groupIndex : indexCollection.getWriteIndexes()) {
      groupIndex.replace(group);
    }
  }

  private GroupReference createGroupReference(String name) {
    AccountGroup.UUID groupUuid = GroupUUID.make(name, serverUser);
    return new GroupReference(groupUuid, name);
  }

  private InternalGroupCreation getGroupCreation(Sequences seqs, GroupReference groupReference) {
    int next = seqs.nextGroupId();
    return InternalGroupCreation.builder()
        .setNameKey(AccountGroup.nameKey(groupReference.getName()))
        .setId(new AccountGroup.Id(next))
        .setGroupUUID(groupReference.getUUID())
        .build();
  }
}
