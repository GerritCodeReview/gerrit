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

package com.google.gerrit.server;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class BecomeAnyAccountLoginServlet extends HttpServlet {
  private boolean allowed;
  private GerritServer server;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    try {
      allowed = Boolean.getBoolean(getClass().getName());
    } catch (SecurityException se) {
      allowed = false;
    }

    try {
      server = GerritServer.getInstance();
    } catch (OrmException e) {
      throw new ServletException("Cannot load GerritServer", e);
    } catch (XsrfException e) {
      throw new ServletException("Cannot load GerritServer", e);
    }
  }

  @Override
  protected void doGet(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    if (!allowed) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    rsp.setContentType("text/html");
    final ServletOutputStream out = rsp.getOutputStream();
    out.print("<html>");
    out.print("<form method=\"POST\"><b>ssh_user_name:</b> "
        + "<input type=\"text\" size=\"30\" name=\"ssh_user_name\" />"
        + "<input type=\"submit\" value=\"Become Account\" />" + "</form>");
    out.print("<form method=\"POST\"><b>preferred_email:</b> "
        + "<input type=\"text\" size=\"30\" name=\"preferred_email\" />"
        + "<input type=\"submit\" value=\"Become Account\" />" + "</form>");
    out.print("<form method=\"POST\"><b>account_id:</b> "
        + "<input type=\"text\" size=\"12\" name=\"account_id\" />"
        + "<input type=\"submit\" value=\"Become Account\" />" + "</form>");
    out.print("</html>");
  }

  @Override
  protected void doPost(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    if (!allowed) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    final List<Account> accounts;
    if (req.getParameter("ssh_user_name") != null) {
      accounts = bySshUserName(rsp, req.getParameter("ssh_user_name"));

    } else if (req.getParameter("preferred_email") != null) {
      accounts = byPreferredEmail(rsp, req.getParameter("preferred_email"));

    } else if (req.getParameter("account_id") != null) {
      accounts = byAccountId(rsp, req.getParameter("account_id"));

    } else {
      doGet(req, rsp);
      return;
    }

    if (accounts.size() == 1) {
      final Account account = accounts.get(0);
      final Cookie c = new Cookie(Gerrit.ACCOUNT_COOKIE, "");
      c.setPath(req.getContextPath() + "/");
      new AccountCookie(account.getId(), false).set(c, server);
      rsp.addCookie(c);
      rsp.sendRedirect("Gerrit.html");

    } else {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  private List<Account> bySshUserName(final HttpServletResponse rsp,
      final String userName) {
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        return db.accounts().bySshUserName(userName).toList();
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
      final ReviewDb db = Common.getSchemaFactory().open();
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
      final ReviewDb db = Common.getSchemaFactory().open();
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
