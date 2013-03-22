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

import com.google.common.base.Objects;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountDiffPreference.Whitespace;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GetDiffPreferences.DiffPreferencesInfo;
import com.google.gerrit.server.account.SetDiffPreferences.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

public class SetDiffPreferences implements RestModifyView<AccountResource, Input> {
  static class Input {
    short context;
    boolean expandAllComments;
    Whitespace ignoreWhitespace;
    boolean intralineDifference;
    int lineLength;
    boolean manualReview;
    boolean retainHeader;
    boolean showLineEndings;
    boolean showTabs;
    boolean showWhitespaceErrors;
    boolean skipDeleted;
    boolean skipUncommented;
    boolean syntaxHighlighting;
    int tabSize;
  }

  private final Provider<CurrentUser> self;
  private final ReviewDb db;

  @Inject
  SetDiffPreferences(Provider<CurrentUser> self, ReviewDb db) {
    this.self = self;
    this.db = db;
  }

  @Override
  public DiffPreferencesInfo apply(AccountResource rsrc, Input input)
      throws AuthException, OrmException {
    if (self.get() != rsrc.getUser()
        && !self.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("restricted to administrator");
    }
    if (input == null) {
      input = new Input();
    }

    AccountDiffPreference p = rsrc.getUser().getAccountDiffPreference();
    p.setContext(input.context);
    p.setExpandAllComments(input.expandAllComments);
    p.setIgnoreWhitespace(Objects.firstNonNull(input.ignoreWhitespace,
        AccountDiffPreference.DEFAULT_IGNORE_WHITESPACE));
    p.setIntralineDifference(input.intralineDifference);
    p.setLineLength(input.lineLength);
    p.setManualReview(input.manualReview);
    p.setRetainHeader(input.retainHeader);
    p.setShowLineEndings(input.showLineEndings);
    p.setShowTabs(input.showTabs);
    p.setShowWhitespaceErrors(input.showWhitespaceErrors);
    p.setSkipDeleted(input.skipDeleted);
    p.setSkipUncommented(input.skipUncommented);
    p.setSyntaxHighlighting(input.syntaxHighlighting);
    p.setTabSize(input.tabSize);

    db.accountDiffPreferences().upsert(Collections.singleton(p));

    return DiffPreferencesInfo.parse(p);
  }
}
