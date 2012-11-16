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

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.ExternalIdsUpdate;
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
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class AuthenticationServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  public static final String PARAMETER_USERNAME = "username";
  public static final String PARAMETER_PASSWORD = "password";

  private static final boolean IS_DEV = Boolean.getBoolean("Gerrit.GwtDevMode");

  private final String canonicalWebUrl;
  private final AuthBackend authBackend;
  private final RealmBackend realmBackend;
  private final Provider<WebSession> session;
  private final SchemaFactory<ReviewDb> schema;
  private final ExternalIds externalIds;
  private final ExternalIdsUpdate.Server externalIdsUpdate;
  private final AccountsUpdate accountsUpdate;

  @Inject
  AuthenticationServlet(
      AuthBackend authBackend,
      RealmBackend realmBackend,
      Provider<WebSession> session,
      SchemaFactory<ReviewDb> schema,
      @CanonicalWebUrl String canonicalWebUrl,
      ExternalIds externalIds,
      ExternalIdsUpdate.Server externalIdsUpdate,
      AccountsUpdate accountsUpdate) {
    this.schema = schema;
    this.session = session;
    this.authBackend = authBackend;
    this.realmBackend = realmBackend;
    this.canonicalWebUrl = canonicalWebUrl;
    this.externalIds = externalIds;
    this.externalIdsUpdate = externalIdsUpdate;
    this.accountsUpdate = accountsUpdate;
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
      ExternalId.Key key = ExternalId.Key.create(ExternalId.SCHEME_UUID, user.getUUID().uuid());
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

  private Account.Id create(ReviewDb db, AuthUser user)
      throws OrmException, ConfigInvalidException, IOException {
    UserData userData = realmBackend.getUserData(user);
    if (userData == null) {
      //TODO(dpursehouse) throw exception ?
    }

    List<ExternalId> extIds = new ArrayList<>(2);
    String username = user.getUsername();
    String email = userData.getEmailAddress();
    Account.Id id = new Account.Id(db.nextAccountId());

    if (username != null) {
      extIds.add(
          ExternalId.createUsername(
              username, id, null)); //TODO(dpursehouse) OK to use null password?
    }
    if (email != null) {
      extIds.add(ExternalId.createEmail(id, email));
    }

    externalIdsUpdate.create().insert(db, extIds);

    Account a = new Account(id, TimeUtil.nowTs());
    a.setFullName(userData.getDisplayName());
    a.setPreferredEmail(email);
    a.setUserName(username);
    accountsUpdate.insert(db, a);

    return a.getId();
  }

  private void redirect(HttpServletResponse resp, String url) throws IOException {
    if (IS_DEV) {
      resp.sendRedirect(canonicalWebUrl + "?gwt.codesrv=127.0.0.1:9997" + url);
    } else {
      resp.sendRedirect(canonicalWebUrl + url);
    }
  }

  private Account.Id getUserAccount(AuthUser user, ExternalId.Key key) {
    try (ReviewDb db = schema.open()) {
      ExternalId externalId = externalIds.get(db, key);
      if (externalId == null) {
        return create(db, user);
      }
      return externalId.accountId();
    } catch (IOException | ConfigInvalidException | OrmException e) {
      throw new RuntimeException(e);
    }
  }
}
