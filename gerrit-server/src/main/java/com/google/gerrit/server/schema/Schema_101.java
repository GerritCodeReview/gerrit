// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.gerrit.server.config.ConfigUtil.storeSection;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.common.DiffPreferencesInfo;
import com.google.gerrit.extensions.common.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.common.Theme;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class Schema_101 extends SchemaVersion {
  private final GitRepositoryManager mgr;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;

  @Inject
  Schema_101(Provider<Schema_100> prior,
      GitRepositoryManager mgr,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.mgr = mgr;
    this.allUsersName = allUsersName;
    this.serverUser = serverUser;
  }

/*
 -- test insert
 insert into ACCOUNT_DIFF_PREFERENCES values (100, 'Y', 'Y', 'Y', 'N', 'N', 100, 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 'N', 16, 'DEFAULT', 1000000, 'Y', 'Y');

 COLUMN_NAME                 | TYPE
 ----------------------------+-----------------------------
 ID                          | INTEGER DEFAULT 0 NOT NULL
 CONTEXT                     | SMALLINT DEFAULT 0 NOT NULL
 EXPAND_ALL_COMMENTS         | CHAR(1) DEFAULT 'N' NOT NULL
 HIDE_LINE_NUMBERS           | CHAR(1) DEFAULT 'N' NOT NULL
 HIDE_TOP_MENU               | CHAR(1) DEFAULT 'N' NOT NULL
 IGNORE_WHITESPACE           | CHAR(1) DEFAULT ' ' NOT NULL
 INTRALINE_DIFFERENCE        | CHAR(1) DEFAULT 'N' NOT NULL
 LINE_LENGTH                 | INTEGER DEFAULT 0 NOT NULL
 MANUAL_REVIEW               | CHAR(1) DEFAULT 'N' NOT NULL
 RENDER_ENTIRE_FILE          | CHAR(1) DEFAULT 'N' NOT NULL
 RETAIN_HEADER               | CHAR(1) DEFAULT 'N' NOT NULL
 SHOW_LINE_ENDINGS           | CHAR(1) DEFAULT 'N' NOT NULL
 SHOW_TABS                   | CHAR(1) DEFAULT 'N' NOT NULL
 SHOW_WHITESPACE_ERRORS      | CHAR(1) DEFAULT 'N' NOT NULL
 SKIP_DELETED                | CHAR(1) DEFAULT 'N' NOT NULL
 SKIP_UNCOMMENTED            | CHAR(1) DEFAULT 'N' NOT NULL
 SYNTAX_HIGHLIGHTING         | CHAR(1) DEFAULT 'N' NOT NULL
 TAB_SIZE                    | INTEGER DEFAULT 0 NOT NULL
 THEME                       | VARCHAR(20)
 HIDE_EMPTY_PANE             | CHAR(1) DEFAULT 'N' NOT NULL
 AUTO_HIDE_DIFF_TABLE_HEADER | CHAR(1) DEFAULT 'N' NOT NULL
*/

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui)
      throws OrmException, SQLException {
    Map<Account.Id, DiffPreferencesInfo> toImport = new HashMap<>();
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet rs = stmt.executeQuery(
          "SELECT "
          + "id, "
          + "context, "
          + "expand_all_comments, "
          + "hide_line_numbers, "
          + "hide_top_menu, "
          + "ignore_whitespace, "
          + "intraline_difference, "
          + "line_length, "
          + "manual_review, "
          + "render_entire_file, "
          + "retain_header, "
          + "show_line_endings, "
          + "show_tabs, "
          + "show_whitespace_errors, "
          + "skip_deleted, "
          + "skip_uncommented, "
          + "syntax_highlighting, "
          + "tab_size, "
          + "theme, "
          + "hide_empty_pane, "
          + "auto_hide_diff_table_header "
          + "FROM account_diff_preferences");
      try {
        while (rs.next()) {
          Account.Id accountId = new Account.Id(rs.getInt(1));
          DiffPreferencesInfo prefs = new DiffPreferencesInfo();
          prefs.context = rs.getShort(2);
          prefs.expandAllComments = toBoolean(rs.getString(3));
          prefs.hideLineNumbers = toBoolean(rs.getString(4));
          prefs.hideTopMenu = toBoolean(rs.getString(5));
          // Enum with char as value
          prefs.ignoreWhitespace = toWhitespace(rs.getString(6));
          prefs.intralineDifference = toBoolean(rs.getString(7));
          prefs.lineLength = rs.getInt(8);
          prefs.manualReview = toBoolean(rs.getString(9));
          prefs.renderEntireFile = toBoolean(rs.getString(10));
          prefs.retainHeader = toBoolean(rs.getString(11));
          prefs.showLineEndings = toBoolean(rs.getString(12));
          prefs.showTabs = toBoolean(rs.getString(13));
          prefs.showWhitespaceErrors = toBoolean(rs.getString(14));
          prefs.skipDeleted = toBoolean(rs.getString(15));
          prefs.skipUncommented = toBoolean(rs.getString(16));
          prefs.syntaxHighlighting = toBoolean(rs.getString(17));
          prefs.tabSize = rs.getInt(18);
          // Enum with name as values; can be null
          prefs.theme = toTheme(rs.getString(19));
          prefs.hideEmptyPane = toBoolean(rs.getString(20));
          prefs.autoHideDiffTableHeader = toBoolean(rs.getString(21));
          toImport.put(accountId, prefs);
        }
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }

    Repository git;
    try {
      git = mgr.openRepository(allUsersName);
    } catch (IOException ex) {
      throw new OrmException(ex);
    }

    try {
      MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED,
          allUsersName, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);

      for (Map.Entry<Account.Id, DiffPreferencesInfo> e : toImport.entrySet()) {
        VersionedAccountPreferences prefs =
            VersionedAccountPreferences.forUser(e.getKey());
        prefs.load(md);
        storeSection(prefs.getConfig(), "diff", null, e.getValue());
        prefs.commit(md);
      }
    } catch (ConfigInvalidException | IOException ex) {
      throw new OrmException(ex);
    } finally {
      git.close();
    }
  }

  private Theme toTheme(String v) {
    if (v == null) {
      return Theme.DEFAULT;
    }
    return Theme.valueOf(v);
  }

  private Whitespace toWhitespace(String v) {
    Preconditions.checkNotNull(v);
    if (v.isEmpty()) {
      return Whitespace.IGNORE_NONE;
    }
    Whitespace r = PatchListKey.WHITESPACE_TYPES.inverse().get(v.charAt(0));
    if (r == null) {
      throw new IllegalArgumentException("Cannot find Whitespace type for: "
          + v);
    }
    return r;
  }

  private boolean toBoolean(String v) {
    Preconditions.checkState(!Strings.isNullOrEmpty(v));
    return v.equals("Y");
  }
}
