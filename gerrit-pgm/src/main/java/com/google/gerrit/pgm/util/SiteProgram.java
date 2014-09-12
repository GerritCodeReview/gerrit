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

package com.google.gerrit.pgm.util;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.Stage.PRODUCTION;

import com.google.common.collect.Lists;
import com.google.gerrit.common.Die;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GerritServerConfigModule;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.schema.DataSourceModule;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.schema.DataSourceType;
import com.google.gerrit.server.schema.DatabaseModule;
import com.google.gerrit.server.schema.SchemaModule;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.securestore.SecureStoreProvider;
import com.google.gwtorm.server.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Message;

import org.eclipse.jgit.lib.Config;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.lang.annotation.Annotation;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

public abstract class SiteProgram extends AbstractProgram {
  @Option(name = "--site-path", aliases = {"-d"}, usage = "Local directory containing site data")
  private File sitePath = new File(".");

  protected Provider<DataSource> dsProvider;

  protected SiteProgram() {
  }

  protected SiteProgram(File sitePath, final Provider<DataSource> dsProvider) {
    this.sitePath = sitePath;
    this.dsProvider = dsProvider;
  }

  /** @return the site path specified on the command line. */
  protected File getSitePath() {
    File path = sitePath.getAbsoluteFile();
    if (".".equals(path.getName())) {
      path = path.getParentFile();
    }
    return path;
  }

  /** Ensures we are running inside of a valid site, otherwise throws a Die. */
  protected void mustHaveValidSite() throws Die {
    if (!new File(new File(getSitePath(), "etc"), "gerrit.config").exists()) {
      throw die("not a Gerrit site: '" + getSitePath() + "'\n"
          + "Perhaps you need to run init first?");
    }
  }

  /** @return provides database connectivity and site path. */
  protected Injector createDbInjector(final DataSourceProvider.Context context) {
    final File sitePath = getSitePath();
    final List<Module> modules = new ArrayList<>();

    Module sitePathModule = new AbstractModule() {
      @Override
      protected void configure() {
        bind(File.class).annotatedWith(SitePath.class).toInstance(sitePath);
        bind(SecureStore.class).toProvider(SecureStoreProvider.class);
      }
    };
    modules.add(sitePathModule);

    modules.add(new LifecycleModule() {
      @Override
      protected void configure() {
        bind(DataSourceProvider.Context.class).toInstance(context);
        if (dsProvider != null) {
          bind(Key.get(DataSource.class, Names.named("ReviewDb")))
            .toProvider(dsProvider)
            .in(SINGLETON);
          if (LifecycleListener.class.isAssignableFrom(dsProvider.getClass())) {
            listener().toInstance((LifecycleListener) dsProvider);
          }
        } else {
          bind(Key.get(DataSource.class, Names.named("ReviewDb")))
            .toProvider(SiteLibraryBasedDataSourceProvider.class)
            .in(SINGLETON);
          listener().to(SiteLibraryBasedDataSourceProvider.class);
        }
      }
    });
    Module configModule = new GerritServerConfigModule();
    modules.add(configModule);
    Injector cfgInjector = Guice.createInjector(sitePathModule, configModule);
    Config cfg = cfgInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    String dbType;
    if (dsProvider != null) {
      dbType = getDbType(dsProvider);
    } else {
      dbType = cfg.getString("database", null, "type");
    }

    final DataSourceType dst = Guice.createInjector(new DataSourceModule(), configModule,
            sitePathModule).getInstance(
            Key.get(DataSourceType.class, Names.named(dbType.toLowerCase())));

    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(DataSourceType.class).toInstance(dst);
      }});
    modules.add(new DatabaseModule());
    modules.add(new SchemaModule());
    modules.add(new LocalDiskRepositoryManager.Module());

    try {
      return Guice.createInjector(PRODUCTION, modules);
    } catch (CreationException ce) {
      final Message first = ce.getErrorMessages().iterator().next();
      Throwable why = first.getCause();

      if (why instanceof SQLException) {
        throw die("Cannot connect to SQL database", why);
      }
      if (why instanceof OrmException && why.getCause() != null
          && "Unable to determine driver URL".equals(why.getMessage())) {
        why = why.getCause();
        if (isCannotCreatePoolException(why)) {
          throw die("Cannot connect to SQL database", why.getCause());
        }
        throw die("Cannot connect to SQL database", why);
      }

      final StringBuilder buf = new StringBuilder();
      if (why != null) {
        buf.append(why.getMessage());
        why = why.getCause();
      } else {
        buf.append(first.getMessage());
      }
      while (why != null) {
        buf.append("\n  caused by ");
        buf.append(why.toString());
        why = why.getCause();
      }
      throw die(buf.toString(), new RuntimeException("DbInjector failed", ce));
    }
  }

  private String getDbType(Provider<DataSource> dsProvider) {
    String dbProductName;
    try {
      Connection conn = dsProvider.get().getConnection();
      try {
        dbProductName = conn.getMetaData().getDatabaseProductName().toLowerCase();
      } finally {
        conn.close();
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    List<Module> modules = Lists.newArrayList();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(File.class).annotatedWith(SitePath.class).toInstance(sitePath);
      }
    });
    modules.add(new GerritServerConfigModule());
    modules.add(new DataSourceModule());
    Injector i = Guice.createInjector(modules);
    List<Binding<DataSourceType>> dsTypeBindings =
        i.findBindingsByType(new TypeLiteral<DataSourceType>() {});
    for (Binding<DataSourceType> binding : dsTypeBindings) {
      Annotation annotation = binding.getKey().getAnnotation();
      if (annotation instanceof Named) {
        if (((Named) annotation).value().toLowerCase().contains(dbProductName)) {
          return ((Named) annotation).value();
        }
      }
    }
    throw new IllegalStateException(String.format(
        "Cannot guess database type from the database product name '%s'",
        dbProductName));
  }

  @SuppressWarnings("deprecation")
  private static boolean isCannotCreatePoolException(Throwable why) {
    return why instanceof org.apache.commons.dbcp.SQLNestedException
        && why.getCause() != null
        && why.getMessage().startsWith(
            "Cannot create PoolableConnectionFactory");
  }
}
