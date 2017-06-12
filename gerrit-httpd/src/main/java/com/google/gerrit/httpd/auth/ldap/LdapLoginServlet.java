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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.CanonicalWebUrl;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.LoginUrlToken;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.template.SiteHeaderFooter;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountUserNameException;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.auth.AuthenticationUnavailableException;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Handles username/password based authentication against the directory. */
@SuppressWarnings("serial")
@Singleton
class LdapLoginServlet extends HttpServlet {
  private static final Logger log = LoggerFactory.getLogger(LdapLoginServlet.class);

  private final AccountManager accountManager;
  private final DynamicItem<WebSession> webSession;
  private final CanonicalWebUrl urlProvider;
  private final SiteHeaderFooter headers;

  @Inject
  LdapLoginServlet(
      AccountManager accountManager,
      DynamicItem<WebSession> webSession,
      CanonicalWebUrl urlProvider,
      SiteHeaderFooter headers) {
    this.accountManager = accountManager;
    this.webSession = webSession;
    this.urlProvider = urlProvider;
    this.headers = headers;
  }

  private void sendForm(
      HttpServletRequest req, HttpServletResponse res, @Nullable String errorMessage)
      throws IOException {
    String self = req.getRequestURI();
    String cancel = MoreObjects.firstNonNull(urlProvider.get(req), "/");
    cancel += LoginUrlToken.getToken(req);

    Document doc = headers.parse(LdapLoginServlet.class, "LoginForm.html");
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
    res.setCharacterEncoding(UTF_8.name());
    res.setContentLength(bin.length);
    try (ServletOutputStream out = res.getOutputStream()) {
      out.write(bin);
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
    sendForm(req, res, null);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    req.setCharacterEncoding(UTF_8.name());
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
    } catch (RuntimeException e) {
      log.error("LDAP authentication failed", e);
      sendForm(req, res, "Authentication unavailable at this time.");
      return;
    }

    StringBuilder dest = new StringBuilder();
    dest.append(urlProvider.get(req));
    dest.append(LoginUrlToken.getToken(req));

    CacheHeaders.setNotCacheable(res);
    webSession.get().login(ares, "1".equals(remember));
    res.sendRedirect(dest.toString());
  }
}
