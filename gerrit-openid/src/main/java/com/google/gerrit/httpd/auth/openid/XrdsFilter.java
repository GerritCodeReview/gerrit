package com.google.gerrit.httpd.auth.openid;

import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

@Singleton
class XrdsFilter implements Filter {
  private final Provider<String> url;

  @Inject
  XrdsFilter(@CanonicalWebUrl final Provider<String> url) {
    this.url = url;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletResponse rsp = (HttpServletResponse) response;
    rsp.setHeader("X-XRDS-Location", url.get() + XrdsServlet.LOCATION);
    chain.doFilter(request, response);
  }

  @Override
  public void init(FilterConfig config) {}

  @Override
  public void destroy() {}
}
