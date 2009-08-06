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
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtjsonrpc.server.ActiveCall;
import com.google.gwtjsonrpc.server.ValidToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;

import org.spearce.jgit.util.Base64;

import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RequestScoped
public class GerritCall extends ActiveCall {
  private final AuthConfig authConfig;
  private final EmailExpander emailExpander;
  private final SchemaFactory<ReviewDb> schema;
  private final AccountByEmailCache byEmailCache;
  private boolean accountRead;
  private Account.Id accountId;
  private boolean rememberAccount;

  @Inject
  GerritCall(final AuthConfig ac, final EmailExpander emailExpander,
      final SchemaFactory<ReviewDb> sf, final AccountByEmailCache bec,
      final HttpServletRequest i, final HttpServletResponse o) {
    super(i, o);
    this.authConfig = ac;
    this.emailExpander = emailExpander;
    this.schema = sf;
    this.byEmailCache = bec;
    setXsrfSignedToken(ac.getXsrfToken());
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
    if (accountRead) {
      return;
    }

    accountRead = true;
    ValidToken accountInfo =
        getCookie(Gerrit.ACCOUNT_COOKIE, authConfig.getAccountToken());

    if (accountInfo == null) {
      switch (authConfig.getLoginType()) {
        case HTTP:
          if (assumeHttp()) {
            return;
          }
          break;
      }

      if (getCookie(Gerrit.ACCOUNT_COOKIE) != null) {
        // The cookie is bogus, but it was sent. Send an expired cookie
        // back to clear it out of the browser's cookie store.
        //
        logout();
      }
      return;
    }

    try {
      final AccountCookie cookie = AccountCookie.parse(accountInfo);
      accountId = cookie.getAccountId();
      rememberAccount = cookie.isRemember();
    } catch (RuntimeException e) {
      // Whoa, did we change our cookie format or something? This should
      // never happen on a valid acocunt token, but discard it anyway.
      //
      logout();
      return;
    }

    if (accountInfo.needsRefresh()) {
      // The cookie is valid, but its getting stale. Update it with a
      // newer date so it doesn't expire on an active user.
      //
      setAccountCookie();
    }
  }

  private boolean assumeHttp() {
    final String hdr = authConfig.getLoginHttpHeader();
    String user;
    if (hdr != null && !"".equals(hdr)
        && !"Authorization".equalsIgnoreCase(hdr)) {
      user = getHttpServletRequest().getHeader(hdr);
    } else {
      user = getHttpServletRequest().getRemoteUser();
      if (user == null) {
        // If the container didn't do the authentication we might
        // have done it in the front-end web server. Try to split
        // the identity out of the Authorization header and honor it.
        //
        user = getHttpServletRequest().getHeader("Authorization");
        if (user != null && user.startsWith("Basic ")) {
          user = new String(Base64.decode(user.substring("Basic ".length())));
          if (user.indexOf(':') >= 0) {
            user = user.substring(0, user.indexOf(':'));
          }
        } else if (user != null && user.startsWith("Digest ")
            && user.contains("username=\"")) {
          user = user.substring(user.indexOf("username=\"") + 10);
          user = user.substring(0, user.indexOf('"'));
        } else {
          user = null;
        }
      }
    }
    if (user == null) {
      return false;
    }

    try {
      final ReviewDb db = schema.open();
      try {
        final String eid = "gerrit:" + user;
        final List<AccountExternalId> matches =
            db.accountExternalIds().byExternal(eid).toList();

        if (matches.size() == 1) {
          // Account exists, connect to it again.
          //
          final AccountExternalId e = matches.get(0);
          e.setLastUsedOn();
          db.accountExternalIds().update(Collections.singleton(e));

          accountId = e.getAccountId();
          rememberAccount = false;
          setAccountCookie();
          return true;
        }

        if (matches.size() == 0) {
          // No account, automatically initialize a new one.
          //
          final Transaction txn = db.beginTransaction();
          final Account.Id nid = new Account.Id(db.nextAccountId());
          final Account a = new Account(nid);
          a.setFullName(user);
          if (emailExpander.canExpand(user)) {
            a.setPreferredEmail(emailExpander.expand(user));
          }

          final AccountExternalId e =
              new AccountExternalId(new AccountExternalId.Key(nid, eid));
          e.setEmailAddress(a.getPreferredEmail());
          e.setLastUsedOn();
          db.accounts().insert(Collections.singleton(a), txn);
          db.accountExternalIds().insert(Collections.singleton(e), txn);
          txn.commit();
          byEmailCache.evict(a.getPreferredEmail());

          accountId = nid;
          rememberAccount = false;
          setAccountCookie();
          return true;
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
    }
    return false;
  }

  private void setAccountCookie() {
    final AccountCookie ac = new AccountCookie(accountId, rememberAccount);
    String val;
    int age;
    try {
      val = authConfig.getAccountToken().newToken(ac.toString());
      age = ac.isRemember() ? authConfig.getSessionAge() : -1;
    } catch (XsrfException e) {
      val = "";
      age = 0;
    }
    setCookie(Gerrit.ACCOUNT_COOKIE, val, age);
  }
}
