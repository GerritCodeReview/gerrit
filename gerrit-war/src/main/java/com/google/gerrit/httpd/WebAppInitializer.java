// Copyright (C) 2009 The Android Open Source Project
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

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.Stage.PRODUCTION;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.AuthConfigModule;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.config.GerritServerConfigModule;
import com.google.gerrit.server.config.MasterNodeStartup;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.schema.DatabaseModule;
import com.google.gerrit.server.schema.SchemaModule;
import com.google.gerrit.server.schema.SchemaVersionCheck;
import com.google.gerrit.sshd.SshModule;
import com.google.gerrit.sshd.commands.MasterCommandModule;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.name.Names;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.spi.Message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletContextEvent;
import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;

/** Configures the web application environment for Gerrit Code Review. */
public class WebAppInitializer extends GuiceServletContextListener {
  private static final Logger log =
      LoggerFactory.getLogger(WebAppInitializer.class);

  private File sitePath;
  private Injector dbInjector;
  private Injector cfgInjector;
  private Injector sysInjector;
  private Injector webInjector;
  private Injector sshInjector;
  private LifecycleManager manager;

  private synchronized void init() {
    if (manager == null) {
      final String path = System.getProperty("gerrit.site_path");
      if (path != null) {
        sitePath = new File(path);
      }

      try {
        dbInjector = createDbInjector();
      } catch (CreationException ce) {
        final Message first = ce.getErrorMessages().iterator().next();
        final StringBuilder buf = new StringBuilder();
        buf.append(first.getMessage());
        Throwable why = first.getCause();
        while (why != null) {
          buf.append("\n  caused by ");
          buf.append(why.toString());
          why = why.getCause();
        }
        if (first.getCause() != null) {
          buf.append("\n");
          buf.append("\nResolve above errors before continuing.");
          buf.append("\nComplete stack trace follows:");
        }
        log.error(buf.toString(), first.getCause());
        throw new CreationException(Collections.singleton(first));
      }

      cfgInjector = createCfgInjector();
      sysInjector = createSysInjector();
      sshInjector = createSshInjector();
      webInjector = createWebInjector();

      // Push the Provider<HttpServletRequest> down into the canonical
      // URL provider. Its optional for that provider, but since we can
      // supply one we should do so, in case the administrator has not
      // setup the canonical URL in the configuration file.
      //
      // Note we have to do this manually as Guice failed to do the
      // injection here because the HTTP environment is not visible
      // to the core server modules.
      //
      sysInjector.getInstance(HttpCanonicalWebUrlProvider.class)
          .setHttpServletRequest(
              webInjector.getProvider(HttpServletRequest.class));

      manager = new LifecycleManager();
      manager.add(dbInjector);
      manager.add(cfgInjector);
      manager.add(sysInjector);
      manager.add(sshInjector);
      manager.add(webInjector);
    }
  }

  private Injector createDbInjector() {
    final List<Module> modules = new ArrayList<Module>();
    if (sitePath != null) {
      modules.add(new LifecycleModule() {
        @Override
        protected void configure() {
          bind(File.class).annotatedWith(SitePath.class).toInstance(sitePath);
          bind(DataSourceProvider.Context.class).toInstance(
              DataSourceProvider.Context.MULTI_USER);
          bind(Key.get(DataSource.class, Names.named("ReviewDb"))).toProvider(
              DataSourceProvider.class).in(SINGLETON);
          listener().to(DataSourceProvider.class);
        }
      });
      modules.add(new GerritServerConfigModule());

    } else {
      modules.add(new LifecycleModule() {
        @Override
        protected void configure() {
          bind(Key.get(DataSource.class, Names.named("ReviewDb"))).toProvider(
              ReviewDbDataSourceProvider.class).in(SINGLETON);
          listener().to(ReviewDbDataSourceProvider.class);
        }
      });
    }
    modules.add(new DatabaseModule());
    return Guice.createInjector(PRODUCTION, modules);
  }

  private Injector createCfgInjector() {
    final List<Module> modules = new ArrayList<Module>();
    if (sitePath == null) {
      // If we didn't get the site path from the system property
      // we need to get it from the database, as that's our old
      // method of locating the site path on disk.
      //
      modules.add(new AbstractModule() {
        @Override
        protected void configure() {
          bind(File.class).annotatedWith(SitePath.class).toProvider(
              SitePathFromSystemConfigProvider.class).in(SINGLETON);
        }
      });
      modules.add(new GerritServerConfigModule());
    }
    modules.add(new SchemaModule());
    modules.add(SchemaVersionCheck.module());
    modules.add(new AuthConfigModule());
    return dbInjector.createChildInjector(modules);
  }

  private Injector createSysInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(cfgInjector.getInstance(GerritGlobalModule.class));
    modules.add(new CanonicalWebUrlModule() {
      @Override
      protected Class<? extends Provider<String>> provider() {
        return HttpCanonicalWebUrlProvider.class;
      }
    });
    modules.add(new MasterNodeStartup());
    return cfgInjector.createChildInjector(modules);
  }

  private Injector createSshInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new SshModule());
    modules.add(new MasterCommandModule());
    return sysInjector.createChildInjector(modules);
  }

  private Injector createWebInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(sshInjector.getInstance(WebModule.class));
    modules.add(sshInjector.getInstance(WebSshGlueModule.class));
    return sysInjector.createChildInjector(modules);
  }

  @Override
  protected Injector getInjector() {
    init();
    return webInjector;
  }

  @Override
  public void contextInitialized(final ServletContextEvent event) {
    super.contextInitialized(event);
    init();
    manager.start();
  }

  @Override
  public void contextDestroyed(final ServletContextEvent event) {
    if (manager != null) {
      manager.stop();
      manager = null;
    }
    super.contextDestroyed(event);
  }
}
