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

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfigModule;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.schema.DatabaseModule;
import com.google.gerrit.server.schema.SchemaModule;
import com.google.gwtorm.client.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.google.inject.spi.Message;

import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileFilter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

public abstract class SiteProgram extends AbstractProgram {
  @Option(name = "--site-path", aliases = {"-d"}, usage = "Local directory containing site data")
  private File sitePath = new File(".");

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

  /** Load extra JARs from {@code lib/} subdirectory of {@link #getSitePath()} */
  protected void loadSiteLib() {
    final File libdir = new File(getSitePath(), "lib");
    final File[] list = libdir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File path) {
        if (!path.isFile()) {
          return false;
        }
        return path.getName().endsWith(".jar") //
            || path.getName().endsWith(".zip");
      }
    });
    if (list != null && 0 < list.length) {
      Arrays.sort(list, new Comparator<File>() {
        @Override
        public int compare(File a, File b) {
          return a.getName().compareTo(b.getName());
        }
      });
      addToClassLoader(list);
    }
  }

  private void addToClassLoader(final File[] additionalLocations) {
    final ClassLoader cl = getClass().getClassLoader();
    if (!(cl instanceof URLClassLoader)) {
      throw noAddURL("Not loaded by URLClassLoader", null);
    }

    final URLClassLoader ucl = (URLClassLoader) cl;
    final Set<URL> have = new HashSet<URL>();
    have.addAll(Arrays.asList(ucl.getURLs()));

    final Method m;
    try {
      m = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
      m.setAccessible(true);
    } catch (SecurityException e) {
      throw noAddURL("Method addURL not available", e);
    } catch (NoSuchMethodException e) {
      throw noAddURL("Method addURL not available", e);
    }

    for (final File path : additionalLocations) {
      try {
        final URL url = path.toURI().toURL();
        if (have.add(url)) {
          m.invoke(cl, url);
        }
      } catch (MalformedURLException e) {
        throw noAddURL("addURL " + path + " failed", e);
      } catch (IllegalArgumentException e) {
        throw noAddURL("addURL " + path + " failed", e);
      } catch (IllegalAccessException e) {
        throw noAddURL("addURL " + path + " failed", e);
      } catch (InvocationTargetException e) {
        throw noAddURL("addURL " + path + " failed", e.getCause());
      }
    }
  }

  private static UnsupportedOperationException noAddURL(String m, Throwable why) {
    final String prefix = "Cannot extend classpath: ";
    return new UnsupportedOperationException(prefix + m, why);
  }

  /** @return provides database connectivity and site path. */
  protected Injector createDbInjector(final DataSourceProvider.Context context) {
    loadSiteLib();

    final File sitePath = getSitePath();
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(File.class).annotatedWith(SitePath.class).toInstance(sitePath);
      }
    });
    modules.add(new LifecycleModule() {
      @Override
      protected void configure() {
        bind(DataSourceProvider.Context.class).toInstance(context);
        bind(Key.get(DataSource.class, Names.named("ReviewDb"))).toProvider(
            DataSourceProvider.class).in(SINGLETON);
        listener().to(DataSourceProvider.class);
      }
    });
    modules.add(new GerritServerConfigModule());
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

  @SuppressWarnings("deprecation")
  private static boolean isCannotCreatePoolException(Throwable why) {
    return why instanceof org.apache.commons.dbcp.SQLNestedException
        && why.getCause() != null
        && why.getMessage().startsWith(
            "Cannot create PoolableConnectionFactory");
  }
}
