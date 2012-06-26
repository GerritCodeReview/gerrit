package com.google.gerrit.httpd;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

import java.io.IOException;
import java.util.Iterator;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/** Filters all HTTP requests passing through the server. */
public abstract class AllRequestFilter implements Filter {
  public static ServletModule module() {
    return new ServletModule() {
      @Override
      protected void configureServlets() {
        DynamicSet.setOf(binder(), AllRequestFilter.class);
        filter("/*").through(FilterProxy.class);
      }
    };
  }

  @Singleton
  static class FilterProxy implements Filter {
    private final DynamicSet<AllRequestFilter> filters;

    @Inject
    FilterProxy(DynamicSet<AllRequestFilter> filters) {
      this.filters = filters;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res,
        final FilterChain last) throws IOException, ServletException {
      final Iterator<AllRequestFilter> itr = filters.iterator();
      new FilterChain() {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res)
            throws IOException, ServletException {
          if (itr.hasNext()) {
            itr.next().doFilter(req, res, this);
          } else {
            last.doFilter(req, res);
          }
        }
      }.doFilter(req, res);
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
    }

    @Override
    public void destroy() {
    }
  }

  @Override
  public void init(FilterConfig config) throws ServletException {
  }

  @Override
  public void destroy() {
  }
}
