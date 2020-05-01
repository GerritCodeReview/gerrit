// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.auth;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.entities.Account;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.account.AuthResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.junit.Test;

public class AuthenticationCheckIT extends AbstractDaemonTest {
  @Test
  public void authCheck_loggedInUser_returnsOk() throws Exception {
    RestResponse r = adminRestSession.get("/auth-check");
    r.assertNoContent();
  }

  @Test
  public void authCheck_anonymousUser_returnsForbidden() throws Exception {
    RestSession anonymous = new RestSession(server, null);
    RestResponse r = anonymous.get("/auth-check");
    r.assertForbidden();
  }

  @Test
  public void authCheck_deactivatedUser() throws Exception {
    String authCheckUrl = "/auth-check";

    // Making sure anonymous user is not authenticated beforehand
    anonymousRestSession.get(authCheckUrl).assertForbidden();

    // Grabbing a session token for user
    String sessionCookie = getSessionCookie(userRestSession);
    Header authHeader = new BasicHeader("Cookie", sessionCookie);
    // From now on, passing the authHeader allows to use the userRestSession.
    // This not a "runAs", but this is mimicking the way api clients work for example is
    // LDAP auth settings. There, the authentication happens once and the session cookie
    // gets used in subsequent requests to re-use the session and by-pass manual authentication
    // again.

    // Checking that anonymous session on its own is still not authenticated
    anonymousRestSession.get(authCheckUrl).assertForbidden();

    // And that the header alone provides authentication
    anonymousRestSession.getWithHeader(authCheckUrl, authHeader).assertNoContent();
    anonymousRestSession.get(authCheckUrl).assertForbidden();

    try {
      gApi.accounts().id(user.id().get()).setActive(false);

      // With the disabled account, authHeader is no longer valid
      anonymousRestSession.getWithHeader(authCheckUrl, authHeader).assertForbidden();

    } finally {
      // Resetting the user status for good measure.
      gApi.accounts().id(user.id().get()).setActive(true);
    }
  }

  private String getSessionCookie(RestSession session) throws Exception {
    String pluginName = "cached-session-login-plugin";
    try (AutoCloseable ignored =
        installPlugin(pluginName, null, CachedSessionLoginModule.class, null)) {
      String loginUrl = "/plugins/" + pluginName + CachedSessionLoginModule.cacheSessionLoginPath;

      RestResponse res = session.get(loginUrl);
      res.assertNoContent();
      String setCookie = res.getHeader("Set-Cookie");

      // setCookie is "GerritAccount=[TOKEN]; Path=/; Expires=..."
      // token will only be the "GerritAccount=[TOKEN]" part of setCookie
      String token = setCookie.split(";")[0];

      return token;
    }
  }

  static class CachedSessionLoginModule extends ServletModule {
    private static final String cacheSessionLoginPath = "/cache-session-login";

    @Override
    protected void configureServlets() {
      serve(cacheSessionLoginPath).with(CacheSessionLoginServlet.class);
    }
  }

  @Singleton
  public static class CacheSessionLoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private final DynamicItem<WebSession> sessionProvider;

    @Inject
    CacheSessionLoginServlet(DynamicItem<WebSession> sessionProvider) {
      this.sessionProvider = sessionProvider;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException {
      // We expect the user to be on a temporary authenticated session and will add that session to
      // the cache by performing a proper login.
      WebSession session = sessionProvider.get();
      Account.Id id = session.getUser().getAccountId();

      AuthResult authRes = new AuthResult(id, null, false);
      session.login(authRes, true);

      res.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
  }
}
