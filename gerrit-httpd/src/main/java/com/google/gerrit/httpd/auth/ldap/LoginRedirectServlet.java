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

package com.google.gerrit.httpd.auth.ldap;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.auth.SignInMode;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet bound to {@code /login/*} to redirect after user/pass sign-in.
 * <p>
 * This servlet is required because user authentication is done over RPC, but if
 * the RPC is successful we need to force the host page to fully reload to pick
 * up the account information, as we don't support updating the UI on the fly
 * after a sign-in.
 */
@Singleton
class LoginRedirectServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private final Provider<WebSession> webSession;
  private final Provider<String> urlProvider;

  @Inject
  LoginRedirectServlet(final Provider<WebSession> webSession,
      @CanonicalWebUrl @Nullable final Provider<String> urlProvider) {
    this.webSession = webSession;
    this.urlProvider = urlProvider;
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    final String token;
    if (webSession.get().isSignedIn()) {
      token = getToken(req);
    } else {
      final String msg = "Session cookie not available.";
      token = "/SignInFailure," + SignInMode.SIGN_IN + "," + msg;
    }

    final StringBuilder rdr = new StringBuilder();
    rdr.append(urlProvider.get());
    rdr.append('#');
    rdr.append(token);

    rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    rsp.setHeader("Pragma", "no-cache");
    rsp.setHeader("Cache-Control", "no-cache, must-revalidate");
    rsp.sendRedirect(rdr.toString());
  }

  private String getToken(final HttpServletRequest req) {
    String token = req.getPathInfo();
    if (token == null || token.isEmpty()) {
      token = PageLinks.MINE;
    } else if (!token.startsWith("/")) {
      token = "/" + token;
    }
    return token;
  }
}
