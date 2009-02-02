// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.Link;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.RevId;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Rewrites Gerrit 1 style URLs to Gerrit 2 style URLs. */
public class UrlRewriteFilter implements Filter {
  private static final Pattern CHANGE_ID = Pattern.compile("^/(\\d+)/?$");
  private static final Pattern REV_ID =
      Pattern.compile("^/r/([0-9a-fA-F]{4," + RevId.LEN + "})/?$");
  private static final Pattern USER_PAGE = Pattern.compile("^/user/(.*)/?$");
  private static final Map<String, String> staticLinks;
  private static final Set<String> staticExtensions;

  static {
    staticLinks = new HashMap<String, String>();
    staticLinks.put("", "");
    staticLinks.put("/", "");

    staticLinks.put("/mine", Link.MINE);
    staticLinks.put("/starred", Link.MINE_STARRED);
    staticLinks.put("/settings", Link.SETTINGS);

    staticLinks.put("/all_unclaimed", Link.ALL_UNCLAIMED);
    staticLinks.put("/all", Link.ALL_MERGED);
    staticLinks.put("/open", Link.ALL_OPEN);

    staticExtensions = new HashSet<String>();
    staticExtensions.add(".css");
    staticExtensions.add(".gif");
    staticExtensions.add(".html");
    staticExtensions.add(".js");
    staticExtensions.add(".png");
  }

  private FilterConfig config;

  public void init(final FilterConfig config) throws ServletException {
    this.config = config;
    try {
      GerritServer.getInstance();
    } catch (OrmException e) {
      throw new ServletException("Cannot initialize GerritServer", e);
    } catch (XsrfException e) {
      throw new ServletException("Cannot initialize GerritServer", e);
    }
  }

  public void destroy() {
  }

  public void doFilter(final ServletRequest sreq, final ServletResponse srsp,
      final FilterChain chain) throws IOException, ServletException {
    final HttpServletRequest req = (HttpServletRequest) sreq;
    final HttpServletResponse rsp = (HttpServletResponse) srsp;
    final String pathInfo = pathInfo(req);

    if (pathInfo.startsWith("/rpc/")) {
      // RPC requests are very common in Gerrit 2, we want to make sure
      // they run quickly by jumping through the chain as fast as we can.
      //
      chain.doFilter(req, rsp);
    } else if (staticExtension(pathInfo, req, rsp, chain)) {
    } else if (staticLink(pathInfo, req, rsp)) {
    } else if (bareChangeId(pathInfo, req, rsp)) {
    } else if (bareRevisionId(pathInfo, req, rsp)) {
    } else if (bareUserEmailDashboard(pathInfo, req, rsp)) {
    } else {
      // Anything else is either a static resource request (which the container
      // can do for us) or is going to be a 404 error when the container cannot
      // find the resource. Either form of request is not very common compared
      // to the above cases.
      //
      chain.doFilter(req, rsp);
    }
  }

  private static boolean staticExtension(final String pathInfo,
      final HttpServletRequest req, final HttpServletResponse rsp,
      final FilterChain chain) throws IOException, ServletException {
    final int d = pathInfo.lastIndexOf('.');
    if (d > 0 && staticExtensions.contains(pathInfo.substring(d + 1))) {
      // Any URL which ends in this static extension is meant to be handled
      // by the servlet container, by returning a resource from the WAR.
      // We don't need to evaluate it any further.
      //
      chain.doFilter(req, rsp);
      return true;
    }
    return false;
  }

  private static boolean staticLink(final String pathInfo,
      final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    final String newLink = staticLinks.get(pathInfo);
    if (newLink == null) {
      return false;
    }

    // A static link (one with no parameters).
    //
    final StringBuffer url = toGerrit(req);
    if (newLink.length() > 0) {
      url.append('#');
      url.append(newLink);
    }
    rsp.sendRedirect(url.toString());
    return true;
  }

  private static boolean bareChangeId(final String pathInfo,
      final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    final Matcher m = CHANGE_ID.matcher(pathInfo);
    if (!m.matches()) {
      return false;
    }

    final Change.Id id = Change.Id.parse(m.group(1));
    final StringBuffer url = toGerrit(req);
    url.append('#');
    url.append(Link.toChange(id));
    rsp.sendRedirect(url.toString());
    return true;
  }

  private boolean bareRevisionId(final String pathInfo,
      final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    final Matcher m = REV_ID.matcher(pathInfo);
    if (!m.matches()) {
      return false;
    }

    final String rev = m.group(1).toLowerCase();
    if (rev.length() > RevId.LEN) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return true;
    }

    final RevId id = new RevId(rev);
    final List<PatchSet> patches;
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        if (id.isComplete()) {
          patches = db.patchSets().byRevision(id).toList();
        } else {
          patches = db.patchSets().byRevisionRange(id, id.max()).toList();
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      config.getServletContext().log("Unable to query for " + rev, e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return true;
    }

    if (patches.size() == 0) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    } else if (patches.size() == 1) {
      final StringBuffer url = toGerrit(req);
      url.append('#');
      url.append(Link.toChange(patches.get(0).getId().getParentKey()));
      rsp.sendRedirect(url.toString());
    } else {
      // TODO Someday this should be a menu of choices.
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    return true;
  }

  private boolean bareUserEmailDashboard(final String pathInfo,
      final HttpServletRequest req, final HttpServletResponse rsp)
      throws IOException {
    final Matcher m = USER_PAGE.matcher(pathInfo);
    if (!m.matches()) {
      return false;
    }

    final String email = cleanEmail(m.group(1));
    final List<Account> people;
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
      try {
        people = db.accounts().byPreferredEmail(email).toList();
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      config.getServletContext().log("Unable to query for " + email, e);
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return true;
    }

    if (people.size() == 0) {
      rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
    } else if (people.size() == 1) {
      final StringBuffer url = toGerrit(req);
      url.append('#');
      url.append(Link.toAccountDashboard(people.get(0).getId()));
      rsp.sendRedirect(url.toString());
    } else {
      // TODO Someday this should be a menu of choices.
      rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
    return true;
  }

  private static String cleanEmail(final String email) {
    int dc = email.indexOf(",,");
    if (dc >= 0) {
      return email.substring(0, dc) + "@" + email.substring(dc + 2);
    }

    dc = email.indexOf(',');
    if (dc >= 0) {
      return email.substring(0, dc) + "@" + email.substring(dc + 1);
    }
    return email;
  }

  private static String pathInfo(final HttpServletRequest req) {
    final String uri = req.getRequestURI();
    final String ctx = req.getContextPath();
    return uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
  }

  private static StringBuffer toGerrit(final HttpServletRequest req) {
    final StringBuffer url = new StringBuffer();
    url.append(req.getContextPath());
    url.append("/Gerrit");
    return url;
  }
}
