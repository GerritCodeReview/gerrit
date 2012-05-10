// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.client.ui;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.QueryScreen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.reviewdb.client.Account;

/** Link to any user's account dashboard. */
public class AccountLink extends InlineHyperlink {
  /** Create a link after locating account details from an active cache. */
  public static AccountLink link(final AccountInfoCache cache,
      final Account.Id id) {
    final AccountInfo ai = cache.get(id);
    return ai != null ? new AccountLink(ai) : null;
  }

  private final String query;

  public AccountLink(final AccountInfo ai) {
    this(FormatUtil.name(ai), ai);
  }

  public AccountLink(final String text, final AccountInfo ai) {
    super(text, PageLinks.toAccountQuery(FormatUtil.name(ai)));
    setTitle(FormatUtil.nameEmail(ai));
    this.query = "owner:\"" + FormatUtil.name(ai) + "\"";
  }

  public AccountLink(final String text, final Account.Id ai) {
    this(text, new AccountInfo(ai));
    addStyleName(Gerrit.RESOURCES.css().accountName());
  }

  private Screen createScreen() {
    return QueryScreen.forQuery(query);
  }

  @Override
  public void go() {
    Gerrit.display(getTargetHistoryToken(), createScreen());
  }
}
