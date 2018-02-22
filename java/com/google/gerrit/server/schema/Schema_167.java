// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.db.GroupBundle;
import com.google.gerrit.server.group.db.GroupNameNotes;
import com.google.gerrit.server.group.db.GroupRebuilder;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

public class Schema_167 extends SchemaVersion {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final GroupRebuilder rebuilder;
  private final GroupBundle.Factory groupBundleFactory;
  private final PersonIdent serverIdent;

  @Inject
  protected Schema_167(
      Provider<Schema_166> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      GroupRebuilder rebuilder,
      GroupBundle.Factory groupBundleFactory,
      @GerritPersonIdent PersonIdent serverIdent) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.rebuilder = rebuilder;
    this.groupBundleFactory = groupBundleFactory;
    this.serverIdent = serverIdent;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try (Repository allUsersRepo = repoManager.openRepository(allUsersName)) {
      // TODO(aliceks): Switch to SQL statements.
      List<AccountGroup> allGroups = db.accountGroups().all().toList();

      BatchRefUpdate batchRefUpdate = allUsersRepo.getRefDatabase().newBatchUpdate();
      writeAllGroupNamesToNoteDb(allUsersRepo, allGroups, batchRefUpdate);

      for (AccountGroup group : allGroups) {
        migrateOneGroupToNoteDb(db, allUsersRepo, group.getId(), batchRefUpdate);
      }

      RefUpdateUtil.executeChecked(batchRefUpdate, allUsersRepo);
      // TODO(aliceks): Switch config settings? Or remove all ReviewDb code in same topic?
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException(
          String.format("Failed to migrate groups to NoteDb for %s", allUsersName.get()), e);
    }
  }

  private void writeAllGroupNamesToNoteDb(
      Repository allUsersRepo, List<AccountGroup> allGroups, BatchRefUpdate batchRefUpdate)
      throws IOException {
    try (ObjectInserter inserter = allUsersRepo.newObjectInserter()) {
      List<GroupReference> groupRefs =
          allGroups.stream().map(GroupReference::forGroup).collect(toImmutableList());
      GroupNameNotes.updateAllGroups(
          allUsersRepo, inserter, batchRefUpdate, groupRefs, serverIdent);
      inserter.flush();
    }
  }

  private void migrateOneGroupToNoteDb(
      ReviewDb db, Repository allUsersRepo, AccountGroup.Id id, BatchRefUpdate batchRefUpdate)
      throws ConfigInvalidException, IOException, OrmException {
    GroupBundle reviewDbBundle = groupBundleFactory.fromReviewDb(db, id);
    rebuilder.rebuild(allUsersRepo, reviewDbBundle, batchRefUpdate);
  }
}
