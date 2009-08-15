// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.http;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtjsonrpc.server.ActiveCall;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestScoped
public class GerritCall extends ActiveCall {
  private final SignedToken sessionKey;
  private final int sessionAge;

  private boolean accountRead;
  private Account.Id accountId;
  private boolean rememberAccount;

  @Inject
  GerritCall(final AuthConfig ac, final HttpServletRequest i,
      final HttpServletResponse o) {
    super(i, o);
    setXsrfSignedToken(ac.getXsrfToken());
    sessionKey = ac.getAccountToken();
    sessionAge = ac.getSessionAge();
  }

  @Override
  public void onFailure(final Throwable error) {
    if (error instanceof OrmException) {
      onInternalFailure(error);
    } else {
      super.onFailure(error);
    }
  }

  @Override
  public String getUser() {
    initAccount();
    return accountId != null ? accountId.toString() : null;
  }

  public void setAccount(final Account.Id id, final boolean remember) {
    accountRead = true;
    accountId = id;
    rememberAccount = remember;
    setAccountCookie();
  }

  public void logout() {
    accountRead = true;
    accountId = null;
    rememberAccount = false;
    removeCookie(Gerrit.ACCOUNT_COOKIE);
  }

  public Account.Id getAccountId() {
    initAccount();
    return accountId;
  }

  private void initAccount() {
    if (!accountRead) {
      accountRead = true;
      accountId = null;
      rememberAccount = false;

      final ValidToken t = getCookie(Gerrit.ACCOUNT_COOKIE, sessionKey);
      if (t != null) {
        final AccountCookie cookie;
        try {
          cookie = AccountCookie.parse(t);
        } catch (RuntimeException e) {
          return;
        }

        accountId = cookie.getAccountId();
        rememberAccount = cookie.isRemember();

        if (t.needsRefresh()) {
          // The cookie is valid, but its getting stale. Update it with a
          // newer date so it doesn't expire on an active user.
          //
          setAccountCookie();
        }
      }
    }
  }

  private void setAccountCookie() {
    final AccountCookie ac = new AccountCookie(accountId, rememberAccount);
    String val;
    int age;
    try {
      val = sessionKey.newToken(ac.toString());
      age = ac.isRemember() ? sessionAge : -1;
    } catch (XsrfException e) {
      val = "";
      age = 0;
    }
    String path = getHttpServletRequest().getContextPath();
    if (path.equals("")) {
      path = "/";
    }
    final Cookie c = new Cookie(Gerrit.ACCOUNT_COOKIE, val);
    c.setMaxAge(age);
    c.setPath(path);
    httpResponse.addCookie(c);
  }
}
