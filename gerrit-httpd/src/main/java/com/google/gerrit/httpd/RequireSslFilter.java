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

package com.google.gerrit.httpd;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Requires the connection to use SSL, redirects if not. */
@Singleton
public class RequireSslFilter implements Filter {
  public static class Module extends ServletModule {
    private final boolean wantSsl;

    @Inject
    Module(@Nullable @CanonicalWebUrl String canonicalUrl) {
      this.wantSsl = canonicalUrl != null && canonicalUrl.startsWith("https:");
    }

    @Override
    protected void configureServlets() {
      if (wantSsl) {
        filter("/*").through(RequireSslFilter.class);
      }
    }
  }

  private final Provider<String> urlProvider;

  @Inject
  RequireSslFilter(@CanonicalWebUrl @Nullable Provider<String> urlProvider) {
    this.urlProvider = urlProvider;
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}

  @Override
  public void doFilter(
      ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    final HttpServletRequest req = (HttpServletRequest) request;
    final HttpServletResponse rsp = (HttpServletResponse) response;

    if (isSecure(req)) {
      chain.doFilter(request, response);

    } else {
      // If we wanted SSL, but the user didn't come to us over it,
      // force SSL by issuing a protocol redirect. Try to keep the
      // name "localhost" in case this is an SSH port tunnel.
      //
      final String url;
      if (isLocalHost(req)) {
        final StringBuffer b = req.getRequestURL();
        b.replace(0, b.indexOf(":"), "https");
        url = b.toString();

      } else {
        url = urlProvider.get() + req.getServletPath();
      }
      rsp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
      rsp.setHeader("Location", url);
    }
  }

  private static boolean isSecure(HttpServletRequest req) {
    return "https".equals(req.getScheme()) || req.isSecure();
  }

  private static boolean isLocalHost(HttpServletRequest req) {
    return "localhost".equals(req.getServerName()) || "127.0.0.1".equals(req.getServerName());
  }
}
