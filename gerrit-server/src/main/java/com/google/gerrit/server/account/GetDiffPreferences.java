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

import com.google.gerrit.extensions.client.Theme;
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
import com.google.inject.Singleton;

@Singleton
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
      info.expandAllComments = p.isExpandAllComments() ? true : null;
      info.ignoreWhitespace = p.getIgnoreWhitespace();
      info.intralineDifference = p.isIntralineDifference() ? true : null;
      info.lineLength = p.getLineLength();
      info.manualReview = p.isManualReview() ? true : null;
      info.retainHeader = p.isRetainHeader() ? true : null;
      info.showLineEndings = p.isShowLineEndings() ? true : null;
      info.showTabs = p.isShowTabs() ? true : null;
      info.showWhitespaceErrors = p.isShowWhitespaceErrors() ? true : null;
      info.skipDeleted = p.isSkipDeleted() ? true : null;
      info.skipUncommented = p.isSkipUncommented() ? true : null;
      info.hideTopMenu = p.isHideTopMenu() ? true : null;
      info.autoHideDiffTableHeader = p.isAutoHideDiffTableHeader() ? true : null;
      info.hideLineNumbers = p.isHideLineNumbers() ? true : null;
      info.syntaxHighlighting = p.isSyntaxHighlighting() ? true : null;
      info.tabSize = p.getTabSize();
      info.renderEntireFile = p.isRenderEntireFile() ? true : null;
      info.hideEmptyPane = p.isHideEmptyPane() ? true : null;
      info.theme = p.getTheme();
      return info;
    }

    public short context;
    public Boolean expandAllComments;
    public Whitespace ignoreWhitespace;
    public Boolean intralineDifference;
    public int lineLength;
    public Boolean manualReview;
    public Boolean retainHeader;
    public Boolean showLineEndings;
    public Boolean showTabs;
    public Boolean showWhitespaceErrors;
    public Boolean skipDeleted;
    public Boolean skipUncommented;
    public Boolean syntaxHighlighting;
    public Boolean hideTopMenu;
    public Boolean autoHideDiffTableHeader;
    public Boolean hideLineNumbers;
    public Boolean renderEntireFile;
    public Boolean hideEmptyPane;
    public int tabSize;
    public Theme theme;
  }
}
