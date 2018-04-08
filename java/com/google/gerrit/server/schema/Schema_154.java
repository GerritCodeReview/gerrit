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

import static java.util.stream.Collectors.toMap;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Migrate accounts to NoteDb. */
public class Schema_154 extends SchemaVersion {
  private static final Logger log = LoggerFactory.getLogger(Schema_154.class);
  private static final String TABLE = "accounts";
  private static final ImmutableMap<String, AccountSetter> ACCOUNT_FIELDS_MAP =
      ImmutableMap.<String, AccountSetter>builder()
          .put("full_name", (a, rs, field) -> a.setFullName(rs.getString(field)))
          .put("preferred_email", (a, rs, field) -> a.setPreferredEmail(rs.getString(field)))
          .put("status", (a, rs, field) -> a.setStatus(rs.getString(field)))
          .put("inactive", (a, rs, field) -> a.setActive(rs.getString(field).equals("N")))
          .build();

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
    Map<String, AccountSetter> fields = getFields(db);
    if (fields.isEmpty()) {
      log.warn("Only account_id and registered_on fields are migrated for accounts");
    }

    List<String> queryFields = new ArrayList<>();
    queryFields.add("account_id");
    queryFields.add("registered_on");
    queryFields.addAll(fields.keySet());
    String query = "SELECT " + String.join(", ", queryFields) + String.format(" FROM %s", TABLE);
    try (Statement stmt = newStatement(db);
        ResultSet rs = stmt.executeQuery(query)) {
      Set<Account> s = new HashSet<>();
      while (rs.next()) {
        Account a = new Account(new Account.Id(rs.getInt(1)), rs.getTimestamp(2));
        for (Map.Entry<String, AccountSetter> field : fields.entrySet()) {
          field.getValue().set(a, rs, field.getKey());
        }
        s.add(a);
        pm.update(1);
      }
      return s;
    }
  }

  private Map<String, AccountSetter> getFields(ReviewDb db) throws SQLException {
    JdbcSchema schema = (JdbcSchema) db;
    Connection connection = schema.getConnection();
    Set<String> columns = schema.getDialect().listColumns(connection, TABLE);
    return ACCOUNT_FIELDS_MAP
        .entrySet()
        .stream()
        .filter(e -> columns.contains(e.getKey()))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private void updateAccountInNoteDb(Repository allUsersRepo, Account account)
      throws IOException, ConfigInvalidException {
    MetaDataUpdate md =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, allUsersRepo);
    PersonIdent ident = serverIdent.get();
    md.getCommitBuilder().setAuthor(ident);
    md.getCommitBuilder().setCommitter(ident);
    new AccountConfig(account.getId(), allUsersRepo).load().setAccount(account).commit(md);
  }

  @FunctionalInterface
  private interface AccountSetter {
    void set(Account a, ResultSet rs, String field) throws SQLException;
  }
}
