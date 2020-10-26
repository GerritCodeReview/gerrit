// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.server.account.ServiceUserClassifier;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.group.db.AuditLogFormatter;
import com.google.gerrit.server.group.db.GroupConfig;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.group.db.InternalGroupUpdate;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

/**
 * Schema 184 for Gerrit metadata.
 *
 * <p>Upgrading to this schema version will rename the {@code Non-Interactive Users} group to {@code
 * Service Users}.
 */
public class Schema_184 implements NoteDbSchemaVersion {
  @Override
  public void upgrade(Arguments args, UpdateUI ui) throws Exception {
    try (Repository allUsersRepo = args.repoManager.openRepository(args.allUsers)) {
      AccountGroup.NameKey newName = AccountGroup.nameKey(ServiceUserClassifier.SERVICE_USERS);
      Optional<GroupReference> nonInteractiveUsers =
          GroupNameNotes.loadAllGroups(allUsersRepo).stream()
              .filter(g -> g.getName().equals("Non-Interactive Users"))
              .findAny();
      if (!nonInteractiveUsers.isPresent()) {
        return;
      }

      GroupNameNotes newNameNotes =
          GroupNameNotes.forRename(
              args.allUsers,
              allUsersRepo,
              nonInteractiveUsers.get().getUUID(),
              AccountGroup.nameKey(nonInteractiveUsers.get().getName()),
              newName);
      GroupConfig groupConfig =
          GroupConfig.loadForGroup(
              args.allUsers, allUsersRepo, nonInteractiveUsers.get().getUUID());
      groupConfig.setGroupUpdate(
          InternalGroupUpdate.builder().setName(newName).build(),
          AuditLogFormatter.createPartiallyWorkingFallBack());
      commit(args.allUsers, args.serverUser, allUsersRepo, groupConfig, newNameNotes);
      index(
          args.groupIndexCollection,
          groupConfig
              .getLoadedGroup()
              .orElseThrow(
                  () -> new IllegalStateException("Created group wasn't automatically loaded")));
    }
  }

  private void commit(
      AllUsersName allUsersName,
      PersonIdent serverUser,
      Repository allUsersRepo,
      GroupConfig groupConfig,
      GroupNameNotes groupNameNotes)
      throws IOException {
    BatchRefUpdate batchRefUpdate = allUsersRepo.getRefDatabase().newBatchUpdate();
    try (MetaDataUpdate metaDataUpdate =
        createMetaDataUpdate(allUsersName, serverUser, allUsersRepo, batchRefUpdate)) {
      groupConfig.commit(metaDataUpdate);
    }
    // MetaDataUpdates unfortunately can't be reused. -> Create a new one.
    try (MetaDataUpdate metaDataUpdate =
        createMetaDataUpdate(allUsersName, serverUser, allUsersRepo, batchRefUpdate)) {
      groupNameNotes.commit(metaDataUpdate);
    }
    RefUpdateUtil.executeChecked(batchRefUpdate, allUsersRepo);
  }

  private MetaDataUpdate createMetaDataUpdate(
      AllUsersName allUsersName,
      PersonIdent serverUser,
      Repository allUsersRepo,
      @Nullable BatchRefUpdate batchRefUpdate) {
    MetaDataUpdate metaDataUpdate =
        new MetaDataUpdate(
            GitReferenceUpdated.DISABLED, allUsersName, allUsersRepo, batchRefUpdate);
    metaDataUpdate.getCommitBuilder().setAuthor(serverUser);
    metaDataUpdate.getCommitBuilder().setCommitter(serverUser);
    return metaDataUpdate;
  }

  private void index(GroupIndexCollection indexCollection, InternalGroup group) {
    for (GroupIndex groupIndex : indexCollection.getWriteIndexes()) {
      groupIndex.replace(group);
    }
  }
}
