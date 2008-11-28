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

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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

public class UrlRewriteFilter implements Filter {
  private static final Pattern CHANGE_ID = Pattern.compile("^/(\\d+)$");
  private static final Pattern REV_ID = Pattern.compile("^/r/([0-9a-f]{40})$");
  private static final Pattern USER_PAGE = Pattern.compile("^/user/(.*)$");
  private static final Map<String, String> staticLinks;

  static {
    staticLinks = new HashMap<String, String>();
    staticLinks.put("/mine", Link.MINE);
    staticLinks.put("/unclaimed", Link.MINE_UNCLAIMED);
    staticLinks.put("/starred", Link.MINE_STARRED);

    staticLinks.put("/all", Link.ALL);
    staticLinks.put("/all_unclaimed", Link.ALL_UNCLAIMED);
    staticLinks.put("/open", Link.ALL_OPEN);
  }

  public void init(final FilterConfig arg0) {
  }

  public void destroy() {
  }

  public void doFilter(final ServletRequest sreq, final ServletResponse srsp,
      final FilterChain chain) throws IOException, ServletException {
    final HttpServletRequest req = (HttpServletRequest) sreq;
    final HttpServletResponse rsp = (HttpServletResponse) srsp;
    final String pathInfo = pathInfo(req);

    if (pathInfo.startsWith("/rpc/")) {
      chain.doFilter(req, rsp);
      return;
    }
    if (pathInfo.equals("/") || pathInfo.equals("")) {
      rsp.sendRedirect(toGerrit(req).toString());
      return;
    }
    {
      final String newLink = staticLinks.get(pathInfo);
      if (newLink != null) {
        final StringBuffer url = toGerrit(req);
        url.append("#");
        url.append(newLink);
        rsp.sendRedirect(url.toString());
        return;
      }
    }
    {
      final Matcher m = CHANGE_ID.matcher(pathInfo);
      if (m.matches()) {
        final Change.Id id = new Change.Id(Integer.parseInt(m.group(1)));
        final StringBuffer url = toGerrit(req);
        url.append("#");
        url.append(Link.toChange(id));
        rsp.sendRedirect(url.toString());
        return;
      }
    }
    {
      final Matcher m = REV_ID.matcher(pathInfo);
      if (m.matches()) {
        final Change.Id id = new Change.Id(Integer.parseInt(m.group(1)));
        final StringBuffer url = toGerrit(req);
        url.append("#");
        url.append(Link.toChange(id));
        rsp.sendRedirect(url.toString());
        return;
      }
    }
    {
      final Matcher m = USER_PAGE.matcher(pathInfo);
      if (m.matches()) {
        final Account.Id id = new Account.Id(Integer.parseInt(m.group(1)));
        final StringBuffer url = toGerrit(req);
        url.append("#");
        url.append(Link.toAccountDashboard(id));
        rsp.sendRedirect(url.toString());
        return;
      }
    }

    chain.doFilter(req, rsp);
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
