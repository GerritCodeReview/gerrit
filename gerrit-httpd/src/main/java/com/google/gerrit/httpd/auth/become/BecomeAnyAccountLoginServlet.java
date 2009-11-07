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

import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.Nullable;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
@Singleton
public class BecomeAnyAccountLoginServlet extends HttpServlet {
  private final SchemaFactory<ReviewDb> schema;
  private final Provider<WebSession> webSession;
  private final Provider<String> urlProvider;
  private final byte[] raw;

  @Inject
  BecomeAnyAccountLoginServlet(final Provider<WebSession> ws,
      final SchemaFactory<ReviewDb> sf,
      final @CanonicalWebUrl @Nullable Provider<String> up,
      final ServletContext servletContext) throws IOException {
    webSession = ws;
    schema = sf;
    urlProvider = up;

    final String pageName = "BecomeAnyAccount.html";
    final String doc = HtmlDomUtil.readFile(getClass(), pageName);
    if (doc == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }

    raw = doc.getBytes(HtmlDomUtil.ENC);
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

    final List<Account> accounts;
    if (req.getParameter("ssh_user_name") != null) {
      accounts = bySshUserName(rsp, req.getParameter("ssh_user_name"));

    } else if (req.getParameter("preferred_email") != null) {
      accounts = byPreferredEmail(rsp, req.getParameter("preferred_email"));

    } else if (req.getParameter("account_id") != null) {
      accounts = byAccountId(rsp, req.getParameter("account_id"));

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

    if (accounts.size() == 1) {
      final Account account = accounts.get(0);
      webSession.get().login(account.getId(), false);
      rsp.sendRedirect(urlProvider.get());

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

  private List<Account> bySshUserName(final HttpServletResponse rsp,
      final String userName) {
    try {
      final ReviewDb db = schema.open();
      try {
        final Account account = db.accounts().bySshUserName(userName);
        return account != null ? Collections.<Account> singletonList(account)
            : Collections.<Account> emptyList();
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      getServletContext().log("cannot query database", e);
      return Collections.<Account> emptyList();
    }
  }

  private List<Account> byPreferredEmail(final HttpServletResponse rsp,
      final String email) {
    try {
      final ReviewDb db = schema.open();
      try {
        return db.accounts().byPreferredEmail(email).toList();
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      getServletContext().log("cannot query database", e);
      return Collections.<Account> emptyList();
    }
  }

  private List<Account> byAccountId(final HttpServletResponse rsp,
      final String idStr) {
    final Account.Id id;
    try {
      id = Account.Id.parse(idStr);
    } catch (NumberFormatException nfe) {
      return Collections.<Account> emptyList();
    }
    try {
      final ReviewDb db = schema.open();
      try {
        final Account account = db.accounts().get(id);
        return account != null ? Collections.<Account> singletonList(account)
            : Collections.<Account> emptyList();
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      getServletContext().log("cannot query database", e);
      return Collections.<Account> emptyList();
    }
  }
}
