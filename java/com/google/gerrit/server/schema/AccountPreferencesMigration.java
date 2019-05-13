// Copyright (C) 2019 The Android Open Source Project
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

import static com.google.gerrit.reviewdb.client.RefNames.REFS_USERS;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class AccountPreferencesMigration {
  public interface Migration {
    void migrate(Config config);
  }

  public interface Factory {
    AccountPreferencesMigration create(Migration migration);
  }

  private final GitRepositoryManager repoManager;
  private final MetaDataUpdate.Server metaDataUpdateFactory;
  private final AllUsersName allUsers;
  private final Migration migration;

  @Inject
  AccountPreferencesMigration(
      GitRepositoryManager repoManager,
      MetaDataUpdate.Server metaDataUpdateFactory,
      AllUsersName allUsers,
      @Assisted Migration migration) {
    this.repoManager = repoManager;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.allUsers = allUsers;
    this.migration = migration;
  }

  public void execute(String message) throws IOException {
    try (Repository repo = repoManager.openRepository(allUsers);
        RevWalk rw = new RevWalk(repo)) {
      BatchRefUpdate batchUpdate = repo.getRefDatabase().newBatchUpdate();
      for (String userRef :
          repo.getRefDatabase().getRefsByPrefix(REFS_USERS).stream()
              .map(Ref::getName)
              .collect(toList())) {
        try (MetaDataUpdate md = metaDataUpdateFactory.create(allUsers, batchUpdate)) {
          md.setMessage(message);
          VersionedAccountPreferences prefs = new VersionedAccountPreferences(userRef);
          migration.migrate(prefs.getConfig());
          prefs.commit(md);
        }
      }
      batchUpdate.execute(rw, NullProgressMonitor.INSTANCE);
    }
  }
}
