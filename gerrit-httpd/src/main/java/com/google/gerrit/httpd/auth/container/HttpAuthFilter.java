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

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.gerrit.reviewdb.client.AccountExternalId.SCHEME_GERRIT;

import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.raw.HostPageServlet;
import com.google.gerrit.reviewdb.client.AccountExternalId;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.util.Base64;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Watches request for the host page and requires login if not yet signed in.
 * <p>
 * If HTTP authentication has been enabled on this server this filter is bound
 * in front of the {@link HostPageServlet} and redirects users who are not yet
 * signed in to visit {@code /login/}, so the web container can force login.
 * This redirect is performed with JavaScript, such that any existing anchor
 * token in the URL can be rewritten and preserved through the authentication
 * process of any enterprise single sign-on solutions.
 */
@Singleton
class HttpAuthFilter implements Filter {
  private final Provider<WebSession> sessionProvider;
  private final byte[] signInRaw;
  private final byte[] signInGzip;
  private final String loginHeader;

  @Inject
  HttpAuthFilter(final Provider<WebSession> webSession,
      final AuthConfig authConfig) throws IOException {
    this.sessionProvider = webSession;

    final String pageName = "LoginRedirect.html";
    final String doc = HtmlDomUtil.readFile(getClass(), pageName);
    if (doc == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }

    signInRaw = doc.getBytes(HtmlDomUtil.ENC);
    signInGzip = HtmlDomUtil.compress(signInRaw);
    loginHeader = firstNonNull(
        emptyToNull(authConfig.getLoginHttpHeader()),
        AUTHORIZATION);
  }

  @Override
  public void doFilter(final ServletRequest request,
      final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    if (isSessionValid((HttpServletRequest) request)) {
      chain.doFilter(request, response);
    } else {
      // Not signed in yet. Since the browser state might have an anchor
      // token which we want to capture and carry through the auth process
      // we send back JavaScript now to capture that, and do the real work
      // of redirecting to the authentication area.
      //
      final HttpServletRequest req = (HttpServletRequest) request;
      final HttpServletResponse rsp = (HttpServletResponse) response;
      final byte[] tosend;
      if (RPCServletUtils.acceptsGzipEncoding(req)) {
        rsp.setHeader("Content-Encoding", "gzip");
        tosend = signInGzip;
      } else {
        tosend = signInRaw;
      }

      CacheHeaders.setNotCacheable(rsp);
      rsp.setContentType("text/html");
      rsp.setCharacterEncoding(HtmlDomUtil.ENC);
      rsp.setContentLength(tosend.length);
      final OutputStream out = rsp.getOutputStream();
      try {
        out.write(tosend);
      } finally {
        out.close();
      }
    }
  }

  private boolean isSessionValid(HttpServletRequest req) {
    WebSession session = sessionProvider.get();
    if (session.isSignedIn()) {
      String user = getRemoteUser(req);
      return user == null || correctUser(user, session);
    }
    return false;
  }

  private static boolean correctUser(String user, WebSession session) {
    AccountExternalId.Key id = session.getLastLoginExternalId();
    return id != null
        && id.equals(new AccountExternalId.Key(SCHEME_GERRIT, user));
  }

  String getRemoteUser(HttpServletRequest req) {
    if (AUTHORIZATION.equals(loginHeader)) {
      String user = emptyToNull(req.getRemoteUser());
      if (user != null) {
        // The container performed the authentication, and has the user
        // identity already decoded for us. Honor that as we have been
        // configured to honor HTTP authentication.
        return user;
      }

      // If the container didn't do the authentication we might
      // have done it in the front-end web server. Try to split
      // the identity out of the Authorization header and honor it.
      //
      String auth = emptyToNull(req.getHeader(AUTHORIZATION));
      if (auth == null) {
        return null;

      } else if (auth.startsWith("Basic ")) {
        auth = auth.substring("Basic ".length());
        auth = new String(Base64.decode(auth));
        final int c = auth.indexOf(':');
        return c > 0 ? auth.substring(0, c) : null;

      } else if (auth.startsWith("Digest ")) {
        int u = auth.indexOf("username=\"");
        if (u <= 0) {
          return null;
        }
        auth = auth.substring(u + 10);
        int e = auth.indexOf('"');
        return e > 0 ? auth.substring(0, auth.indexOf('"')) : null;

      } else {
        return null;
      }
    } else {
      // Nonstandard HTTP header. We have been told to trust this
      // header blindly as-is.
      //
      return emptyToNull(req.getHeader(loginHeader));
    }
  }

  String getLoginHeader() {
    return loginHeader;
  }

  @Override
  public void init(final FilterConfig filterConfig) {
  }

  @Override
  public void destroy() {
  }
}
