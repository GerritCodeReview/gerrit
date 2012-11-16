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

package com.google.gerrit.httpd.auth;

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_USERNAME;

import com.google.common.collect.Sets;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.reviewdb.client.AccountExternalId.Key;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountByEmailCache;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.auth.AuthBackend;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.auth.AuthUser;
import com.google.gerrit.server.auth.RealmBackend;
import com.google.gerrit.server.auth.UserData;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.util.Collections;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class AuthenticationServlet extends HttpServlet {
  public static final String PARAMETER_USERNAME = "username";
  public static final String PARAMETER_PASSWORD = "password";

  private static final boolean IS_DEV = Boolean.getBoolean("Gerrit.GwtDevMode");

  private final String canonicalWebUrl;
  private final AuthBackend authBackend;
  private final RealmBackend realmBackend;
  private final Provider<WebSession> session;
  private final SchemaFactory<ReviewDb> schema;
  private final AccountByEmailCache byEmailCache;

  @Inject
  AuthenticationServlet(
      AuthBackend authBackend,
      RealmBackend realmBackend,
      Provider<WebSession> session,
      SchemaFactory<ReviewDb> schema,
      AccountByEmailCache byEmailCache,
      @CanonicalWebUrl String canonicalWebUrl) {
    this.schema = schema;
    this.session = session;
    this.authBackend = authBackend;
    this.byEmailCache = byEmailCache;
    this.realmBackend = realmBackend;
    this.canonicalWebUrl = canonicalWebUrl;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doPost(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    String username = req.getParameter(PARAMETER_USERNAME);
    String password = req.getParameter(PARAMETER_PASSWORD);
    HttpAuthRequest authRequest = new HttpAuthRequest(username, password, req, resp);
    try {
      AuthUser user = authBackend.authenticate(authRequest);
      AccountExternalId.Key key = new AccountExternalId.Key(user.getUUID().get());
      Account.Id accountId = getUserAccount(user, key);
      AuthResult res = new AuthResult(accountId, key, false);
      session.get().login(res, false);
      redirect(resp, "#");
      return;
    } catch (AuthException e) {
      // log and just return fail response
    }
    redirect(resp, "#/auth-dialog");
  }

  private Account.Id create(ReviewDb db, AuthUser user, Key key)
      throws OrmException {
    String username = user.getUsername();
    Account.Id newId = new Account.Id(db.nextAccountId());
    Account account = new Account(newId);
    AccountExternalId accountExternalId = new AccountExternalId(newId, key);
    UserData userData = realmBackend.getUserData(user);
    if (userData == null) {
      // throw exception ?
    }

    accountExternalId.setEmailAddress(userData.getEmailAddress());
    account.setFullName(userData.getDisplayName());
    account.setPreferredEmail(userData.getEmailAddress());
    account.setUserName(username);

    // create username
    AccountExternalId.Key userNameKey = new AccountExternalId.Key(SCHEME_USERNAME, username);
    AccountExternalId userNameId = new AccountExternalId(newId, userNameKey);

    db.accounts().insert(Collections.singleton(account));
    db.accountExternalIds().insert(Sets.newHashSet(accountExternalId, userNameId));

    byEmailCache.evict(account.getPreferredEmail());
    return account.getId();
  }

  private void redirect(HttpServletResponse resp, String url) throws IOException {
    if (IS_DEV) {
      resp.sendRedirect(canonicalWebUrl + "?gwt.codesrv=127.0.0.1:9997" + url);
    } else {
      resp.sendRedirect(canonicalWebUrl + url);
    }
  }

  private Account.Id getUserAccount(AuthUser user, AccountExternalId.Key key) {
    try {
      ReviewDb db = schema.open();
      try {
        AccountExternalId externalId = db.accountExternalIds().get(key);
        if (externalId == null) {
          return create(db, user, key);
        }
        return externalId.getAccountId();
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new RuntimeException(e);
    }
  }
}
