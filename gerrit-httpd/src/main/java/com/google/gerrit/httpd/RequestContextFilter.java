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

import com.google.gerrit.server.RequestCleanup;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.Module;
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

/** Executes any pending {@link RequestCleanup} at the end of a request. */
@Singleton
public class RequestContextFilter implements Filter {
  public static Module module() {
    return new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/*").through(RequestContextFilter.class);
      }
    };
  }

  private final Provider<RequestCleanup> cleanup;
  private final Provider<HttpRequestContext> requestContext;
  private final ThreadLocalRequestContext local;

  @Inject
  RequestContextFilter(
      final Provider<RequestCleanup> r,
      final Provider<HttpRequestContext> c,
      final ThreadLocalRequestContext l) {
    cleanup = r;
    requestContext = c;
    local = l;
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws IOException, ServletException {
    RequestContext old = local.setContext(requestContext.get());
    try {
      try {
        chain.doFilter(request, response);
      } finally {
        cleanup.get().run();
      }
    } finally {
      local.setContext(old);
    }
  }
}
