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
      for (AllRequestFilter f: filters) {
        f.init(config);
      }
    }

    @Override
    public void destroy() {
      for (AllRequestFilter f: filters) {
        f.destroy();
      }
    }
  }

  @Override
  public void init(FilterConfig config) throws ServletException {
  }

  @Override
  public void destroy() {
  }
}
