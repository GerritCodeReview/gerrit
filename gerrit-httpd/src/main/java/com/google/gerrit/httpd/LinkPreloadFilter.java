// Copyright (C) 2017 The Android Open Source Project
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

import java.io.IOException;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

@Singleton
class LinkPreloadFilter implements Filter {

  private static final ImmutableMap<String, ImmutableList<String>> PRELOAD_MAP =
      ImmutableMap.of(
          "^/q/.*", ImmutableList.of(
              "/accounts/self/detail",
              "/config/server/info",
              "/config/server/version"
              ),
          "^/c/.*", ImmutableList.of(
              "/accounts/self/detail",
              "/config/server/info",
              "/config/server/version"),
          "^/c/(\\d+)/?$", ImmutableList.of(
              "/changes/$1/detail?O=11640c")
          );

  static class Module extends ServletModule {

    @Override
    protected void configureServlets() {
      for (Map.Entry<String, ImmutableList<String>> entry : PRELOAD_MAP.entrySet()) {
        String key = entry.getKey();
        for (String url : entry.getValue()) {
          filterRegex(key).through(new LinkPreloadFilter(key, url));
        }
      }
    }
  }

  private final String key;
  private final String url;

  LinkPreloadFilter(String key, String url) {
    this.key = key;
    this.url = url;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    if ("GET".equals(req.getMethod())) {
      HttpServletResponse rsp = (HttpServletResponse) response;
      rsp.addHeader("Link",
          String.format("<%s>;rel=\"preload\";crossorigin",
              req.getRequestURI().replaceAll(key, url)));
    }
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {}

  @Override
  public void init(FilterConfig config) throws ServletException {}
}
