// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor.DocQueryException;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor.DocResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@Singleton
public class QueryDocumentationFilter implements Filter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final QueryDocumentationExecutor searcher;

  @Inject
  QueryDocumentationFilter(QueryDocumentationExecutor searcher) {
    this.searcher = searcher;
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    if ("GET".equals(req.getMethod()) && !Strings.isNullOrEmpty(req.getParameter("q"))) {
      HttpServletResponse rsp = (HttpServletResponse) response;
      try {
        List<DocResult> result = searcher.doQuery(request.getParameter("q"));
        RestApiServlet.replyJson(req, rsp, false, ImmutableListMultimap.of(), result);
      } catch (DocQueryException e) {
        logger.atSevere().withCause(e).log("Doc search failed");
        rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
    } else {
      chain.doFilter(request, response);
    }
  }
}
