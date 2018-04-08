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

import static java.util.stream.Collectors.toSet;

import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;

/** Delete user branches for which no account exists. */
public class Schema_147 extends SchemaVersion {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;

  @Inject
  Schema_147(
      Provider<Schema_146> prior, GitRepositoryManager repoManager, AllUsersName allUsersName) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      Set<Account.Id> accountIdsFromReviewDb = scanAccounts(db);
      Set<Account.Id> accountIdsFromUserBranches =
          repo.getRefDatabase()
              .getRefs(RefNames.REFS_USERS)
              .values()
              .stream()
              .map(r -> Account.Id.fromRef(r.getName()))
              .filter(Objects::nonNull)
              .collect(toSet());
      accountIdsFromUserBranches.removeAll(accountIdsFromReviewDb);
      for (Account.Id accountId : accountIdsFromUserBranches) {
        deleteUserBranch(repo, accountId);
      }
    } catch (IOException e) {
      throw new OrmException("Failed to delete user branches for non-existing accounts.", e);
    }
  }

  private Set<Account.Id> scanAccounts(ReviewDb db) throws SQLException {
    try (Statement stmt = newStatement(db);
        ResultSet rs = stmt.executeQuery("SELECT account_id FROM accounts")) {
      Set<Account.Id> ids = new HashSet<>();
      while (rs.next()) {
        ids.add(new Account.Id(rs.getInt(1)));
      }
      return ids;
    }
  }

  private void deleteUserBranch(Repository allUsersRepo, Account.Id accountId) throws IOException {
    String refName = RefNames.refsUsers(accountId);
    Ref ref = allUsersRepo.exactRef(refName);
    if (ref == null) {
      return;
    }

    RefUpdate ru = allUsersRepo.updateRef(refName);
    ru.setExpectedOldObjectId(ref.getObjectId());
    ru.setNewObjectId(ObjectId.zeroId());
    ru.setForceUpdate(true);
    Result result = ru.delete();
    if (result != Result.FORCED) {
      throw new IOException(String.format("Failed to delete ref %s: %s", refName, result.name()));
    }
  }
}
