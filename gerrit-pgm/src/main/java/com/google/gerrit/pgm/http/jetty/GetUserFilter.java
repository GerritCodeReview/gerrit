// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.gerrit.pgm.http.jetty;

import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

import org.eclipse.jgit.lib.Config;

import java.io.IOException;
import java.net.URI;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Stores as a request attribute, so the {@link HttpLog} can include the the
 * user for the request outside of the request scope.
 *
 * @author cranger@google.com (Colby Ranger)
 */
@Singleton
public class GetUserFilter implements Filter {

  static final String REQ_ATTR_KEY = CurrentUser.class.toString();

  public static class Module extends ServletModule {

    private boolean loggingEnabled;

    @Inject
    Module(@GerritServerConfig final Config cfg) {
      URI[] urls = JettyServer.listenURLs(cfg);
      boolean reverseProxy = JettyServer.isReverseProxied(urls);
      this.loggingEnabled = cfg.getBoolean("httpd", "requestlog", !reverseProxy);
    }

    @Override
    protected void configureServlets() {
      if (loggingEnabled) {
        filter("/*").through(GetUserFilter.class);
      }
    }
  }

  private final Provider<CurrentUser> userProvider;

  @Inject
  GetUserFilter(final Provider<CurrentUser> userProvider) {
    this.userProvider = userProvider;
  }

  @Override
  public void doFilter(
      ServletRequest req, ServletResponse resp, FilterChain chain)
      throws IOException, ServletException {
    req.setAttribute(REQ_ATTR_KEY, userProvider.get());
    chain.doFilter(req, resp);
  }

  @Override
  public void destroy() {
  }

  @Override
  public void init(FilterConfig arg0) {
  }
}
