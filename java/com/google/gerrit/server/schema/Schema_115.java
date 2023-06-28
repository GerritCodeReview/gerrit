// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.config.ConfigUtil.storeSection;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.client.Theme;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class Schema_115 extends SchemaVersion {
  private static final String CREATE_ACCOUNT_MSG = "Create Account";
  private final GitRepositoryManager mgr;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;

  @Inject
  Schema_115(
      Provider<Schema_114> prior,
      GitRepositoryManager mgr,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.mgr = mgr;
    this.allUsersName = allUsersName;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    Map<Account.Id, DiffPreferencesInfo> imports = new HashMap<>();
    HashMap<Account.Id, Timestamp> registeredOnByAccount = new HashMap<>();
    try (Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
        ResultSet rs =
            stmt.executeQuery(
                "SELECT *, accounts.registered_on FROM account_diff_preferences "
                    + "JOIN accounts ON account_diff_preferences.id=accounts.account_id")) {
      Set<String> availableColumns = getColumns(rs);
      while (rs.next()) {
        Account.Id accountId = new Account.Id(rs.getInt("id"));
        DiffPreferencesInfo prefs = new DiffPreferencesInfo();
        if (availableColumns.contains("context")) {
          prefs.context = (int) rs.getShort("context");
        }
        if (availableColumns.contains("expand_all_comments")) {
          prefs.expandAllComments = toBoolean(rs.getString("expand_all_comments"));
        }
        if (availableColumns.contains("hide_line_numbers")) {
          prefs.hideLineNumbers = toBoolean(rs.getString("hide_line_numbers"));
        }
        if (availableColumns.contains("hide_top_menu")) {
          prefs.hideTopMenu = toBoolean(rs.getString("hide_top_menu"));
        }
        if (availableColumns.contains("ignore_whitespace")) {
          // Enum with char as value
          prefs.ignoreWhitespace = toWhitespace(rs.getString("ignore_whitespace"));
        }
        if (availableColumns.contains("intraline_difference")) {
          prefs.intralineDifference = toBoolean(rs.getString("intraline_difference"));
        }
        if (availableColumns.contains("line_length")) {
          prefs.lineLength = rs.getInt("line_length");
        }
        if (availableColumns.contains("manual_review")) {
          prefs.manualReview = toBoolean(rs.getString("manual_review"));
        }
        if (availableColumns.contains("render_entire_file")) {
          prefs.renderEntireFile = toBoolean(rs.getString("render_entire_file"));
        }
        if (availableColumns.contains("retain_header")) {
          prefs.retainHeader = toBoolean(rs.getString("retain_header"));
        }
        if (availableColumns.contains("show_line_endings")) {
          prefs.showLineEndings = toBoolean(rs.getString("show_line_endings"));
        }
        if (availableColumns.contains("show_tabs")) {
          prefs.showTabs = toBoolean(rs.getString("show_tabs"));
        }
        if (availableColumns.contains("show_whitespace_errors")) {
          prefs.showWhitespaceErrors = toBoolean(rs.getString("show_whitespace_errors"));
        }
        if (availableColumns.contains("skip_deleted")) {
          prefs.skipDeleted = toBoolean(rs.getString("skip_deleted"));
        }
        if (availableColumns.contains("skip_uncommented")) {
          prefs.skipUncommented = toBoolean(rs.getString("skip_uncommented"));
        }
        if (availableColumns.contains("syntax_highlighting")) {
          prefs.syntaxHighlighting = toBoolean(rs.getString("syntax_highlighting"));
        }
        if (availableColumns.contains("tab_size")) {
          prefs.tabSize = rs.getInt("tab_size");
        }
        if (availableColumns.contains("theme")) {
          // Enum with name as values; can be null
          prefs.theme = toTheme(rs.getString("theme"));
        }
        if (availableColumns.contains("hide_empty_pane")) {
          prefs.hideEmptyPane = toBoolean(rs.getString("hide_empty_pane"));
        }
        if (availableColumns.contains("auto_hide_diff_table_header")) {
          prefs.autoHideDiffTableHeader = toBoolean(rs.getString("auto_hide_diff_table_header"));
        }
        if (availableColumns.contains("registered_on")) {
          registeredOnByAccount.put(accountId, rs.getTimestamp("registered_on"));
        }
        imports.put(accountId, prefs);
      }
    }

    if (imports.isEmpty()) {
      return;
    }

    try (Repository git = mgr.openRepository(allUsersName);
        ObjectInserter inserter = getPackInserterFirst(git);
        ObjectReader reader = inserter.newReader();
        RevWalk rw = new RevWalk(reader)) {
      RefDatabase refDb = git.getRefDatabase();
      BatchRefUpdate bru =
          refDb instanceof RefDirectory
              ? ((RefDirectory) refDb).newBatchUpdate(false)
              : refDb.newBatchUpdate();
      bru.setAtomic(refDb instanceof RefDirectory);
      ObjectId emptyTree = emptyTree(inserter);
      for (Map.Entry<Account.Id, DiffPreferencesInfo> e : imports.entrySet()) {
        try (MetaDataUpdate md =
            new MetaDataUpdate(GitReferenceUpdated.DISABLED, allUsersName, git, bru)) {
          Account.Id accountId = e.getKey();
          VersionedAccountPreferences p = VersionedAccountPreferences.forUser(accountId);
          p.load(md);
          BatchMetaDataUpdate batch = p.openUpdate(md, inserter, reader, rw);
          if (p.getRevision() == null) {
            batch.write(
                buildCommit(
                    new PersonIdent(serverUser, registeredOnByAccount.get(accountId)),
                    emptyTree,
                    CREATE_ACCOUNT_MSG));
          }
          md.getCommitBuilder().setAuthor(serverUser);
          md.getCommitBuilder().setCommitter(serverUser);
          storeSection(
              p.getConfig(),
              UserConfigSections.DIFF,
              null,
              e.getValue(),
              DiffPreferencesInfo.defaults());
          batch.write(md.getCommitBuilder());
          batch.commit();
        }
      }

      inserter.flush();
      bru.execute(rw, NullProgressMonitor.INSTANCE);
    } catch (ConfigInvalidException | IOException ex) {
      throw new OrmException(ex);
    }
  }

  private Set<String> getColumns(ResultSet rs) throws SQLException {
    ResultSetMetaData metaData = rs.getMetaData();
    int columnCount = metaData.getColumnCount();
    Set<String> columns = new HashSet<>(columnCount);
    for (int i = 1; i <= columnCount; i++) {
      columns.add(metaData.getColumnLabel(i).toLowerCase());
    }
    return columns;
  }

  private static Theme toTheme(String v) {
    if (v == null) {
      return Theme.DEFAULT;
    }
    return Theme.valueOf(v);
  }

  private static Whitespace toWhitespace(String v) {
    requireNonNull(v);
    if (v.isEmpty()) {
      return Whitespace.IGNORE_NONE;
    }
    Whitespace r = PatchListKey.WHITESPACE_TYPES.inverse().get(v.charAt(0));
    if (r == null) {
      throw new IllegalArgumentException("Cannot find Whitespace type for: " + v);
    }
    return r;
  }

  private static boolean toBoolean(String v) {
    checkState(!Strings.isNullOrEmpty(v));
    return v.equals("Y");
  }
}
