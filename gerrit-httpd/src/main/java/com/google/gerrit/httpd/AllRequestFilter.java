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

    /**
     * Initializes a filter if needed
     *
     * @param filter The filter that should get initialized
     * @return {@code true} iff filter is now initialized
     * @throws ServletException if filter itself fails to init
     */
    private synchronized boolean initFilterIfNeeded(AllRequestFilter filter)
        throws ServletException {
      boolean ret = true;
      if (filters.contains(filter)) {
        // Regardless of whether or not the caller checked filter's
        // containment in initializedFilters, we better re-check as we're now
        // synchronized.
        if (!initializedFilters.contains(filter)) {
          filter.init(filterConfig);
          initializedFilters.add(filter);
        }
      } else {
        ret = false;
      }
      return ret;
    }

    private synchronized void cleanUpInitializedFilters() {
      Iterable<AllRequestFilter> filtersToCleanUp = initializedFilters;
      initializedFilters = new DynamicSet<>();
      for (AllRequestFilter filter : filtersToCleanUp) {
        if (filters.contains(filter)) {
          initializedFilters.add(filter);
        } else {
          filter.destroy();
        }
      }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, final FilterChain last)
        throws IOException, ServletException {
      final Iterator<AllRequestFilter> itr = filters.iterator();
      new FilterChain() {
        @Override
        public void doFilter(ServletRequest req, ServletResponse res)
            throws IOException, ServletException {
          while (itr.hasNext()) {
            AllRequestFilter filter = itr.next();
            // To avoid {@code synchronized} on the the whole filtering (and
            // thereby killing concurrency), we start the below disjunction
            // with an unsynchronized check for containment. This
            // unsynchronized check is always correct if no filters got
            // initialized/cleaned concurrently behind our back.
            // The case of concurrently initialized filters is saved by the
            // call to initFilterIfNeeded. So that's fine too.
            // The case of concurrently cleaned filters between the {@code if}
            // condition and the call to {@code doFilter} is not saved by
            // anything. If a filter is getting removed concurrently while
            // another thread is in those two lines, doFilter might (but need
            // not) fail.
            //
            // Since this failure only occurs if a filter is deleted
            // (e.g.: a plugin reloaded) exactly when a thread is in those
            // two lines, and it only breaks a single request, we're ok with
            // it, given that this is really both really improbable and also
            // the "proper" fix for it would basically kill concurrency of
            // webrequests.
            if (initializedFilters.contains(filter) || initFilterIfNeeded(filter)) {
              filter.doFilter(req, res, this);
              return;
            }
          }
          last.doFilter(req, res);
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

      for (AllRequestFilter f : filters) {
        initFilterIfNeeded(f);
      }
    }

    @Override
    public synchronized void destroy() {
      Iterable<AllRequestFilter> filtersToDestroy = initializedFilters;
      initializedFilters = new DynamicSet<>();
      for (AllRequestFilter filter : filtersToDestroy) {
        filter.destroy();
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
  public void init(FilterConfig config) throws ServletException {}

  @Override
  public void destroy() {}
}
