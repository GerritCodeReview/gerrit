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

import static com.google.gerrit.server.config.ConfigUtil.storeSection;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.client.Theme;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gerrit.server.patch.PatchListKey;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class Schema_115 extends SchemaVersion {
  private final GitRepositoryManager mgr;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;

  @Inject
  Schema_115(Provider<Schema_114> prior,
      GitRepositoryManager mgr,
      AllUsersName allUsersName,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.mgr = mgr;
    this.allUsersName = allUsersName;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui)
      throws OrmException, SQLException {
    Map<Account.Id, DiffPreferencesInfo> imports = new HashMap<>();
    try (Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
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
          + "FROM account_diff_preferences")) {
        while (rs.next()) {
          Account.Id accountId = new Account.Id(rs.getInt(1));
          DiffPreferencesInfo prefs = new DiffPreferencesInfo();
          prefs.context = (int)rs.getShort(2);
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
          imports.put(accountId, prefs);
        }
    }

    if (imports.isEmpty()) {
      return;
    }

    try (Repository git = mgr.openRepository(allUsersName);
        RevWalk rw = new RevWalk(git)) {
      BatchRefUpdate bru = git.getRefDatabase().newBatchUpdate();
      MetaDataUpdate md = new MetaDataUpdate(GitReferenceUpdated.DISABLED,
          allUsersName, git, bru);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);

      for (Map.Entry<Account.Id, DiffPreferencesInfo> e : imports.entrySet()) {
        VersionedAccountPreferences p =
            VersionedAccountPreferences.forUser(e.getKey());
        p.load(md);
        storeSection(p.getConfig(), UserConfigSections.DIFF, null,
            e.getValue(), DiffPreferencesInfo.defaults());
        p.commit(md);
      }

      bru.execute(rw, NullProgressMonitor.INSTANCE);
    } catch (ConfigInvalidException | IOException ex) {
      throw new OrmException(ex);
    }
  }

  private static Theme toTheme(String v) {
    if (v == null) {
      return Theme.DEFAULT;
    }
    return Theme.valueOf(v);
  }

  private static Whitespace toWhitespace(String v) {
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

  private static boolean toBoolean(String v) {
    Preconditions.checkState(!Strings.isNullOrEmpty(v));
    return v.equals("Y");
  }
}
