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

import static com.google.gerrit.server.Sequence.LightweightGroups;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.Sequence;
import com.google.gerrit.server.account.GroupUuid;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.db.AuditLogFormatter;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.GroupDelta;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.group.db.InternalGroupCreation;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

// TODO(dborowitz): The current NoteDb implementation mirrors the old ReviewDb code: this class is
// called to create the site early on in NoteDbSchemaUpdater#update. This logic is a little
// confusing and could stand to be reworked. Another smell is that this is an interface only for
// testing purposes.
public class SchemaCreatorImpl implements SchemaCreator {
  public static final String BLOCKED_USERS = "Blocked Users";

  private final GitRepositoryManager repoManager;
  private final AllProjectsCreator allProjectsCreator;
  private final AllUsersCreator allUsersCreator;
  private final AllUsersName allUsersName;
  private final Sequence groupsSequence;
  private final PersonIdent serverUser;
  private final GroupIndexCollection indexCollection;
  private final String serverId;
  private final AllProjectsName allProjectsName;

  @Inject
  public SchemaCreatorImpl(
      GitRepositoryManager repoManager,
      AllProjectsCreator ap,
      AllUsersCreator auc,
      AllUsersName allUsersName,
      @LightweightGroups Sequence groupsSequence,
      @GerritPersonIdent PersonIdent au,
      GroupIndexCollection ic,
      String serverId,
      AllProjectsName apName) {
    this.repoManager = repoManager;
    allProjectsCreator = ap;
    allUsersCreator = auc;
    this.allUsersName = allUsersName;
    this.groupsSequence = groupsSequence;
    serverUser = au;
    indexCollection = ic;
    this.serverId = serverId;

    this.allProjectsName = apName;
  }

  @Override
  public void create() throws IOException, ConfigInvalidException {
    try (RefUpdateContext ctx = RefUpdateContext.open(RefUpdateType.INIT_REPO)) {
      GroupReference admins = createGroupReference("Administrators");
      GroupReference serviceUsers = createGroupReference(ServiceUserClassifier.SERVICE_USERS);
      GroupReference blockedUsers = createGroupReference(BLOCKED_USERS);

      AllProjectsInput allProjectsInput =
          AllProjectsInput.builder()
              .administratorsGroup(admins)
              .serviceUsersGroup(serviceUsers)
              .blockedUsersGroup(blockedUsers)
              .build();
      allProjectsCreator.create(allProjectsInput);
      // We have to create the All-Users repository before we can use it to store the groups in it.
      allUsersCreator.setAdministrators(admins).create();

      try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
        createAdminsGroup(allUsersRepo, admins);
        createServiceUsersGroup(allUsersRepo, serviceUsers, admins.getUUID());
        createBlockedUsersGroup(allUsersRepo, blockedUsers, admins.getUUID());
      }
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

  private void createAdminsGroup(Repository allUsersRepo, GroupReference groupReference)
      throws IOException, ConfigInvalidException {
    InternalGroupCreation groupCreation = getGroupCreation(groupReference);
    GroupDelta groupDelta =
        GroupDelta.builder().setDescription("Gerrit Site Administrators").build();

    createGroup(allUsersRepo, groupCreation, groupDelta);
  }

  private void createServiceUsersGroup(
      Repository allUsersRepo, GroupReference groupReference, AccountGroup.UUID adminsGroupUuid)
      throws IOException, ConfigInvalidException {
    InternalGroupCreation groupCreation = getGroupCreation(groupReference);
    GroupDelta groupDelta =
        GroupDelta.builder()
            .setDescription("Users who perform batch actions on Gerrit")
            .setOwnerGroupUUID(adminsGroupUuid)
            .setVisibleToAll(true)
            .build();

    createGroup(allUsersRepo, groupCreation, groupDelta);
  }

  private void createBlockedUsersGroup(
      Repository allUsersRepo, GroupReference groupReference, AccountGroup.UUID adminsGroupUuid)
      throws IOException, ConfigInvalidException {
    InternalGroupCreation groupCreation = getGroupCreation(groupReference);
    GroupDelta groupDelta =
        GroupDelta.builder()
            .setDescription("Blocked users. Add spammers to this group.")
            .setOwnerGroupUUID(adminsGroupUuid)
            .build();

    createGroup(allUsersRepo, groupCreation, groupDelta);
  }

  private void createGroup(
      Repository allUsersRepo, InternalGroupCreation groupCreation, GroupDelta groupDelta)
      throws ConfigInvalidException, IOException {
    InternalGroup createdGroup = createGroupInNoteDb(allUsersRepo, groupCreation, groupDelta);
    index(createdGroup);
  }

  private InternalGroup createGroupInNoteDb(
      Repository allUsersRepo, InternalGroupCreation groupCreation, GroupDelta groupDelta)
      throws ConfigInvalidException, IOException, DuplicateKeyException {
    // This method is only executed on a new server which doesn't have any accounts or groups.
    AuditLogFormatter auditLogFormatter =
        AuditLogFormatter.createBackedBy(ImmutableSet.of(), ImmutableSet.of(), serverId);

    GroupConfig groupConfig =
        GroupConfig.createForNewGroup(allUsersName, allUsersRepo, groupCreation);
    groupConfig.setGroupDelta(groupDelta, auditLogFormatter);

    AccountGroup.NameKey groupName = groupDelta.getName().orElseGet(groupCreation::getNameKey);
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
    AccountGroup.UUID groupUuid = GroupUuid.make(name, serverUser);
    return GroupReference.create(groupUuid, name);
  }

  private InternalGroupCreation getGroupCreation(GroupReference groupReference) {
    int next = groupsSequence.next();
    return InternalGroupCreation.builder()
        .setNameKey(AccountGroup.nameKey(groupReference.getName()))
        .setId(AccountGroup.id(next))
        .setGroupUUID(groupReference.getUUID())
        .build();
  }
}
