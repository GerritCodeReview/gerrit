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
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.query.account.InternalAccountQuery;
import com.google.gerrit.util.http.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Singleton
class BecomeAnyAccountLoginServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private final DynamicItem<WebSession> webSession;
  private final Accounts accounts;
  private final AccountCache accountCache;
  private final AccountManager accountManager;
  private final SiteHeaderFooter headers;
  private final Provider<InternalAccountQuery> queryProvider;

  @Inject
  BecomeAnyAccountLoginServlet(
      DynamicItem<WebSession> ws,
      Accounts a,
      AccountCache ac,
      AccountManager am,
      SiteHeaderFooter shf,
      Provider<InternalAccountQuery> qp) {
    webSession = ws;
    accounts = a;
    accountCache = ac;
    accountManager = am;
    headers = shf;
    queryProvider = qp;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
      throws IOException, ServletException {
    doPost(req, rsp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    CacheHeaders.setNotCacheable(rsp);

    final AuthResult res;
    if ("create_account".equals(req.getParameter("action"))) {
      res = create();

    } else if (req.getParameter("user_name") != null) {
      res = byUserName(req.getParameter("user_name"));

    } else if (req.getParameter("preferred_email") != null) {
      res = byPreferredEmail(req.getParameter("preferred_email")).orElse(null);

    } else if (req.getParameter("account_id") != null) {
      res = byAccountId(req.getParameter("account_id")).orElse(null);

    } else {
      byte[] raw;
      raw = prepareHtmlOutput();
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

  private byte[] prepareHtmlOutput() throws IOException {
    final String pageName = "BecomeAnyAccount.html";
    Document doc = headers.parse(getClass(), pageName);
    if (doc == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }

    Element userlistElement = HtmlDomUtil.find(doc, "userlist");
    for (Account.Id accountId : accounts.firstNIds(100)) {
      Optional<AccountState> accountState = accountCache.get(accountId);
      if (!accountState.isPresent()) {
        continue;
      }
      Account account = accountState.get().getAccount();
      String displayName;
      if (accountState.get().getUserName().isPresent()) {
        displayName = accountState.get().getUserName().get();
      } else if (account.getFullName() != null && !account.getFullName().isEmpty()) {
        displayName = account.getFullName();
      } else if (account.getPreferredEmail() != null) {
        displayName = account.getPreferredEmail();
      } else {
        displayName = accountId.toString();
      }

      Element linkElement = doc.createElement("a");
      linkElement.setAttribute("href", "?account_id=" + account.getId().toString());
      linkElement.setTextContent(displayName);
      userlistElement.appendChild(linkElement);
      userlistElement.appendChild(doc.createElement("br"));
    }

    return HtmlDomUtil.toUTF8(doc);
  }

  private Optional<AuthResult> auth(Optional<AccountState> account) {
    return account.map(a -> new AuthResult(a.getAccount().getId(), null, false));
  }

  private AuthResult auth(Account.Id account) {
    if (account != null) {
      return new AuthResult(account, null, false);
    }
    return null;
  }

  private AuthResult byUserName(String userName) {
    List<AccountState> accountStates = queryProvider.get().byExternalId(SCHEME_USERNAME, userName);
    if (accountStates.isEmpty()) {
      getServletContext().log("No accounts with username " + userName + " found");
      return null;
    }
    if (accountStates.size() > 1) {
      getServletContext().log("Multiple accounts with username " + userName + " found");
      return null;
    }
    return auth(accountStates.get(0).getAccount().getId());
  }

  private Optional<AuthResult> byPreferredEmail(String email) {
    return auth(queryProvider.get().byPreferredEmail(email).stream().findFirst());
  }

  private Optional<AuthResult> byAccountId(String idStr) {
    Optional<Account.Id> id = Account.Id.tryParse(idStr);
    if (!id.isPresent()) {
      return Optional.empty();
    }

    try {
      return auth(accounts.get(id.get()));
    } catch (IOException | ConfigInvalidException e) {
      getServletContext().log("cannot query database", e);
      return Optional.empty();
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
