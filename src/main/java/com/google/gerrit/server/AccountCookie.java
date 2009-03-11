// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server;


import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;

import javax.servlet.http.Cookie;


/** Data encoded into the {@link Gerrit#ACCOUNT_COOKIE} value. */
class AccountCookie {
  private Account.Id accountId;
  private boolean remember;

  AccountCookie(final Account.Id id, final boolean remember) {
    this.accountId = id;
    this.remember = remember;
  }

  Account.Id getAccountId() {
    return accountId;
  }

  boolean isRemember() {
    return remember;
  }

  void set(final Cookie c, final GerritServer gs) {
    try {
      c.setValue(gs.getAccountToken().newToken(toString()));
      c.setMaxAge(isRemember() ? gs.getSessionAge() : -1);
    } catch (XsrfException e) {
      c.setValue("");
      c.setMaxAge(0);
    }
  }

  @Override
  public String toString() {
    return getAccountId().toString() + "." + (isRemember() ? "t" : "f");
  }

  static AccountCookie parse(final ValidToken tok) {
    if (tok == null) {
      return null;
    }

    final String str = tok.getData();
    if (str == null || str.length() == 0) {
      return null;
    }

    final String[] parts = str.split("\\.");
    if (parts.length == 0 || parts.length > 2) {
      return null;
    }

    final Account.Id accountId = Account.Id.parse(parts[0]);
    final boolean remember = parts.length == 2 ? "t".equals(parts[0]) : true;
    return new AccountCookie(accountId, remember);
  }
}
