// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.vhost;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.Scopes.SINGLETON;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.httpd.HttpCanonicalWebUrlProvider;
import com.google.gerrit.httpd.RequestContextFilter;
import com.google.gerrit.httpd.WebModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.util.SiteLibraryBasedDataSourceProvider;
import com.google.gerrit.server.account.InternalAccountDirectory;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.schema.DataSourceModule;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.schema.DatabaseModule;
import com.google.gerrit.server.schema.SchemaModule;
import com.google.gerrit.server.ssh.NoSshInfo;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.ServletModule;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

/** An active Gerrit Code Review site in a virtual hosted server. */
public class RunningSite {
  private final LifecycleManager manager;
  private final GuiceFilter filter;
  private final Injector sysInjector;
  private final Injector webInjector;
  private boolean stopped;
  private volatile boolean filterInitDone;

  @Singleton
  static class Globals {
    private final GlobalDataModule globalModule;

    @Inject
    Globals(GlobalDataModule globalModule) {
      this.globalModule = globalModule;
    }
  }

  static RunningSite create(final Globals cfg, String siteName) {
    checkArgument(!Strings.isNullOrEmpty(siteName), "siteName");

    Injector cfgInjector = Guice.createInjector(
        cfg.globalModule,
        new VirtualHostedConfigModule(siteName));

    Injector sysInjector = cfgInjector.createChildInjector(
        cfgInjector.getInstance(GerritGlobalModule.class),
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(DataSourceProvider.Context.class)
              .toInstance(DataSourceProvider.Context.MULTI_USER);
            bind(Key.get(DataSource.class, Names.named("ReviewDb")))
              .toProvider(SiteLibraryBasedDataSourceProvider.class)
              .in(SINGLETON);
            DynamicSet.bind(binder(), LifecycleListener.class)
              .to(SiteLibraryBasedDataSourceProvider.class);
          }
        },
        new DataSourceModule(),
        new DatabaseModule(),
        new SchemaModule(),
        new LuceneIndexModule(cfg.globalModule.getIndexExecutor()),
        new InternalAccountDirectory.Module(),
        new SiteCacheFactory.Module());

    Injector webInjector = sysInjector.createChildInjector(
        RequestContextFilter.module(),
        cfgInjector.getInstance(WebModule.class),
        new ServletModule() {
          @Override
          protected void configureServlets() {
            serve("/plugins/").with(NoPluginsServlet.class);
          }
        },
        new AbstractModule() {
          @Override
          protected void configure() {
          }

          @Provides
          SshInfo getSshInfo() {
            return new NoSshInfo();
          }
        });

    // Glue the HttpServletRequest down into the URL provider. They
    // are currently in different injectors so we have to manually
    // wire up this dependency.
    webInjector.getInstance(HttpCanonicalWebUrlProvider.class)
      .setHttpServletRequest(webInjector.getProvider(HttpServletRequest.class));

    GuiceFilter filter = webInjector.getInstance(GuiceFilter.class);
    LifecycleManager manager = new LifecycleManager();
    manager.add(cfgInjector, sysInjector, webInjector);
    manager.start();
    return new RunningSite(manager, filter, sysInjector, webInjector);
  }

  private RunningSite(LifecycleManager manager, GuiceFilter filter,
      Injector sysInjector, Injector webInjector) {
    this.manager = checkNotNull(manager);
    this.filter = checkNotNull(filter);
    this.sysInjector = checkNotNull(sysInjector);
    this.webInjector = checkNotNull(webInjector);
  }

  public Injector getSysInjector() {
    return sysInjector;
  }

  public Injector getWebInjector() {
    return webInjector;
  }

  void dispatch(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    filter.doFilter(req, res, chain);
  }

  void initOnFirstRequest(final ServletContext context)
      throws ServletException {
    if (filterInitDone) {
      return;
    }

    synchronized (this) {
      if (filterInitDone) {
        return;
      }

      filter.init(new FilterConfig() {
        @Override
        public String getFilterName() {
          return GuiceFilter.class.getName();
        }

        @Override
        public String getInitParameter(String name) {
          return null;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public Enumeration getInitParameterNames() {
          return Collections.enumeration(Collections.emptyList());
        }

        @Override
        public ServletContext getServletContext() {
          return context;
        }
      });
      filterInitDone = true;
    }
  }

  synchronized void stop() {
    if (!stopped) {
      stopped = true;
      if (filterInitDone) {
        filter.destroy();
      }
      manager.stop();
    }
  }
}
