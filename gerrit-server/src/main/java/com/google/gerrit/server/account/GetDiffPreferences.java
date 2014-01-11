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
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class GetDiffPreferences implements RestReadView<AccountResource> {
  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> db;

  @Inject
  GetDiffPreferences(Provider<CurrentUser> self, Provider<ReviewDb> db) {
    this.self = self;
    this.db = db;
  }

  @Override
  public DiffPreferencesInfo apply(AccountResource rsrc)
      throws AuthException, OrmException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }

    Account.Id userId = rsrc.getUser().getAccountId();
    AccountDiffPreference a = db.get().accountDiffPreferences().get(userId);
    if (a == null) {
      a = new AccountDiffPreference(userId);
    }
    return DiffPreferencesInfo.parse(a);
  }

  public static class DiffPreferencesInfo {
    static DiffPreferencesInfo parse(AccountDiffPreference p) {
      DiffPreferencesInfo info = new DiffPreferencesInfo();
      info.context = p.getContext();
      info.expand_all_comments = p.isExpandAllComments() ? true : null;
      info.ignore_whitespace = p.getIgnoreWhitespace();
      info.intraline_difference = p.isIntralineDifference() ? true : null;
      info.line_length = p.getLineLength();
      info.manual_review = p.isManualReview() ? true : null;
      info.retain_header = p.isRetainHeader() ? true : null;
      info.show_line_endings = p.isShowLineEndings() ? true : null;
      info.show_tabs = p.isShowTabs() ? true : null;
      info.show_whitespace_errors = p.isShowWhitespaceErrors() ? true : null;
      info.skip_deleted = p.isSkipDeleted() ? true : null;
      info.skip_uncommented = p.isSkipUncommented() ? true : null;
      info.hide_top_menu = p.isHideTopMenu() ? true : null;
      info.hide_line_numbers = p.isHideLineNumbers() ? true : null;
      info.syntax_highlighting = p.isSyntaxHighlighting() ? true : null;
      info.tab_size = p.getTabSize();
      info.render_entire_file = p.isRenderEntireFile() ? true : null;
      return info;
    }

    public short context;
    public Boolean expand_all_comments;
    public Whitespace ignore_whitespace;
    public Boolean intraline_difference;
    public int line_length;
    public Boolean manual_review;
    public Boolean retain_header;
    public Boolean show_line_endings;
    public Boolean show_tabs;
    public Boolean show_whitespace_errors;
    public Boolean skip_deleted;
    public Boolean skip_uncommented;
    public Boolean syntax_highlighting;
    public Boolean hide_top_menu;
    public Boolean hide_line_numbers;
    public Boolean render_entire_file;
    public int tab_size;
  }
}
