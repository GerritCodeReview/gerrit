package com.google.gerrit.pgm.http.jetty;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
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
import org.eclipse.jgit.lib.Config;

@Singleton
public class SameSiteFilter implements Filter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String sameSite;

  @Inject
  SameSiteFilter(@GerritServerConfig Config cfg) {
    this.sameSite = cfg.getString("httpd", null, "sameSite");
  }

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletResponse rsp = (HttpServletResponse) response;
    if (sameSite == null) {
      chain.doFilter(request, response);
      return;
    }
    String sameSiteComment =
        switch (sameSite.toLowerCase()) {
          case "lax" -> HttpCookie.SAME_SITE_LAX_COMMENT;
          case "strict" -> HttpCookie.SAME_SITE_STRICT_COMMENT;
          case "none" -> HttpCookie.SAME_SITE_NONE_COMMENT;
          default ->
              throw new ServletException(String.format("Invalid sameSite value: %s", sameSite));
        };
    chain.doFilter(
        request,
        new HttpServletResponseWrapper(rsp) {
          @Override
          public void addCookie(Cookie cookie) {
            logger.atFine().log("Setting SameSite attribute on: %s", cookie.getName());
            cookie.setComment(sameSiteComment);
            super.addCookie(cookie);
          }
        });
  }

  @Override
  public void destroy() {}
}
