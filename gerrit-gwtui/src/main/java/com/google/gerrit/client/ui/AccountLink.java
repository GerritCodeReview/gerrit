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
import com.google.gerrit.client.account.AccountInfo;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccountInfoCache;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.UserIdentity;

/** Link to any user's account dashboard. */
public class AccountLink extends InlineHyperlink {
  /** Create a link after locating account details from an active cache. */
  public static AccountLink link(AccountInfoCache cache, Account.Id id) {
    com.google.gerrit.common.data.AccountInfo ai = cache.get(id);
    return ai != null ? new AccountLink(ai) : null;
  }

  public AccountLink(com.google.gerrit.common.data.AccountInfo ai) {
    this(FormatUtil.asInfo(ai));
  }

  public AccountLink(UserIdentity ident) {
    this(AccountInfo.create(
        ident.getAccount().get(),
        ident.getName(),
        ident.getEmail()));
  }

  public AccountLink(AccountInfo info) {
    this(info, Change.Status.NEW);
  }

  public AccountLink(AccountInfo info, Change.Status status) {
    super(FormatUtil.name(info), PageLinks.toAccountQuery(owner(info), status));
    setTitle(FormatUtil.nameEmail(info));
  }

  private static String owner(AccountInfo ai) {
    if (ai.email() != null) {
      return ai.email();
    } else if (ai.name() != null) {
      return ai.name();
    } else if (ai._account_id() != 0) {
      return "" + ai._account_id();
    } else {
      return "";
    }
  }

  @Override
  public void go() {
    Gerrit.display(getTargetHistoryToken());
  }
}
