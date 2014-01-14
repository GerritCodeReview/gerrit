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

    short changes_per_page;
    Boolean show_site_header;
    Boolean use_flash_clipboard;
    DownloadScheme download_scheme;
    DownloadCommand download_command;
    Boolean copy_self_on_email;
    DateFormat date_format;
    TimeFormat time_format;
    Boolean reverse_patch_set_order;
    Boolean showUsername_in_review_category;
    Boolean relative_date_in_change_table;
    Boolean size_bar_in_change_table;
    CommentVisibilityStrategy comment_visibility_strategy;
    DiffView diff_view;
    ChangeScreen change_screen;

    PreferenceInfo(AccountGeneralPreferences p) {
      changes_per_page = p.getMaximumPageSize();
      show_site_header = p.isShowSiteHeader() ? true : null;
      use_flash_clipboard = p.isUseFlashClipboard() ? true : null;
      download_scheme = p.getDownloadUrl();
      download_command = p.getDownloadCommand();
      copy_self_on_email = p.isCopySelfOnEmails() ? true : null;
      date_format = p.getDateFormat();
      time_format = p.getTimeFormat();
      reverse_patch_set_order = p.isReversePatchSetOrder() ? true : null;
      showUsername_in_review_category = p.isShowUsernameInReviewCategory() ? true : null;
      relative_date_in_change_table = p.isRelativeDateInChangeTable() ? true : null;
      size_bar_in_change_table = p.isSizeBarInChangeTable() ? true : null;
      comment_visibility_strategy = p.getCommentVisibilityStrategy();
      diff_view = p.getDiffView();
      change_screen = p.getChangeScreen();
    }
  }
}
