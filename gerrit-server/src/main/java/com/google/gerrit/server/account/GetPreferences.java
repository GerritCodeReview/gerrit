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
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.List;

public class GetPreferences implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> db;

  @Inject
  GetPreferences(Provider<CurrentUser> self, Provider<ReviewDb> db) {
    this.self = self;
    this.db = db;
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
    return new PreferenceInfo(a.getGeneralPreferences());
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

    PreferenceInfo(AccountGeneralPreferences p) {
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
      my = my();
    }

    private List<TopMenu.MenuItem> my() {
      List<TopMenu.MenuItem> my = new ArrayList<TopMenu.MenuItem>();
      my.add(new TopMenu.MenuItem("Changes", "#/", ""));
      my.add(new TopMenu.MenuItem("Drafts", "#/q/is:draft", ""));
      my.add(new TopMenu.MenuItem("Draft Comments", "#/q/has:draft", ""));
      my.add(new TopMenu.MenuItem("Watched Changes", "#/q/is:watched+is:open", ""));
      my.add(new TopMenu.MenuItem("Starred Changes", "#/q/is:starred", ""));
      return my;
    }
  }
}
