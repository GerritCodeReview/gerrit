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
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.DateFormat;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.DiffView;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.DownloadCommand;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.ReviewCategoryStrategy;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.TimeFormat;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.VersionedAccountPreferences;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.UserConfigSections;
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

public class Schema_117 extends SchemaVersion {
  private final GitRepositoryManager mgr;
  private final AllUsersName allUsersName;
  private final PersonIdent serverUser;

  @Inject
  Schema_117(Provider<Schema_116> prior,
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
    Map<Account.Id, AccountGeneralPreferencesInfo> imports = new HashMap<>();
    try (Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
        ResultSet rs = stmt.executeQuery(
          "select "
          + "account_id, "
          + "maximum_page_size, "
          + "show_site_header, "
          + "use_flash_clipboard, "
          + "download_url, "
          + "download_command, "
          + "copy_self_on_email, "
          + "date_format, "
          + "time_format, "
          + "relative_date_in_change_table, "
          + "diff_view, "
          + "size_bar_in_change_table, "
          + "legacycid_in_change_table, "
          + "review_category_strategy, "
          + "mute_common_path_prefixes "
          + "from accounts")) {
        while (rs.next()) {
          AccountGeneralPreferencesInfo p =
              new AccountGeneralPreferencesInfo();
          Account.Id accountId = new Account.Id(rs.getInt(1));
          p.changesPerPage = (int)rs.getShort(2);
          p.showSiteHeader = toBoolean(rs.getString(3));
          p.useFlashClipboard = toBoolean(rs.getString(4));
          p.downloadScheme = rs.getString(5);
          p.downloadCommand = toDownloadCommand(rs.getString(6));
          p.copySelfOnEmail = toBoolean(rs.getString(7));
          p.dateFormat = toDateFormat(rs.getString(8));
          p.timeFormat = toTimeFormat(rs.getString(9));
          p.relativeDateInChangeTable = toBoolean(rs.getString(10));
          p.diffView = toDiffView(rs.getString(11));
          p.sizeBarInChangeTable = toBoolean(rs.getString(12));
          p.legacycidInChangeTable = toBoolean(rs.getString(13));
          p.reviewCategoryStrategy =
              toReviewCategoryStrategy(rs.getString(14));
          p.muteCommonPathPrefixes = toBoolean(rs.getString(15));
          imports.put(accountId, p);
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

      for (Map.Entry<Account.Id, AccountGeneralPreferencesInfo> e
          : imports.entrySet()) {
        VersionedAccountPreferences p =
            VersionedAccountPreferences.forUser(e.getKey());
        p.load(md);
        storeSection(p.getConfig(), UserConfigSections.GENERAL, null,
            e.getValue(), AccountGeneralPreferencesInfo.defaults());
        p.commit(md);
      }

      bru.execute(rw, NullProgressMonitor.INSTANCE);
    } catch (ConfigInvalidException | IOException ex) {
      throw new OrmException(ex);
    }
  }

  private static DownloadCommand toDownloadCommand(String v) {
    if (v == null) {
      return DownloadCommand.CHECKOUT;
    }
    return DownloadCommand.valueOf(v);
  }

  private static DateFormat toDateFormat(String v) {
    if (v == null) {
      return DateFormat.STD;
    }
    return DateFormat.valueOf(v);
  }

  private static TimeFormat toTimeFormat(String v) {
    if (v == null) {
      return TimeFormat.HHMM_12;
    }
    return TimeFormat.valueOf(v);
  }

  private static DiffView toDiffView(String v) {
    if (v == null) {
      return DiffView.SIDE_BY_SIDE;
    }
    return DiffView.valueOf(v);
  }

  private static ReviewCategoryStrategy toReviewCategoryStrategy(String v) {
    if (v == null) {
      return ReviewCategoryStrategy.NONE;
    }
    return ReviewCategoryStrategy.valueOf(v);
  }

  private static boolean toBoolean(String v) {
    Preconditions.checkState(!Strings.isNullOrEmpty(v));
    return v.equals("Y");
  }
}
