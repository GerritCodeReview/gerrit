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

package com.google.gerrit.httpd.auth.become;

import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_USERNAME;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_UUID;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.LoginUrlToken;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.template.SiteHeaderFooter;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@SuppressWarnings("serial")
@Singleton
class BecomeAnyAccountLoginServlet extends HttpServlet {
  private final DynamicItem<WebSession> webSession;
  private final SchemaFactory<ReviewDb> schema;
  private final Accounts accounts;
  private final AccountCache accountCache;
  private final AccountManager accountManager;
  private final SiteHeaderFooter headers;
  private final InternalAccountQuery accountQuery;

  @Inject
  BecomeAnyAccountLoginServlet(
      DynamicItem<WebSession> ws,
      SchemaFactory<ReviewDb> sf,
      Accounts a,
      AccountCache ac,
      AccountManager am,
      SiteHeaderFooter shf,
      InternalAccountQuery aq) {
    webSession = ws;
    schema = sf;
    accounts = a;
    accountCache = ac;
    accountManager = am;
    headers = shf;
    accountQuery = aq;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    doPost(req, rsp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    CacheHeaders.setNotCacheable(rsp);

    final AuthResult res;
    if ("create_account".equals(req.getParameter("action"))) {
      res = create();

    } else if (req.getParameter("user_name") != null) {
      res = byUserName(req.getParameter("user_name"));

    } else if (req.getParameter("preferred_email") != null) {
      res = byPreferredEmail(req.getParameter("preferred_email"));

    } else if (req.getParameter("account_id") != null) {
      res = byAccountId(req.getParameter("account_id"));

    } else {
      byte[] raw;
      try {
        raw = prepareHtmlOutput();
      } catch (OrmException e) {
        throw new ServletException(e);
      }
      rsp.setContentType("text/html");
      rsp.setCharacterEncoding(HtmlDomUtil.ENC.name());
      rsp.setContentLength(raw.length);
      try (OutputStream out = rsp.getOutputStream()) {
        out.write(raw);
      }
      return;
    }

    if (res != null) {
      webSession.get().login(res, false);
      final StringBuilder rdr = new StringBuilder();
      rdr.append(req.getContextPath());
      rdr.append("/");

      if (res.isNew()) {
        rdr.append('#' + PageLinks.REGISTER);
      } else {
        rdr.append(LoginUrlToken.getToken(req));
      }
      rsp.sendRedirect(rdr.toString());

    } else {
      rsp.setContentType("text/html");
      rsp.setCharacterEncoding(HtmlDomUtil.ENC.name());
      try (Writer out = rsp.getWriter()) {
        out.write("<html>");
        out.write("<body>");
        out.write("<h1>Account Not Found</h1>");
        out.write("</body>");
        out.write("</html>");
      }
    }
  }

  private byte[] prepareHtmlOutput() throws IOException, OrmException {
    final String pageName = "BecomeAnyAccount.html";
    Document doc = headers.parse(getClass(), pageName);
    if (doc == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }

    Element userlistElement = HtmlDomUtil.find(doc, "userlist");
    try (ReviewDb db = schema.open()) {
      for (Account.Id accountId : accounts.firstNIds(100)) {
        Account a = accountCache.get(accountId).getAccount();
        String displayName;
        if (a.getUserName() != null) {
          displayName = a.getUserName();
        } else if (a.getFullName() != null && !a.getFullName().isEmpty()) {
          displayName = a.getFullName();
        } else if (a.getPreferredEmail() != null) {
          displayName = a.getPreferredEmail();
        } else {
          displayName = accountId.toString();
        }

        Element linkElement = doc.createElement("a");
        linkElement.setAttribute("href", "?account_id=" + a.getId().toString());
        linkElement.setTextContent(displayName);
        userlistElement.appendChild(linkElement);
        userlistElement.appendChild(doc.createElement("br"));
      }
    }

    return HtmlDomUtil.toUTF8(doc);
  }

  private AuthResult auth(Account account) {
    if (account != null) {
      return new AuthResult(account.getId(), null, false);
    }
    return null;
  }

  private AuthResult auth(Account.Id account) {
    if (account != null) {
      return new AuthResult(account, null, false);
    }
    return null;
  }

  private AuthResult byUserName(String userName) {
    try {
      List<AccountState> accountStates = accountQuery.byExternalId(SCHEME_USERNAME, userName);
      if (accountStates.isEmpty()) {
        getServletContext().log("No accounts with username " + userName + " found");
        return null;
      }
      if (accountStates.size() > 1) {
        getServletContext().log("Multiple accounts with username " + userName + " found");
        return null;
      }
      return auth(accountStates.get(0).getAccount().getId());
    } catch (OrmException e) {
      getServletContext().log("cannot query account index", e);
      return null;
    }
  }

  private AuthResult byPreferredEmail(String email) {
    try (ReviewDb db = schema.open()) {
      List<Account> matches = db.accounts().byPreferredEmail(email).toList();
      return matches.size() == 1 ? auth(matches.get(0)) : null;
    } catch (OrmException e) {
      getServletContext().log("cannot query database", e);
      return null;
    }
  }

  private AuthResult byAccountId(String idStr) {
    final Account.Id id;
    try {
      id = Account.Id.parse(idStr);
    } catch (NumberFormatException nfe) {
      return null;
    }
    try (ReviewDb db = schema.open()) {
      return auth(accounts.get(db, id));
    } catch (OrmException e) {
      getServletContext().log("cannot query database", e);
      return null;
    }
  }

  private AuthResult create() throws IOException {
    try {
      return accountManager.authenticate(
          new AuthRequest(ExternalId.Key.create(SCHEME_UUID, UUID.randomUUID().toString())));
    } catch (AccountException e) {
      getServletContext().log("cannot create new account", e);
      return null;
    }
  }
}
