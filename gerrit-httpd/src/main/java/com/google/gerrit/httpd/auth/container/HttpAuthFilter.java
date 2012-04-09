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

import com.google.gerrit.httpd.HtmlDomUtil;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.httpd.raw.HostPageServlet;
import com.google.gwtjsonrpc.server.RPCServletUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
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
  private final Provider<WebSession> webSession;
  private final byte[] signInRaw;
  private final byte[] signInGzip;

  @Inject
  HttpAuthFilter(final Provider<WebSession> webSession,
      final ServletContext servletContext) throws IOException {
    this.webSession = webSession;

    final String pageName = "LoginRedirect.html";
    final String doc = HtmlDomUtil.readFile(getClass(), pageName);
    if (doc == null) {
      throw new FileNotFoundException("No " + pageName + " in webapp");
    }

    signInRaw = doc.getBytes(HtmlDomUtil.ENC);
    signInGzip = HtmlDomUtil.compress(signInRaw);
  }

  @Override
  public void doFilter(final ServletRequest request,
      final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    if (!webSession.get().isSignedIn()) {
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

      rsp.setHeader("Expires", "Fri, 01 Jan 1980 00:00:00 GMT");
      rsp.setHeader("Pragma", "no-cache");
      rsp.setHeader("Cache-Control", "no-cache, must-revalidate");
      rsp.setContentType("text/html");
      rsp.setCharacterEncoding(HtmlDomUtil.ENC);
      rsp.setContentLength(tosend.length);
      final OutputStream out = rsp.getOutputStream();
      try {
        out.write(tosend);
      } finally {
        out.close();
      }
    } else {
      // Already signed in, forward the request.
      //
      chain.doFilter(request, response);
    }
  }

  @Override
  public void init(final FilterConfig filterConfig) {
  }

  @Override
  public void destroy() {
  }
}
