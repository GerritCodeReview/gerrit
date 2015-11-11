package com.google.gerrit.httpd;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jetty.http.HttpStatus;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

@Singleton
public class RequestMetricsFilter implements Filter {

  private final RequestMetrics metrics;

  @Inject
  RequestMetricsFilter(RequestMetrics metrics) {
    this.metrics = metrics;
  }

  @Override
  public void destroy() {
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    Response rsp = new Response((HttpServletResponse) response, metrics);

    chain.doFilter(request, rsp);
  }

  @Override
  public void init(FilterConfig cfg) throws ServletException {
  }

  static class Response extends HttpServletResponseWrapper {
    private final RequestMetrics metrics;
    public Response(HttpServletResponse response, RequestMetrics metrics) {
      super(response);
      this.metrics = metrics;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      status(sc);
      super.sendError(sc, msg);
    }

    @Override
    public void sendError(int sc) throws IOException {
      status(sc);
      super.sendError(sc);
    }

    @Override
    @Deprecated
    public void setStatus(int sc, String sm) {
      status(sc);
      super.setStatus(sc, sm);
    }

    @Override
    public void setStatus(int sc) {
      status(sc);
      super.setStatus(sc);
    }

    private void status(int sc) {
      if (sc >= HttpStatus.BAD_REQUEST_400) {
        metrics.errors.increment(sc);
      } else {
        metrics.successes.increment(sc);
      }
    }
  }
}
