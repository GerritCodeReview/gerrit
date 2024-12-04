// Copyright (C) 2024 The Android Open Source Project
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

import static com.google.gerrit.httpd.GerritHeaders.X_GERRIT_TRACE;

import com.google.common.base.Strings;
import com.google.gerrit.httpd.restapi.ParameterParser;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.logging.TraceContext;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This filter associates a trace ID to each http request. If requested, forced tracing is also
 * enabled.
 *
 * <p>There are 2 ways to force tracing for http requests: 1. by using the 'trace' or
 * 'trace=<trace-id>' request parameter 2. by setting the 'X-Gerrit-Trace:' or
 * 'X-Gerrit-Trace:<trace-id>' header
 */
@Singleton
public class EnableTracingFilter implements Filter {

  public static final String REQUEST_TRACE_CONTEXT = "REQUEST_TRACE_CONTEXT";

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  private int count;

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    System.out.printf("%d EnableTracingFilter.doFilter\n", ++count);
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;
    try (TraceContext traceContext = enableTracing(req, res)) {
      request.setAttribute(REQUEST_TRACE_CONTEXT, traceContext);
      chain.doFilter(request, response);
    }
  }

  private TraceContext enableTracing(HttpServletRequest req, HttpServletResponse res) {
    String traceValueFromHeader = req.getHeader(X_GERRIT_TRACE);
    String traceValueFromRequestParam = req.getParameter(ParameterParser.TRACE_PARAMETER);
    boolean forceLogging = traceValueFromHeader != null || traceValueFromRequestParam != null;

    // Check whether no trace ID, one trace ID or 2 different trace IDs have been specified.
    String traceId1;
    String traceId2;
    if (!Strings.isNullOrEmpty(traceValueFromHeader)) {
      traceId1 = traceValueFromHeader;
      if (!Strings.isNullOrEmpty(traceValueFromRequestParam)
          && !traceValueFromHeader.equals(traceValueFromRequestParam)) {
        traceId2 = traceValueFromRequestParam;
      } else {
        traceId2 = null;
      }
    } else {
      traceId1 = Strings.emptyToNull(traceValueFromRequestParam);
      traceId2 = null;
    }

    // Use the first trace ID to start tracing. If this trace ID is null, a trace ID will be
    // generated.
    TraceContext traceContext =
        TraceContext.newTrace(
            forceLogging, traceId1, (tagName, traceId) -> res.setHeader(X_GERRIT_TRACE, traceId));
    // If a second trace ID was specified, add a tag for it as well.
    if (traceId2 != null) {
      traceContext.addTag(RequestId.Type.TRACE_ID, traceId2);
      res.addHeader(X_GERRIT_TRACE, traceId2);
    }
    return traceContext;
  }

  @Override
  public void destroy() {}
}
