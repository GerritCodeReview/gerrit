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
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GetDiffPreferences.DiffPreferencesInfo;
import com.google.gerrit.server.account.SetDiffPreferences.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.Collections;

@Singleton
public class SetDiffPreferences implements RestModifyView<AccountResource, Input> {
  static class Input {
    Short context;
    Boolean expandAllComments;
    Whitespace ignoreWhitespace;
    Boolean intralineDifference;
    Integer lineLength;
    Boolean manualReview;
    Boolean retainHeader;
    Boolean showLineEndings;
    Boolean showTabs;
    Boolean showWhitespaceErrors;
    Boolean skipDeleted;
    Boolean skipUncommented;
    Boolean syntaxHighlighting;
    Boolean hideTopMenu;
    Boolean autoHideDiffTableHeader;
    Boolean hideLineNumbers;
    Boolean renderEntireFile;
    Integer tabSize;
    Theme theme;
    Boolean hideEmptyPane;
  }

  private final Provider<CurrentUser> self;
  private final Provider<ReviewDb> db;

  @Inject
  SetDiffPreferences(Provider<CurrentUser> self, Provider<ReviewDb> db) {
    this.self = self;
    this.db = db;
  }

  @Override
  public DiffPreferencesInfo apply(AccountResource rsrc, Input input)
      throws AuthException, OrmException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canModifyAccount()) {
      throw new AuthException("restricted to members of Modify Accounts");
    }
    if (input == null) {
      input = new Input();
    }

    Account.Id accountId = rsrc.getUser().getAccountId();
    AccountDiffPreference p;

    db.get().accounts().beginTransaction(accountId);
    try {
      p = db.get().accountDiffPreferences().get(accountId);
      if (p == null) {
        p = new AccountDiffPreference(accountId);
      }

      if (input.context != null) {
        p.setContext(input.context);
      }
      if (input.ignoreWhitespace != null) {
        p.setIgnoreWhitespace(input.ignoreWhitespace);
      }
      if (input.expandAllComments != null) {
        p.setExpandAllComments(input.expandAllComments);
      }
      if (input.intralineDifference != null) {
        p.setIntralineDifference(input.intralineDifference);
      }
      if (input.lineLength != null) {
        p.setLineLength(input.lineLength);
      }
      if (input.manualReview != null) {
        p.setManualReview(input.manualReview);
      }
      if (input.retainHeader != null) {
        p.setRetainHeader(input.retainHeader);
      }
      if (input.showLineEndings != null) {
        p.setShowLineEndings(input.showLineEndings);
      }
      if (input.showTabs != null) {
        p.setShowTabs(input.showTabs);
      }
      if (input.showWhitespaceErrors != null) {
        p.setShowWhitespaceErrors(input.showWhitespaceErrors);
      }
      if (input.skipDeleted != null) {
        p.setSkipDeleted(input.skipDeleted);
      }
      if (input.skipUncommented != null) {
        p.setSkipUncommented(input.skipUncommented);
      }
      if (input.syntaxHighlighting != null) {
        p.setSyntaxHighlighting(input.syntaxHighlighting);
      }
      if (input.hideTopMenu != null) {
        p.setHideTopMenu(input.hideTopMenu);
      }
      if (input.autoHideDiffTableHeader != null) {
        p.setAutoHideDiffTableHeader(input.autoHideDiffTableHeader);
      }
      if (input.hideLineNumbers != null) {
        p.setHideLineNumbers(input.hideLineNumbers);
      }
      if (input.renderEntireFile != null) {
        p.setRenderEntireFile(input.renderEntireFile);
      }
      if (input.tabSize != null) {
        p.setTabSize(input.tabSize);
      }
      if (input.theme != null) {
        p.setTheme(input.theme);
      }
      if (input.hideEmptyPane != null) {
        p.setHideEmptyPane(input.hideEmptyPane);
      }

      db.get().accountDiffPreferences().upsert(Collections.singleton(p));
      db.get().commit();
    } finally {
      db.get().rollback();
    }
    return DiffPreferencesInfo.parse(p);
  }
}
