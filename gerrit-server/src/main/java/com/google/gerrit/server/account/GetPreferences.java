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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DateFormat;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DiffView;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.ReviewCategoryStrategy;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.TimeFormat;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.UserConfigSections;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
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

  @Inject
  GetPreferences(Provider<CurrentUser> self, Provider<ReviewDb> db,
      AllUsersName allUsersName,
      GitRepositoryManager gitMgr) {
    this.self = self;
    this.db = db;
    this.allUsersName = allUsersName;
    this.gitMgr = gitMgr;
  }

  @Override
  public PreferenceInfo apply(AccountResource rsrc)
      throws AuthException,
      ResourceNotFoundException,
      OrmException,
      IOException,
      ConfigInvalidException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }
    Account a = db.get().accounts().get(rsrc.getUser().getAccountId());
    if (a == null) {
      throw new ResourceNotFoundException();
    }

    try (Repository git = gitMgr.openRepository(allUsersName)) {
      VersionedAccountPreferences p =
          VersionedAccountPreferences.forUser(rsrc.getUser().getAccountId());
      p.load(git);
      return new PreferenceInfo(a.getGeneralPreferences(), p, git);
    }
  }

  public static class PreferenceInfo {
    Short changesPerPage;
    Boolean showSiteHeader;
    Boolean useFlashClipboard;
    String downloadScheme;
    DownloadCommand downloadCommand;
    Boolean copySelfOnEmail;
    DateFormat dateFormat;
    TimeFormat timeFormat;
    Boolean relativeDateInChangeTable;
    Boolean sizeBarInChangeTable;
    Boolean legacycidInChangeTable;
    Boolean muteCommonPathPrefixes;
    Boolean publishDraftCommentsOnPush;
    ReviewCategoryStrategy reviewCategoryStrategy;
    DiffView diffView;
    List<TopMenu.MenuItem> my;
    Map<String, String> urlAliases;

    public PreferenceInfo(AccountGeneralPreferences p,
        VersionedAccountPreferences v, Repository allUsers) {
      if (p != null) {
        changesPerPage = p.getMaximumPageSize();
        showSiteHeader = p.isShowSiteHeader() ? true : null;
        useFlashClipboard = p.isUseFlashClipboard() ? true : null;
        downloadScheme = p.getDownloadUrl();
        downloadCommand = p.getDownloadCommand();
        copySelfOnEmail = p.isCopySelfOnEmails() ? true : null;
        dateFormat = p.getDateFormat();
        timeFormat = p.getTimeFormat();
        relativeDateInChangeTable = p.isRelativeDateInChangeTable() ? true : null;
        sizeBarInChangeTable = p.isSizeBarInChangeTable() ? true : null;
        legacycidInChangeTable = p.isLegacycidInChangeTable() ? true : null;
        muteCommonPathPrefixes = p.isMuteCommonPathPrefixes() ? true : null;
        publishDraftCommentsOnPush = p.isPublishDraftCommentsOnPush() ? true : null;
        reviewCategoryStrategy = p.getReviewCategoryStrategy();
        diffView = p.getDiffView();
      }
      loadFromAllUsers(v, allUsers);
    }

    private void loadFromAllUsers(VersionedAccountPreferences v,
        Repository allUsers) {
      my = my(v);
      if (my.isEmpty() && !v.isDefaults()) {
        try {
          VersionedAccountPreferences d = VersionedAccountPreferences.forDefault();
          d.load(allUsers);
          my = my(d);
        } catch (ConfigInvalidException | IOException e) {
          log.warn("cannot read default preferences", e);
        }
      }
      if (my.isEmpty()) {
        my.add(new TopMenu.MenuItem("Changes", "#/dashboard/self", null));
        my.add(new TopMenu.MenuItem("Drafts", "#/q/owner:self+is:draft", null));
        my.add(new TopMenu.MenuItem("Draft Comments", "#/q/has:draft", null));
        my.add(new TopMenu.MenuItem("Edits", "#/q/has:edit", null));
        my.add(new TopMenu.MenuItem("Watched Changes", "#/q/is:watched+is:open", null));
        my.add(new TopMenu.MenuItem("Starred Changes", "#/q/is:starred", null));
        my.add(new TopMenu.MenuItem("Groups", "#/groups/self", null));
      }

      urlAliases = urlAliases(v);
    }

    private List<TopMenu.MenuItem> my(VersionedAccountPreferences v) {
      List<TopMenu.MenuItem> my = new ArrayList<>();
      Config cfg = v.getConfig();
      for (String subsection : cfg.getSubsections(UserConfigSections.MY)) {
        String url = my(cfg, subsection, KEY_URL, "#/");
        String target = my(cfg, subsection, KEY_TARGET,
            url.startsWith("#") ? null : "_blank");
        my.add(new TopMenu.MenuItem(
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
  }
}
