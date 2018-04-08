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

package com.google.gerrit.server.schema;

import com.google.common.primitives.Ints;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.SQLException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

public class Schema_148 extends SchemaVersion {
  private static final String COMMIT_MSG = "Make account IDs of external IDs human-readable";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;

  @Inject
  Schema_148(
      Provider<Schema_147> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      ExternalIdNotes extIdNotes = ExternalIdNotes.loadNoCacheUpdate(repo);
      for (ExternalId extId : extIdNotes.all()) {
        if (needsUpdate(extId)) {
          extIdNotes.upsert(extId);
        }
      }

      try (MetaDataUpdate metaDataUpdate =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, repo)) {
        metaDataUpdate.getCommitBuilder().setAuthor(serverUser);
        metaDataUpdate.getCommitBuilder().setCommitter(serverUser);
        metaDataUpdate.getCommitBuilder().setMessage(COMMIT_MSG);
        extIdNotes.commit(metaDataUpdate);
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException("Failed to update external IDs", e);
    }
  }

  private static boolean needsUpdate(ExternalId extId) {
    Config cfg = new Config();
    cfg.setInt("externalId", extId.key().get(), "accountId", extId.accountId().get());
    return Ints.tryParse(cfg.getString("externalId", extId.key().get(), "accountId")) == null;
  }
}
