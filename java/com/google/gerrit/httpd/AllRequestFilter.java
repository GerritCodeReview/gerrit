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
import com.google.gerrit.server.plugins.StopPluginListener;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.servlet.ServletModule;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;

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
  static class FilterProxy extends FilterProxyType<AllRequestFilter> {
    @Inject
    FilterProxy(DynamicSet<AllRequestFilter> filters) {
      super(filters);
    }
  }

  @Override
  public void init(FilterConfig config) throws ServletException {}

  @Override
  public void destroy() {}
}
