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

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountUserNameException;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.auth.AuthenticationUnavailableException;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Handles username/password based authentication against the directory. */
@SuppressWarnings("serial")
@Singleton
class LdapLoginServlet extends HttpServlet {
  private static final Logger log = LoggerFactory
      .getLogger(LdapLoginServlet.class);

  private final AccountManager accountManager;
  private final Provider<WebSession> webSession;
  private final Provider<String> urlProvider;

  @Inject
  LdapLoginServlet(AccountManager accountManager,
      Provider<WebSession> webSession,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider) {
    this.accountManager = accountManager;
    this.webSession = webSession;
    this.urlProvider = urlProvider;

    if (Strings.isNullOrEmpty(urlProvider.get())) {
      log.error("gerrit.canonicalWebUrl must be set in gerrit.config");
    }
  }

  private void sendForm(HttpServletRequest req, HttpServletResponse res,
      @Nullable String errorMessage) throws IOException {
    String self = req.getRequestURI();
    String cancel = Objects.firstNonNull(urlProvider.get(), "/");
    Document doc =
        HtmlDomUtil.parseFile(LdapLoginServlet.class, "LoginForm.html");
    HtmlDomUtil.find(doc, "hostName").setTextContent(req.getServerName());
    HtmlDomUtil.find(doc, "login_form").setAttribute("action", self);
    HtmlDomUtil.find(doc, "cancel_link").setAttribute("href", cancel);

    Element emsg = HtmlDomUtil.find(doc, "error_message");
    if (Strings.isNullOrEmpty(errorMessage)) {
      emsg.getParentNode().removeChild(emsg);
    } else {
      emsg.setTextContent(errorMessage);
    }

    byte[] bin = HtmlDomUtil.toUTF8(doc);
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setContentType("text/html");
    res.setCharacterEncoding("UTF-8");
    res.setContentLength(bin.length);
    ServletOutputStream out = res.getOutputStream();
    try {
      out.write(bin);
    } finally {
      out.close();
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    sendForm(req, res, null);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    String username = Strings.nullToEmpty(req.getParameter("username")).trim();
    String password = Strings.nullToEmpty(req.getParameter("password"));
    String remember = Strings.nullToEmpty(req.getParameter("rememberme"));
    if (username.isEmpty() || password.isEmpty()) {
      sendForm(req, res, "Invalid username or password.");
      return;
    }

    AuthRequest areq = AuthRequest.forUser(username);
    areq.setPassword(password);

    AuthResult ares;
    try {
      ares = accountManager.authenticate(areq);
    } catch (AccountUserNameException e) {
      sendForm(req, res, e.getMessage());
      return;
    } catch (AuthenticationUnavailableException e) {
      sendForm(req, res, "Authentication unavailable at this time.");
      return;
    } catch (AccountException e) {
      log.info(String.format("'%s' failed to sign in: %s", username, e.getMessage()));
      sendForm(req, res, "Invalid username or password.");
      return;
    }

    String token = getToken(req);
    StringBuilder dest = new StringBuilder();
    dest.append(urlProvider.get());
    dest.append('#');
    dest.append(token);

    CacheHeaders.setNotCacheable(res);
    webSession.get().login(ares, "1".equals(remember));
    res.sendRedirect(dest.toString());
  }

  private static String getToken(final HttpServletRequest req) {
    String token = req.getPathInfo();
    if (token == null || token.isEmpty()) {
      token = PageLinks.MINE;
    } else if (!token.startsWith("/")) {
      token = "/" + token;
    }
    return token;
  }
}
