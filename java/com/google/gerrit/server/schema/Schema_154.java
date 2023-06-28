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

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountConfig;
import com.google.gerrit.server.config.AllUsersName;
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
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevWalk;

/** Migrate accounts to NoteDb. */
public class Schema_154 extends SchemaVersion {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
  private final Stopwatch sw = Stopwatch.createStarted();

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
      try (Repository repo = repoManager.openRepository(allUsersName);
          ObjectInserter inserter = getPackInserterFirst(repo);
          ObjectReader reader = inserter.newReader();
          RevWalk rw = new RevWalk(reader)) {
        RefDatabase refDb = repo.getRefDatabase();
        BatchRefUpdate bru =
            refDb instanceof RefDirectory
                ? ((RefDirectory) refDb).newBatchUpdate(false)
                : refDb.newBatchUpdate();
        bru.setAtomic(refDb instanceof RefDirectory);
        ProgressMonitor pm = new TextProgressMonitor();
        pm.beginTask("Collecting accounts", ProgressMonitor.UNKNOWN);
        Set<Account> accounts = scanAccounts(db, pm);
        pm.endTask();
        pm.beginTask("Migrating accounts to NoteDb", accounts.size());
        for (Account account : accounts) {
          updateAccountInNoteDb(repo, account, bru, inserter, reader, rw);
        }
        inserter.flush();
        bru.execute(rw, pm);
        pm.endTask();
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException("Migrating accounts to NoteDb failed", e);
    }
  }

  private Set<Account> scanAccounts(ReviewDb db, ProgressMonitor pm) throws SQLException {
    Map<String, AccountSetter> fields = getFields(db);
    if (fields.isEmpty()) {
      logger.atWarning().log("Only account_id and registered_on fields are migrated for accounts");
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
    return ACCOUNT_FIELDS_MAP.entrySet().stream()
        .filter(e -> columns.contains(e.getKey()))
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private void updateAccountInNoteDb(
      Repository allUsersRepo,
      Account account,
      BatchRefUpdate bru,
      ObjectInserter oi,
      ObjectReader reader,
      RevWalk rw)
      throws IOException, ConfigInvalidException {
    MetaDataUpdate md =
        new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, allUsersRepo, bru);
    PersonIdent ident = serverIdent.get();
    md.getCommitBuilder().setAuthor(ident);
    md.getCommitBuilder().setCommitter(ident);
    new AccountConfig(account.getId(), allUsersName, allUsersRepo)
        .load()
        .setAccount(account)
        .commit(md, oi, reader, rw);
  }

  @FunctionalInterface
  private interface AccountSetter {
    void set(Account a, ResultSet rs, String field) throws SQLException;
  }
}
