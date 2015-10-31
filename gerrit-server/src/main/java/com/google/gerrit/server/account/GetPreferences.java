// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.config.ConfigUtil.loadSection;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.DateFormat;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.DiffView;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.DownloadCommand;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.ReviewCategoryStrategy;
import com.google.gerrit.extensions.client.AccountGeneralPreferencesInfo.TimeFormat;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class GetPreferences implements RestReadView<AccountResource> {
  private static final Logger log = LoggerFactory.getLogger(GetPreferences.class);

  public static final String KEY_URL = "url";
  public static final String KEY_TARGET = "target";
  public static final String KEY_ID = "id";
  public static final String URL_ALIAS = "urlAlias";
  public static final String KEY_MATCH = "match";
  public static final String KEY_TOKEN = "token";

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> db;
  private final AllUsersName allUsersName;
  private final GitRepositoryManager gitMgr;
  private final boolean readFromGit;

  @Inject
  GetPreferences(Provider<CurrentUser> self,
      @GerritServerConfig Config cfg,
      Provider<ReviewDb> db,
      AllUsersName allUsersName,
      GitRepositoryManager gitMgr) {
    this.self = self;
    this.db = db;
    this.allUsersName = allUsersName;
    this.gitMgr = gitMgr;
    readFromGit = cfg.getBoolean("user", null, "readPrefsFromGit", false);
  }

  @Override
  public AccountGeneralPreferencesInfo apply(AccountResource rsrc)
      throws AuthException, ResourceNotFoundException, OrmException,
      IOException, ConfigInvalidException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }

    Account.Id accountId = rsrc.getUser().getAccountId();
    return readFromGit
        ? readFromGit(accountId, gitMgr, allUsersName, null)
        : readFromDb(accountId);
  }

  private AccountGeneralPreferencesInfo readFromDb(Id accountId)
      throws IOException, ConfigInvalidException, RepositoryNotFoundException,
      OrmException {
    Account a = db.get().accounts().get(accountId);
    AccountGeneralPreferencesInfo r = nullify(initFromDb(
        a.getGeneralPreferences()));

    try (Repository allUsers = gitMgr.openRepository(allUsersName)) {
      VersionedAccountPreferences p =
          VersionedAccountPreferences.forUser(accountId);
      p.load(allUsers);

      return loadFromAllUsers(r, p, allUsers);
    }
  }

  public static AccountGeneralPreferencesInfo readFromGit(Account.Id id,
      GitRepositoryManager gitMgr, AllUsersName allUsersName,
      AccountGeneralPreferencesInfo in) throws IOException,
          ConfigInvalidException, RepositoryNotFoundException {
    try (Repository allUsers = gitMgr.openRepository(allUsersName)) {
      VersionedAccountPreferences p =
          VersionedAccountPreferences.forUser(id);
      p.load(allUsers);

      AccountGeneralPreferencesInfo r =
          loadSection(p.getConfig(), UserConfigSections.GENERAL, null,
          new AccountGeneralPreferencesInfo(),
          AccountGeneralPreferencesInfo.defaults(), in);

      return loadFromAllUsers(r, p, allUsers);
    }
  }

  public static AccountGeneralPreferencesInfo loadFromAllUsers(
      AccountGeneralPreferencesInfo r, VersionedAccountPreferences v,
      Repository allUsers) {
    r.my = my(v);
    if (r.my.isEmpty() && !v.isDefaults()) {
      try {
        VersionedAccountPreferences d = VersionedAccountPreferences.forDefault();
        d.load(allUsers);
        r.my = my(d);
      } catch (ConfigInvalidException | IOException e) {
        log.warn("cannot read default preferences", e);
      }
    }
    if (r.my.isEmpty()) {
      r.my.add(new MenuItem("Changes", "#/dashboard/self", null));
      r.my.add(new MenuItem("Drafts", "#/q/owner:self+is:draft", null));
      r.my.add(new MenuItem("Draft Comments", "#/q/has:draft", null));
      r.my.add(new MenuItem("Edits", "#/q/has:edit", null));
      r.my.add(new MenuItem("Watched Changes", "#/q/is:watched+is:open",
          null));
      r.my.add(new MenuItem("Starred Changes", "#/q/is:starred", null));
      r.my.add(new MenuItem("Groups", "#/groups/self", null));
    }

    r.urlAliases = urlAliases(v);
    return r;
  }

  private static List<MenuItem> my(VersionedAccountPreferences v) {
    List<MenuItem> my = new ArrayList<>();
    Config cfg = v.getConfig();
    for (String subsection : cfg.getSubsections(UserConfigSections.MY)) {
      String url = my(cfg, subsection, KEY_URL, "#/");
      String target = my(cfg, subsection, KEY_TARGET,
          url.startsWith("#") ? null : "_blank");
      my.add(new MenuItem(
          subsection, url, target,
          my(cfg, subsection, KEY_ID, null)));
    }
    return my;
  }

  private static String my(Config cfg, String subsection, String key,
      String defaultValue) {
    String val = cfg.getString(UserConfigSections.MY, subsection, key);
    return !Strings.isNullOrEmpty(val) ? val : defaultValue;
  }

  private static Map<String, String> urlAliases(VersionedAccountPreferences v) {
    HashMap<String, String> urlAliases = new HashMap<>();
    Config cfg = v.getConfig();
    for (String subsection : cfg.getSubsections(URL_ALIAS)) {
      urlAliases.put(cfg.getString(URL_ALIAS, subsection, KEY_MATCH),
         cfg.getString(URL_ALIAS, subsection, KEY_TOKEN));
    }
    return !urlAliases.isEmpty() ? urlAliases : null;
  }

  static AccountGeneralPreferencesInfo initFromDb(AccountGeneralPreferences a) {
    AccountGeneralPreferencesInfo p = AccountGeneralPreferencesInfo.defaults();
    if (a != null) {
      p.changesPerPage = (int)a.getMaximumPageSize();
      p.showSiteHeader = a.isShowSiteHeader();
      p.useFlashClipboard = a.isUseFlashClipboard();
      p.downloadScheme = a.getDownloadUrl();
      if (a.getDownloadCommand() != null) {
        p.downloadCommand = DownloadCommand.valueOf(
            a.getDownloadCommand().name());
      }
      p.copySelfOnEmail = a.isCopySelfOnEmails();
      p.dateFormat = DateFormat.valueOf(a.getDateFormat().name());
      p.timeFormat = TimeFormat.valueOf(a.getTimeFormat().name());
      p.relativeDateInChangeTable = a.isRelativeDateInChangeTable();
      p.sizeBarInChangeTable = a.isSizeBarInChangeTable();
      p.legacycidInChangeTable = a.isLegacycidInChangeTable();
      p.muteCommonPathPrefixes = a.isMuteCommonPathPrefixes();
      p.reviewCategoryStrategy = ReviewCategoryStrategy.valueOf(
          a.getReviewCategoryStrategy().name());
      p.diffView = DiffView.valueOf(a.getDiffView().name());
    }

    return p;
  }

  private static AccountGeneralPreferencesInfo nullify(
      AccountGeneralPreferencesInfo p) {
    p.showSiteHeader = b(p.showSiteHeader);
    p.useFlashClipboard = b(p.useFlashClipboard);
    p.copySelfOnEmail = b(p.copySelfOnEmail);
    p.relativeDateInChangeTable = b(p.relativeDateInChangeTable);
    p.legacycidInChangeTable = b(p.legacycidInChangeTable);
    p.muteCommonPathPrefixes = b(p.muteCommonPathPrefixes);
    return p;
  }

  private static Boolean b(Boolean b) {
    if (b == null) {
      return null;
    }
    return b ? Boolean.TRUE : null;
  }
}
