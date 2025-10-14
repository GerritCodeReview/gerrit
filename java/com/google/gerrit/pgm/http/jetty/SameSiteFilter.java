package com.google.gerrit.pgm.http.jetty;

import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.eclipse.jetty.http.HttpCookie;

public class SameSiteFilter implements Filter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletResponse rsp = (HttpServletResponse) response;
    chain.doFilter(
        request,
        new HttpServletResponseWrapper(rsp) {
          @Override
          public void addCookie(Cookie cookie) {
            logger.atFine().log("Setting SameSite attribute on: %s", cookie.getName());
            cookie.setComment(HttpCookie.SAME_SITE_STRICT_COMMENT);
            super.addCookie(cookie);
          }
        });
  }

  @Override
  public void destroy() {}
}
