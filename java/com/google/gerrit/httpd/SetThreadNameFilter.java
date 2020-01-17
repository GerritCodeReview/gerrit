// Copyright (C) 2020 The Android Open Source Project
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

import com.google.gerrit.server.CurrentUser;
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
import javax.servlet.http.HttpServletRequest;

@Singleton
public class SetThreadNameFilter implements Filter {
  private static final int MAX_PATH_LENGTH = 120;

  public static Module module() {
    return new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/*").through(SetThreadNameFilter.class);
      }
    };
  }

  private final Provider<CurrentUser> user;

  @Inject
  public SetThreadNameFilter(Provider<CurrentUser> user) {
    this.user = user;
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Thread current = Thread.currentThread();
    String old = current.getName();
    try {
      current.setName(computeName((HttpServletRequest) request));
      chain.doFilter(request, response);
    } finally {
      current.setName(old);
    }
  }

  private String computeName(HttpServletRequest req) {
    StringBuilder s = new StringBuilder();
    s.append("HTTP ");
    s.append(req.getMethod());
    s.append(" ");
    s.append(req.getRequestURI());
    String query = req.getQueryString();
    if (query != null) {
      s.append("?").append(query);
    }
    if (s.length() > MAX_PATH_LENGTH) {
      s.delete(MAX_PATH_LENGTH, s.length());
    }
    s.append(" (");
    s.append(user.get().getUserName().orElse("N/A"));
    s.append(" from ");
    s.append(req.getRemoteAddr());
    s.append(")");
    return s.toString();
  }

  @Override
  public void destroy() {}
}
