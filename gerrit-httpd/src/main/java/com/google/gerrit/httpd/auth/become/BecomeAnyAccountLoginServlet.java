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

import static com.google.gerrit.reviewdb.AccountExternalId.SCHEME_USERNAME;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.account.AccountException;
import com.google.gerrit.server.account.AccountExternalIdCache;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.account.AuthResult;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
@Singleton
public class BecomeAnyAccountLoginServlet extends HttpServlet {
  private static final boolean IS_DEV = Boolean.getBoolean("Gerrit.GwtDevMode");

  private final SchemaFactory<ReviewDb> schema;
  private final Provider<WebSession> webSession;
  private final Provider<String> urlProvider;
  private final AccountManager accountManager;
  private final AccountExternalIdCache accountExternalIdCache;
  private final byte[] raw;

  @Inject
  BecomeAnyAccountLoginServlet(final Provider<WebSession> ws,
      final SchemaFactory<ReviewDb> sf,
      final @CanonicalWebUrl @Nullable Provider<String> up,
      final AccountManager am, final ServletContext servletContext,
      final AccountExternalIdCache aeidc)
      throws IOException {
    webSession = ws;
    schema = sf;
    urlProvider = up;
    accountManager = am;
    accountExternalIdCache = aeidc;

    final String pageName = "BecomeAnyAccount.html";
    final Document doc = HtmlDomUtil.parseFile(getClass(), pageName);
    if (doc == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }
    if (!IS_DEV) {
      final Element devmode = HtmlDomUtil.find(doc, "gerrit_gwtdevmode");
      if (devmode != null) {
        devmode.getParentNode().removeChild(devmode);
      }
    }
    raw = HtmlDomUtil.toUTF8(doc);
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    doPost(req, rsp);
  }

  @Override
  protected void doPost(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
    rsp.setHeader("Pragma", "no-cache");
    rsp.setHeader("Cache-Control", "no-cache, must-revalidate");

    final AuthResult res;
    if ("create_account".equals(req.getParameter("action"))) {
      res = create();

    } else if (req.getParameter("user_name") != null) {
      res = byUserName(rsp, req.getParameter("user_name"));

    } else if (req.getParameter("preferred_email") != null) {
      res = byPreferredEmail(rsp, req.getParameter("preferred_email"));

    } else if (req.getParameter("account_id") != null) {
      res = byAccountId(rsp, req.getParameter("account_id"));

    } else {
      rsp.setContentType("text/html");
      rsp.setCharacterEncoding(HtmlDomUtil.ENC);
      rsp.setContentLength(raw.length);
      final OutputStream out = rsp.getOutputStream();
      try {
        out.write(raw);
      } finally {
        out.close();
      }
      return;
    }

    if (res != null) {
      try {
        webSession.get().login(res, false);
      } catch (OrmException e) {
        rsp.setContentType("text/html");
        rsp.setCharacterEncoding(HtmlDomUtil.ENC);
        final Writer out = rsp.getWriter();
        out.write("<html>");
        out.write("<body>");
        out.write("<h1>Could not log in</h1>");
        out.write("</body>");
        out.write("</html>");
        out.close();
        log("Could not log in", e);
        return;
      }
      final StringBuilder rdr = new StringBuilder();
      rdr.append(urlProvider.get());
      if (IS_DEV && req.getParameter("gwt.codesvr") != null) {
        if (rdr.indexOf("?") < 0) {
          rdr.append("?");
        } else {
          rdr.append("&");
        }
        rdr.append("gwt.codesvr=").append(req.getParameter("gwt.codesvr"));
      }
      rdr.append('#');
      if (res.isNew()) {
        rdr.append(PageLinks.REGISTER);
        rdr.append(',');
      }
      rdr.append(PageLinks.MINE);
      rsp.sendRedirect(rdr.toString());

    } else {
      rsp.setContentType("text/html");
      rsp.setCharacterEncoding(HtmlDomUtil.ENC);
      final Writer out = rsp.getWriter();
      out.write("<html>");
      out.write("<body>");
      out.write("<h1>Account Not Found</h1>");
      out.write("</body>");
      out.write("</html>");
      out.close();
    }
  }

  private AuthResult auth(final Account account) {
    if (account != null) {
      return new AuthResult(account.getId(), null, false);
    }
    return null;
  }

  private AuthResult auth(final AccountExternalId account) {
    if (account != null) {
      return new AuthResult(account.getAccountId(), null, false);
    }
    return null;
  }

  private AuthResult byUserName(final HttpServletResponse rsp,
      final String userName) {
    try {
      final ReviewDb db = schema.open();
      try {
        AccountExternalId.Key key =
            new AccountExternalId.Key(SCHEME_USERNAME, userName);
        return auth(accountExternalIdCache.get(key));
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      getServletContext().log("cannot query database", e);
      return null;
    }
  }

  private AuthResult byPreferredEmail(final HttpServletResponse rsp,
      final String email) {
    try {
      final ReviewDb db = schema.open();
      try {
        List<Account> matches = db.accounts().byPreferredEmail(email).toList();
        return matches.size() == 1 ? auth(matches.get(0)) : null;
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      getServletContext().log("cannot query database", e);
      return null;
    }
  }

  private AuthResult byAccountId(final HttpServletResponse rsp,
      final String idStr) {
    final Account.Id id;
    try {
      id = Account.Id.parse(idStr);
    } catch (NumberFormatException nfe) {
      return null;
    }
    try {
      final ReviewDb db = schema.open();
      try {
        return auth(db.accounts().get(id));
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      getServletContext().log("cannot query database", e);
      return null;
    }
  }

  private AuthResult create() {
    String fakeId = AccountExternalId.SCHEME_UUID + UUID.randomUUID();
    try {
      return accountManager.authenticate(new AuthRequest(fakeId));
    } catch (AccountException e) {
      getServletContext().log("cannot create new account", e);
      return null;
    }
  }
}
