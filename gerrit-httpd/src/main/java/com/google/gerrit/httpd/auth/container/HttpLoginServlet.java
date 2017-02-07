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

package com.google.gerrit.httpd.auth.container;

import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_EXTERNAL;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.CanonicalWebUrl;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.LoginUrlToken;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.gwtorm.server.OrmException;
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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Initializes the user session if HTTP authentication is enabled.
 *
 * <p>If HTTP authentication has been enabled this servlet binds to {@code /login/} and initializes
 * the user session based on user information contained in the HTTP request.
 */
@Singleton
class HttpLoginServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(HttpLoginServlet.class);

  private final DynamicItem<WebSession> webSession;
  private final CanonicalWebUrl urlProvider;
  private final AccountManager accountManager;
  private final HttpAuthFilter authFilter;
  private final AuthConfig authConfig;

  @Inject
  HttpLoginServlet(
      final DynamicItem<WebSession> webSession,
      final CanonicalWebUrl urlProvider,
      final AccountManager accountManager,
      final HttpAuthFilter authFilter,
      final AuthConfig authConfig) {
    this.webSession = webSession;
    this.urlProvider = urlProvider;
    this.accountManager = accountManager;
    this.authFilter = authFilter;
    this.authConfig = authConfig;
  }

  @Override
  protected void doGet(final HttpServletRequest req, final HttpServletResponse rsp)
      throws ServletException, IOException {
    final String token = LoginUrlToken.getToken(req);

    CacheHeaders.setNotCacheable(rsp);
    final String user = authFilter.getRemoteUser(req);
    if (user == null || "".equals(user)) {
      log.error(
          "Unable to authenticate user by "
              + authFilter.getLoginHeader()
              + " request header.  Check container or server configuration.");

      final Document doc =
          HtmlDomUtil.parseFile( //
              HttpLoginServlet.class, "ConfigurationError.html");

      replace(doc, "loginHeader", authFilter.getLoginHeader());
      replace(doc, "ServerName", req.getServerName());
      replace(doc, "ServerPort", ":" + req.getServerPort());
      replace(doc, "ContextPath", req.getContextPath());

      final byte[] bin = HtmlDomUtil.toUTF8(doc);
      rsp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      rsp.setContentType("text/html");
      rsp.setCharacterEncoding(UTF_8.name());
      rsp.setContentLength(bin.length);
      try (ServletOutputStream out = rsp.getOutputStream()) {
        out.write(bin);
      }
      return;
    }

    final AuthRequest areq = AuthRequest.forUser(user);
    areq.setDisplayName(authFilter.getRemoteDisplayname(req));
    areq.setEmailAddress(authFilter.getRemoteEmail(req));
    final AuthResult arsp;
    try {
      arsp = accountManager.authenticate(areq);
    } catch (AccountException e) {
      log.error("Unable to authenticate user \"" + user + "\"", e);
      rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String remoteExternalId = authFilter.getRemoteExternalIdToken(req);
    if (remoteExternalId != null) {
      try {
        log.debug("Associating external identity \"{}\" to user \"{}\"", remoteExternalId, user);
        updateRemoteExternalId(arsp, remoteExternalId);
      } catch (AccountException | OrmException e) {
        log.error(
            "Unable to associate external identity \""
                + remoteExternalId
                + "\" to user \""
                + user
                + "\"",
            e);
        rsp.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
    }

    final StringBuilder rdr = new StringBuilder();
    if (arsp.isNew() && authConfig.getRegisterPageUrl() != null) {
      rdr.append(authConfig.getRegisterPageUrl());
    } else {
      rdr.append(urlProvider.get(req));
      if (arsp.isNew() && !token.startsWith(PageLinks.REGISTER + "/")) {
        rdr.append('#' + PageLinks.REGISTER);
      }
      rdr.append(token);
    }

    webSession.get().login(arsp, true /* persistent cookie */);
    rsp.sendRedirect(rdr.toString());
  }

  private void updateRemoteExternalId(AuthResult arsp, String remoteAuthToken)
      throws AccountException, OrmException, IOException {
    AccountExternalId remoteAuthExtId =
        new AccountExternalId(
            arsp.getAccountId(), new AccountExternalId.Key(SCHEME_EXTERNAL, remoteAuthToken));
    accountManager.updateLink(
        arsp.getAccountId(), new AuthRequest(remoteAuthExtId.getExternalId()));
  }

  private void replace(Document doc, String name, String value) {
    Element e = HtmlDomUtil.find(doc, name);
    if (e != null) {
      e.setTextContent(value);
    } else {
      replaceByClass(doc, name, value);
    }
  }

  private void replaceByClass(Node parent, String name, String value) {
    final NodeList list = parent.getChildNodes();
    for (int i = 0; i < list.getLength(); i++) {
      final Node n = list.item(i);
      if (n instanceof Element) {
        final Element e = (Element) n;
        if (name.equals(e.getAttribute("class"))) {
          e.setTextContent(value);
        }
      }
      replaceByClass(n, name, value);
    }
  }
}
