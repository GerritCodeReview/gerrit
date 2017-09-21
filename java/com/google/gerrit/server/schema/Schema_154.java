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

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
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
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;

/** Migrate accounts to NoteDb. */
public class Schema_154 extends SchemaVersion {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private final Provider<PersonIdent> serverIdent;

  @Inject
  Schema_154(
      Provider<Schema_153> prior,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      @GerritPersonIdent Provider<PersonIdent> serverIdent) {
    super(prior);
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.serverIdent = serverIdent;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    try {
      try (Repository repo = repoManager.openRepository(allUsersName)) {
        ProgressMonitor pm = new TextProgressMonitor();
        pm.beginTask("Collecting accounts", ProgressMonitor.UNKNOWN);
        Set<Account> accounts = scanAccounts(db, pm);
        pm.endTask();
        pm.beginTask("Migrating accounts to NoteDb", accounts.size());
        for (Account account : accounts) {
          updateAccountInNoteDb(repo, account);
          pm.update(1);
        }
        pm.endTask();
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException("Migrating accounts to NoteDb failed", e);
    }
  }

  private Set<Account> scanAccounts(ReviewDb db, ProgressMonitor pm) throws SQLException {
    try (Statement stmt = newStatement(db);
        ResultSet rs =
            stmt.executeQuery(
                "SELECT account_id,"
                    + " registered_on,"
                    + " full_name, "
                    + " preferred_email,"
                    + " status,"
                    + " inactive"
                    + " FROM accounts")) {
      Set<Account> s = new HashSet<>();
      while (rs.next()) {
        Account a = new Account(new Account.Id(rs.getInt(1)), rs.getTimestamp(2));
        a.setFullName(rs.getString(3));
        a.setPreferredEmail(rs.getString(4));
        a.setStatus(rs.getString(5));
        a.setActive(rs.getString(6).equals("N"));
        s.add(a);
        pm.update(1);
      }
      return s;
    }
  }

  private void updateAccountInNoteDb(Repository allUsersRepo, Account account)
      throws IOException, ConfigInvalidException {
    MetaDataUpdate md =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, allUsersRepo);
    PersonIdent ident = serverIdent.get();
    md.getCommitBuilder().setAuthor(ident);
    md.getCommitBuilder().setCommitter(ident);
    AccountConfig accountConfig = new AccountConfig(null, account.getId());
    accountConfig.load(allUsersRepo);
    accountConfig.setAccount(account);
    accountConfig.commit(md);
  }
}
