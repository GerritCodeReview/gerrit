// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.StopPluginListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.internal.UniqueAnnotations;
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

        bind(StopPluginListener.class)
          .annotatedWith(UniqueAnnotations.create())
          .to(FilterProxy.class);
      }
    };
  }

  @Singleton
  static class FilterProxy implements Filter, StopPluginListener {
    private final DynamicSet<AllRequestFilter> filters;

    private DynamicSet<AllRequestFilter> initializedFilters;
    private FilterConfig filterConfig;

    @Inject
    FilterProxy(DynamicSet<AllRequestFilter> filters) {
      this.filters = filters;
      this.initializedFilters = new DynamicSet<>();
      this.filterConfig = null;
    }

    private void initFilter(AllRequestFilter filter) throws ServletException {
      synchronized (initializedFilters) {
        // Since we're synchronized only now, we need to re-check if some
        // other thread already initialized the filter in the meantime.
        if (!initializedFilters.contains(filter)) {
          filter.init(filterConfig);
          initializedFilters.add(filter);
        }
      }
    }

    private void cleanUpInitializedFilters() {
      synchronized (initializedFilters) {
        DynamicSet<AllRequestFilter> cleaned = new DynamicSet<>();

        Iterator<AllRequestFilter> iterator = initializedFilters.iterator();
        while (iterator.hasNext()) {
          AllRequestFilter initializedFilter = iterator.next();
          if (filters.contains(initializedFilter)) {
            cleaned.add(initializedFilter);
          } else {
            initializedFilter.destroy();
          }
        }
        initializedFilters = cleaned;
      }
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
            AllRequestFilter filter = itr.next();
            if (!initializedFilters.contains(filter)) {
              initFilter(filter);
            }
            filter.doFilter(req, res, this);
          } else {
            last.doFilter(req, res);
          }
        }
      }.doFilter(req, res);
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
      // Plugins that provide AllRequestFilters might get loaded later at
      // runtime, long after this init method had been called. To allow to
      // correctly init such plugins' AllRequestFilters, we keep the
      // FilterConfig around, and reuse it to lazy init the AllRequestFilters.
      filterConfig = config;

      for (AllRequestFilter f: filters) {
        initFilter(f);
      }
    }

    @Override
    public void destroy() {
      for (AllRequestFilter f: initializedFilters) {
        f.destroy();
      }
    }

    @Override
    public void onStopPlugin(Plugin plugin) {
      // In order to allow properly garbage collection, we need to scrub
      // initializedFilters clean of filters stemming from plugins as they
      // get unloaded.
      cleanUpInitializedFilters();
    }
  }

  @Override
  public void init(FilterConfig config) throws ServletException {
  }

  @Override
  public void destroy() {
  }
}
