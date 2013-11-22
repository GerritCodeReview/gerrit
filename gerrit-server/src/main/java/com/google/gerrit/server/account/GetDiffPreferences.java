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

  static class DiffPreferencesInfo {
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
      info.syntaxHighlighting = p.isSyntaxHighlighting() ? true : null;
      info.tabSize = p.getTabSize();
      return info;
    }

    short context;
    Boolean expandAllComments;
    Whitespace ignoreWhitespace;
    Boolean intralineDifference;
    int lineLength;
    Boolean manualReview;
    Boolean retainHeader;
    Boolean showLineEndings;
    Boolean showTabs;
    Boolean showWhitespaceErrors;
    Boolean skipDeleted;
    Boolean skipUncommented;
    Boolean syntaxHighlighting;
    int tabSize;
  }
}
