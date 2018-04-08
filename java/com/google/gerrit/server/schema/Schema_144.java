// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIdNotes;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

public class Schema_144 extends SchemaVersion {
  private static final String COMMIT_MSG = "Import external IDs from ReviewDb";

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final PersonIdent serverIdent;

  @Inject
  Schema_144(
      Provider<Schema_143> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverIdent) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverIdent = serverIdent;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    Set<ExternalId> toAdd = new HashSet<>();
    try (Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT "
                    + "account_id, "
                    + "email_address, "
                    + "password, "
                    + "external_id "
                    + "FROM account_external_ids")) {
      while (rs.next()) {
        Account.Id accountId = new Account.Id(rs.getInt(1));
        String email = rs.getString(2);
        String password = rs.getString(3);
        String externalId = rs.getString(4);

        toAdd.add(ExternalId.create(ExternalId.Key.parse(externalId), accountId, email, password));
      }
    }

    try {
      try (Repository repo = repoManager.openRepository(allUsersName)) {
        ExternalIdNotes extIdNotes = ExternalIdNotes.loadNoCacheUpdate(repo);
        extIdNotes.upsert(toAdd);
        try (MetaDataUpdate metaDataUpdate =
            new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, repo)) {
          metaDataUpdate.getCommitBuilder().setAuthor(serverIdent);
          metaDataUpdate.getCommitBuilder().setCommitter(serverIdent);
          metaDataUpdate.getCommitBuilder().setMessage(COMMIT_MSG);
          extIdNotes.commit(metaDataUpdate);
        }
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException("Failed to migrate external IDs to NoteDb", e);
    }
  }
}
