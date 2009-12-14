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

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gwtorm.jdbc.SimpleDataSource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import org.apache.commons.dbcp.BasicDataSource;
import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

/** Provides access to the DataSource. */
@Singleton
public final class DataSourceProvider implements Provider<DataSource>,
    LifecycleListener {
  private final DataSource ds;

  @Inject
  DataSourceProvider(@SitePath final File sitePath,
      @GerritServerConfig final Config cfg, Context ctx) {
    ds = open(sitePath, cfg, ctx);
  }

  @Override
  public synchronized DataSource get() {
    return ds;
  }

  @Override
  public void start() {
  }

  @Override
  public synchronized void stop() {
    if (ds instanceof BasicDataSource) {
      try {
        ((BasicDataSource) ds).close();
      } catch (SQLException e) {
        // Ignore the close failure.
      }
    }
  }

  public static enum Context {
    SINGLE_USER, MULTI_USER;
  }

  public static enum Type {
    DEFAULT, JDBC, POSTGRES, POSTGRESQL, H2, MYSQL;
  }

  private DataSource open(final File sitePath, final Config cfg,
      final Context context) {
    Type type = ConfigUtil.getEnum(cfg, "database", null, "type", Type.DEFAULT);
    String driver = optional(cfg, "driver");
    String url = optional(cfg, "url");
    String username = optional(cfg, "username");
    String password = optional(cfg, "password");
    String hostname = optional(cfg, "hostname");
    String port = optional(cfg, "port");
    if (hostname == null) {
      hostname = "localhost";
    }

    if (Type.DEFAULT == type && (driver == null || driver.isEmpty())) {
      if (url != null && url.isEmpty()) {

        if (url.startsWith("jdbc:postgresql:")) {
          type = Type.POSTGRES;
        } else if (url.startsWith("postgresql:")) {
          url = "jdbc:" + url;
          type = Type.POSTGRES;
        } else if (url.startsWith("postgres:")) {
          url = "jdbc:postgresql:" + url.substring(url.indexOf(':') + 1);
          type = Type.POSTGRES;

        } else if (url.startsWith("jdbc:h2:")) {
          type = Type.H2;
        } else if (url.startsWith("h2:")) {
          url = "jdbc:" + url;
          type = Type.H2;

        } else if (url.startsWith("jdbc:mysql:")) {
          type = Type.MYSQL;
        } else if (url.startsWith("mysql:")) {
          url = "jdbc:" + url;
          type = Type.MYSQL;

        }

      } else if (url == null || url.isEmpty()) {
        type = Type.H2;
      }
    }

    switch (type) {
      case POSTGRES:
      case POSTGRESQL: {
        final String pfx = "jdbc:postgresql://";
        driver = "org.postgresql.Driver";
        if (url == null) {
          final StringBuilder b = new StringBuilder();
          b.append(pfx);
          b.append(hostname);
          if (port != null && !port.isEmpty()) {
            b.append(":");
            b.append(port);
          }
          b.append("/");
          b.append(required(cfg, "database"));
          url = b.toString();
        }
        if (url == null || !url.startsWith(pfx)) {
          throw new IllegalArgumentException("database.url must be " + pfx
              + " and not " + url);
        }
        break;
      }

      case H2: {
        final String pfx = "jdbc:h2:";
        driver = "org.h2.Driver";
        if (url == null) {
          String database = optional(cfg, "database");
          if (database == null || database.isEmpty()) {
            database = "db/ReviewDB";
          }

          File db = new File(database);
          if (!db.isAbsolute()) {
            db = new File(sitePath, database);
            try {
              db = db.getCanonicalFile();
            } catch (IOException e) {
              db = db.getAbsoluteFile();
            }
          }
          url = pfx + db.toURI().toString();
        }
        if (url == null || !url.startsWith(pfx)) {
          throw new IllegalArgumentException("database.url must be " + pfx
              + " and not " + url);
        }
        break;
      }

      case MYSQL: {
        final String pfx = "jdbc:mysql://";
        driver = "com.mysql.jdbc.Driver";
        if (url == null) {
          final StringBuilder b = new StringBuilder();
          b.append(pfx);
          b.append(hostname);
          if (port != null && !port.isEmpty()) {
            b.append(":");
            b.append(port);
          }
          b.append("/");
          b.append(required(cfg, "database"));
          url = b.toString();
        }
        if (url == null || !url.startsWith(pfx)) {
          throw new IllegalArgumentException("database.url must be " + pfx
              + " and not " + url);
        }
        break;
      }

      case DEFAULT:
      case JDBC:
      default:
        driver = required(cfg, "driver");
        url = required(cfg, "url");
        if (!url.startsWith("jdbc:")) {
          throw new IllegalArgumentException("database.url must be jdbc: style");
        }
        break;
    }

    boolean usePool;
    if (url.startsWith("jdbc:mysql:")) {
      // MySQL has given us trouble with the connection pool,
      // sometimes the backend disconnects and the pool winds
      // up with a stale connection. Fortunately opening up
      // a new MySQL connection is usually very fast.
      //
      usePool = false;
    } else {
      usePool = true;
    }
    usePool = cfg.getBoolean("database", "connectionpool", usePool);
    if (context == Context.SINGLE_USER) {
      usePool = false;
    }

    if (usePool) {
      final BasicDataSource ds = new BasicDataSource();
      ds.setDriverClassName(driver);
      ds.setUrl(url);
      if (username != null && !username.isEmpty()) {
        ds.setUsername(username);
      }
      if (password != null && !password.isEmpty()) {
        ds.setPassword(password);
      }
      ds.setMaxActive(cfg.getInt("database", "poollimit", 8));
      ds.setMinIdle(cfg.getInt("database", "poolminidle", 4));
      ds.setMaxIdle(cfg.getInt("database", "poolmaxidle", 4));
      ds.setMaxWait(cfg.getInt("database", "poolmaxwait", 30000));
      ds.setInitialSize(ds.getMinIdle());
      return ds;

    } else {
      // Don't use the connection pool.
      //
      try {
        final Properties p = new Properties();
        p.setProperty("driver", driver);
        p.setProperty("url", url);
        if (username != null) {
          p.setProperty("user", username);
        }
        if (password != null) {
          p.setProperty("password", password);
        }
        return new SimpleDataSource(p);
      } catch (SQLException se) {
        throw new ProvisionException("Database unavailable", se);
      }
    }
  }

  private static String optional(final Config config, final String name) {
    return config.getString("database", null, name);
  }

  private static String required(final Config config, final String name) {
    final String v = optional(config, name);
    if (v == null || "".equals(v)) {
      throw new IllegalArgumentException("No database." + name + " configured");
    }
    return v;
  }
}
