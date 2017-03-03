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
import java.util.List;
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
import com.google.common.net.HttpHeaders;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

@Singleton
class LinkPreloadFilter implements Filter {

  private static final ImmutableList<String> COMMON_PRELOAD =
      ImmutableList.of(
          "/accounts/self/detail",
          "/config/server/info",
          "/config/server/version"
          );

  // TODO (viktard): experiment with optimal amount of preloading.
  private static final ImmutableMap<String, ImmutableList<String>> PRELOAD_MAP =
      ImmutableMap.of(
          "^/$", COMMON_PRELOAD,
          "^/dashboard/self$", COMMON_PRELOAD,
          "^/q/.*", COMMON_PRELOAD,
          "^/c/.*", COMMON_PRELOAD,
          "^/c/(\\d+)/?$", ImmutableList.of(
              "/changes/$1/detail?O=11640c")
          );

  static class Module extends ServletModule {

    @Override
    protected void configureServlets() {
      for (Map.Entry<String, ImmutableList<String>> entry : PRELOAD_MAP.entrySet()) {
        String key = entry.getKey();
        filterRegex(key).through(new LinkPreloadFilter(key, entry.getValue()));
      }
    }
  }

  private final String key;
  private final List<String> urls;

  LinkPreloadFilter(String key, List<String> urls) {
    this.key = key;
    this.urls = urls;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    if ("GET".equals(req.getMethod())) {
      String path = req.getServletPath();
      HttpServletResponse rsp = (HttpServletResponse) response;
      for (String url : urls) {
        rsp.addHeader(HttpHeaders.LINK,
            String.format("<%s>;rel=\"preload\";crossorigin",
                req.getContextPath() + path.replaceAll(key, url)));
      }
    }
    chain.doFilter(request, response);
  }

  @Override
  public void destroy() {}

  @Override
  public void init(FilterConfig config) throws ServletException {}
}
