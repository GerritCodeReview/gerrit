// Copyright (C) 2015 The Android Open Source Project
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

import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

@Singleton
public class RequestMetricsFilter implements Filter {
  public static Module module() {
    return new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/*").through(RequestMetricsFilter.class);
      }
    };
  }

  private final RequestMetrics metrics;
  private final AtomicLong requestCount;

  @Inject
  RequestMetricsFilter(RequestMetrics metrics) {
    this.metrics = metrics;
    this.requestCount = new AtomicLong();
  }

  @Override
  public void destroy() {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    Response rsp = new Response((HttpServletResponse) response, (HttpServletRequest) request);

    chain.doFilter(request, rsp);
  }

  @Override
  public void init(FilterConfig cfg) throws ServletException {}

  public long getRequestCount() {
    return requestCount.get();
  }

  /**
   * Wrapper that records status metrics.
   *
   * <p>The status is recorded into a metric before setting the status on the wrapped {@link
   * HttpServletResponse}. As a result, broken connections are recorded under their original status
   * code, and are recorded in {@link #getRequestCount()}.
   */
  private class Response extends HttpServletResponseWrapper {
    Response(HttpServletResponse response, HttpServletRequest request) {
      super(response);
      System.err.println(
          "=== HTTP Request: " + request.getRequestURL() + "?" + request.getQueryString());
      System.err.println("From " + RequestMetricsFilter.this);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      System.err.println("sendError with msg " + sc);
      status(sc);
      super.sendError(sc, msg);
    }

    @Override
    public void sendError(int sc) throws IOException {
      System.err.println("sendError " + sc);
      status(sc);
      super.sendError(sc);
    }

    @Override
    @Deprecated
    public void setStatus(int sc, String sm) {
      System.err.println("setStatus with msg " + sc);
      status(sc);
      super.setStatus(sc, sm);
    }

    @Override
    public void setStatus(int sc) {
      System.err.println("setStatus " + sc);
      status(sc);
      super.setStatus(sc);
    }

    private void status(int sc) {
      requestCount.incrementAndGet();
      if (sc >= SC_BAD_REQUEST) {
        metrics.errors.increment(sc);
      } else {
        metrics.successes.increment(sc);
      }
    }
  }
}
