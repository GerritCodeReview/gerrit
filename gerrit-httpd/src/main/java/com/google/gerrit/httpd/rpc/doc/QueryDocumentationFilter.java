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

package com.google.gerrit.httpd.rpc.doc;

import com.google.common.base.Strings;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor.DocQueryException;
import com.google.gerrit.server.documentation.QueryDocumentationExecutor.DocResult;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QueryDocumentationFilter implements Filter {
  private final Logger log = LoggerFactory.getLogger(QueryDocumentationFilter.class);

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
        Multimap<String, String> config = LinkedHashMultimap.create();
        RestApiServlet.replyJson(req, rsp, config, result);
      } catch (DocQueryException e) {
        log.error("Doc search failed:", e);
        rsp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
    } else {
      chain.doFilter(request, response);
    }
  }
}
