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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.StarredChangesUtil.IllegalLabelException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

public class Schema_123 extends SchemaVersion {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;

  @Inject
  Schema_123(
      Provider<Schema_122> prior, GitRepositoryManager repoManager, AllUsersName allUsersName) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    ListMultimap<Account.Id, Change.Id> imports =
        MultimapBuilder.hashKeys().arrayListValues().build();
    try (Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
        ResultSet rs = stmt.executeQuery("SELECT account_id, change_id FROM starred_changes")) {
      while (rs.next()) {
        Account.Id accountId = new Account.Id(rs.getInt(1));
        Change.Id changeId = new Change.Id(rs.getInt(2));
        imports.put(accountId, changeId);
      }
    }

    if (imports.isEmpty()) {
      return;
    }

    try (Repository git = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(git)) {
      BatchRefUpdate bru = git.getRefDatabase().newBatchUpdate();
      ObjectId id = StarredChangesUtil.writeLabels(git, StarredChangesUtil.DEFAULT_LABELS);
      for (Map.Entry<Account.Id, Change.Id> e : imports.entries()) {
        bru.addCommand(
            new ReceiveCommand(
                ObjectId.zeroId(), id, RefNames.refsStarredChanges(e.getValue(), e.getKey())));
      }
      bru.execute(rw, new TextProgressMonitor());
    } catch (IOException | IllegalLabelException ex) {
      throw new OrmException(ex);
    }
  }
}
