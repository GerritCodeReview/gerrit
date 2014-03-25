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

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.ChangeScreen;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.CommentVisibilityStrategy;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DateFormat;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DiffView;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadCommand;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DownloadScheme;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.TimeFormat;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

import java.util.ArrayList;
import java.util.List;

public class GetPreferences implements RestReadView<AccountResource> {
  public static final String REFS_META_USER = "refs/meta/user/";
  public static final String PREFERENCES = "preferences.config";
  public static final String MY = "my";
  public static final String KEY_URL = "url";
  public static final String KEY_TARGET = "target";
  public static final String KEY_ID = "id";

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> db;
  private final ProjectState allProjects;

  @Inject
  GetPreferences(Provider<CurrentUser> self, Provider<ReviewDb> db,
      ProjectCache projectCache) {
    this.self = self;
    this.db = db;
    this.allProjects = projectCache.getAllProjects();
  }

  @Override
  public PreferenceInfo apply(AccountResource rsrc)
      throws AuthException, ResourceNotFoundException, OrmException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }
    Account a = db.get().accounts().get(rsrc.getUser().getAccountId());
    if (a == null) {
      throw new ResourceNotFoundException();
    }
    return new PreferenceInfo(a.getGeneralPreferences(), allProjects);
  }

  static class PreferenceInfo {
    final String kind = "gerritcodereview#preferences";

    short changesPerPage;
    Boolean showSiteHeader;
    Boolean useFlashClipboard;
    DownloadScheme downloadScheme;
    DownloadCommand downloadCommand;
    Boolean copySelfOnEmail;
    DateFormat dateFormat;
    TimeFormat timeFormat;
    Boolean reversePatchSetOrder;
    Boolean showUsernameInReviewCategory;
    Boolean relativeDateInChangeTable;
    Boolean sizeBarInChangeTable;
    CommentVisibilityStrategy commentVisibilityStrategy;
    DiffView diffView;
    ChangeScreen changeScreen;
    List<TopMenu.MenuItem> my;

    PreferenceInfo(AccountGeneralPreferences p, ProjectState allProjects) {
      changesPerPage = p.getMaximumPageSize();
      showSiteHeader = p.isShowSiteHeader() ? true : null;
      useFlashClipboard = p.isUseFlashClipboard() ? true : null;
      downloadScheme = p.getDownloadUrl();
      downloadCommand = p.getDownloadCommand();
      copySelfOnEmail = p.isCopySelfOnEmails() ? true : null;
      dateFormat = p.getDateFormat();
      timeFormat = p.getTimeFormat();
      reversePatchSetOrder = p.isReversePatchSetOrder() ? true : null;
      showUsernameInReviewCategory = p.isShowUsernameInReviewCategory() ? true : null;
      relativeDateInChangeTable = p.isRelativeDateInChangeTable() ? true : null;
      sizeBarInChangeTable = p.isSizeBarInChangeTable() ? true : null;
      commentVisibilityStrategy = p.getCommentVisibilityStrategy();
      diffView = p.getDiffView();
      changeScreen = p.getChangeScreen();
      my = my(allProjects);
    }

    private List<TopMenu.MenuItem> my(ProjectState allProjects) {
      List<TopMenu.MenuItem> my = new ArrayList<TopMenu.MenuItem>();
      Config cfg = allProjects.getConfig(PREFERENCES, REFS_META_USER).get();
      for (String subsection : cfg.getSubsections(MY)) {
        my.add(new TopMenu.MenuItem(subsection, my(cfg, subsection, KEY_URL, "#/"),
            my(cfg, subsection, KEY_TARGET, null), my(cfg, subsection, KEY_ID, null)));
      }
      if (my.isEmpty()) {
        my.add(new TopMenu.MenuItem("Changes", "#/", ""));
        my.add(new TopMenu.MenuItem("Drafts", "#/q/is:draft", ""));
        my.add(new TopMenu.MenuItem("Draft Comments", "#/q/has:draft", ""));
        my.add(new TopMenu.MenuItem("Watched Changes", "#/q/is:watched+is:open", ""));
        my.add(new TopMenu.MenuItem("Starred Changes", "#/q/is:starred", ""));
      }
      return my;
    }

    private static String my(Config cfg, String subsection, String key,
        String defaultValue) {
      String value = cfg.getString(MY, subsection, key);
      return value != null ? value : defaultValue;
    }
  }
}
