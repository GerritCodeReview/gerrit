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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.net.HttpHeaders.AUTHORIZATION;
import static com.google.gerrit.server.account.externalids.ExternalId.SCHEME_GERRIT;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.RemoteUserUtil;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.raw.HostPageServlet;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
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
 *
 * <p>If HTTP authentication has been enabled on this server this filter is bound in front of the
 * {@link HostPageServlet} and redirects users who are not yet signed in to visit {@code /login/},
 * so the web container can force login. This redirect is performed with JavaScript, such that any
 * existing anchor token in the URL can be rewritten and preserved through the authentication
 * process of any enterprise single sign-on solutions.
 */
@Singleton
class HttpAuthFilter implements Filter {
  private final DynamicItem<WebSession> sessionProvider;
  private final byte[] signInRaw;
  private final byte[] signInGzip;
  private final String loginHeader;
  private final String displaynameHeader;
  private final String emailHeader;
  private final String externalIdHeader;
  private final boolean userNameToLowerCase;

  @Inject
  HttpAuthFilter(DynamicItem<WebSession> webSession, AuthConfig authConfig) throws IOException {
    this.sessionProvider = webSession;

    final String pageName = "LoginRedirect.html";
    final String doc = HtmlDomUtil.readFile(getClass(), pageName);
    if (doc == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }

    signInRaw = doc.getBytes(HtmlDomUtil.ENC);
    signInGzip = HtmlDomUtil.compress(signInRaw);
    loginHeader = firstNonNull(emptyToNull(authConfig.getLoginHttpHeader()), AUTHORIZATION);
    displaynameHeader = emptyToNull(authConfig.getHttpDisplaynameHeader());
    emailHeader = emptyToNull(authConfig.getHttpEmailHeader());
    externalIdHeader = emptyToNull(authConfig.getHttpExternalIdHeader());
    userNameToLowerCase = authConfig.isUserNameToLowerCase();
  }

  @Override
  public void doFilter(final ServletRequest request, ServletResponse response, FilterChain chain)
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
      rsp.setCharacterEncoding(HtmlDomUtil.ENC.name());
      rsp.setContentLength(tosend.length);
      try (OutputStream out = rsp.getOutputStream()) {
        out.write(tosend);
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
    ExternalId.Key id = session.getLastLoginExternalId();
    return id != null && id.equals(ExternalId.Key.create(SCHEME_GERRIT, user));
  }

  String getRemoteUser(HttpServletRequest req) {
    String remoteUser = RemoteUserUtil.getRemoteUser(req, loginHeader);
    return (userNameToLowerCase && remoteUser != null)
        ? remoteUser.toLowerCase(Locale.US)
        : remoteUser;
  }

  String getRemoteDisplayname(HttpServletRequest req) {
    if (displaynameHeader != null) {
      String raw = req.getHeader(displaynameHeader);
      return emptyToNull(new String(raw.getBytes(ISO_8859_1), UTF_8));
    }
    return null;
  }

  String getRemoteEmail(HttpServletRequest req) {
    if (emailHeader != null) {
      return emptyToNull(req.getHeader(emailHeader));
    }
    return null;
  }

  String getRemoteExternalIdToken(HttpServletRequest req) {
    if (externalIdHeader != null) {
      return emptyToNull(req.getHeader(externalIdHeader));
    }
    return null;
  }

  String getLoginHeader() {
    return loginHeader;
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}
}
