// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static com.google.common.base.Strings.nullToEmpty;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.http.server.GitSmartHttpTools;

@Singleton
public class XsrfCookieFilter implements Filter {
  private final Provider<CurrentUser> user;
  private final DynamicItem<WebSession> session;

  @Inject
  XsrfCookieFilter(Provider<CurrentUser> user, DynamicItem<WebSession> session) {
    this.user = user;
    this.session = session;
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse rsp, FilterChain chain)
      throws IOException, ServletException {
    try (TraceTimer ignored = TraceContext.newTimer("XsrfCookieFilter#preprocess")) {
      HttpServletRequest httpRequest = (HttpServletRequest) req;
      if (!GitSmartHttpTools.isGitClient(httpRequest)) {
        WebSession s = user.get().isIdentifiedUser() ? session.get() : null;
        setXsrfTokenCookie(httpRequest, (HttpServletResponse) rsp, s);
      }
    }
    chain.doFilter(req, rsp);
  }

  private void setXsrfTokenCookie(
      HttpServletRequest req, HttpServletResponse rsp, WebSession session) {
    String v = session != null ? session.getXGerritAuth() : null;
    Cookie c = new Cookie(XsrfConstants.XSRF_COOKIE_NAME, nullToEmpty(v));
    c.setPath("/");
    c.setSecure(isSecure(req));
    c.setMaxAge(
        v != null
            ? -1 // Set the cookie for this browser session.
            : 0); // Remove the cookie (expire immediately).
    rsp.addCookie(c);
  }

  private boolean isSecure(HttpServletRequest req) {
    return req.isSecure() || "https".equals(req.getScheme());
  }

  @Override
  public void init(FilterConfig config) {}

  @Override
  public void destroy() {}
}
