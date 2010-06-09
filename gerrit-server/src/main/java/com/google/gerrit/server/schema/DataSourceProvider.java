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

package com.google.gerrit.server.schema;

import static com.google.gerrit.server.config.ConfigUtil.getEnum;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.jdbc.Database;
import com.google.gwtorm.jdbc.SimpleDataSource;
import com.google.gwtorm.nosql.heap.FileDatabase;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import org.apache.commons.dbcp.BasicDataSource;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

/** Provides access to the DataSource. */
@Singleton
public final class DataSourceProvider implements
    Provider<SchemaFactory<ReviewDb>>, LifecycleListener {
  private static final Logger log =
      LoggerFactory.getLogger(DataSourceProvider.class);

  private final MyProvider ds;

  @Inject
  DataSourceProvider(final SitePaths site,
      @GerritServerConfig final Config cfg, Context ctx) {
    try {
      Type type = getEnum(cfg, "database", null, "type", Type.values(), null);
      if (type == DataSourceProvider.Type.NOSQL_HEAP_FILE) {
        ds = new NoSqlHeapFileProvider(site, cfg, ctx);

      } else {
        ds = new JdbcProvider(site, cfg, ctx);
      }
    } catch (OrmException e) {
      throw new ProvisionException("Cannot create ReviewDb", e);
    }
  }

  @Override
  public SchemaFactory<ReviewDb> get() {
    return ds.get();
  }

  @Override
  public void start() {
    ds.start();
  }

  @SuppressWarnings("unchecked")
  @Override
  public synchronized void stop() {
    ds.stop();
  }

  public static enum Context {
    SINGLE_USER, MULTI_USER;
  }

  public static enum Type {
    // Traditional SQL databases
    H2, POSTGRESQL, MYSQL, JDBC,

    // NoSQL types we also support
    NOSQL_HEAP_FILE;
  }

  private static String hostname(String hostname) {
    if (hostname == null || hostname.isEmpty()) {
      hostname = "localhost";

    } else if (hostname.contains(":") && !hostname.startsWith("[")) {
      hostname = "[" + hostname + "]";
    }
    return hostname;
  }

  private static String port(String port) {
    if (port != null && !port.isEmpty()) {
      return ":" + port;
    }
    return "";
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

  private abstract static class MyProvider implements
      Provider<SchemaFactory<ReviewDb>>, LifecycleListener {
  }

  private static class NoSqlHeapFileProvider extends MyProvider {
    private FileDatabase<ReviewDb> db;

    NoSqlHeapFileProvider(final SitePaths site, final Config cfg,
        final Context context) throws OrmException {
      String database = optional(cfg, "database");
      if (database == null || database.isEmpty()) {
        database = "db/ReviewDB";
      }
      File path = site.resolve(database);
      try {
        path = path.getCanonicalFile();
      } catch (IOException e) {
        path = path.getAbsoluteFile();
      }
      db = new FileDatabase<ReviewDb>(path, ReviewDb.class);
    }

    @Override
    public SchemaFactory<ReviewDb> get() {
      return db;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
      if (db != null) {
        try {
          db.close();
        } catch (OrmException err) {
          log.warn("Cannot safely close database", err);
        } finally {
          db = null;
        }
      }
    }
  }

  private static class JdbcProvider extends MyProvider {
    private Database<ReviewDb> db;
    private BasicDataSource dataSource;

    JdbcProvider(final SitePaths site, final Config cfg, final Context context)
        throws OrmException {
      Type type = getEnum(cfg, "database", null, "type", Type.values(), null);
      String driver = optional(cfg, "driver");
      String url = optional(cfg, "url");
      String username = optional(cfg, "username");
      String password = optional(cfg, "password");

      if (url == null || url.isEmpty()) {
        if (type == null) {
          type = Type.H2;
        }

        switch (type) {
          case H2: {
            String database = optional(cfg, "database");
            if (database == null || database.isEmpty()) {
              database = "db/ReviewDB";
            }
            File db = site.resolve(database);
            try {
              db = db.getCanonicalFile();
            } catch (IOException e) {
              db = db.getAbsoluteFile();
            }
            url = "jdbc:h2:" + db.toURI().toString();
            break;
          }

          case POSTGRESQL: {
            final StringBuilder b = new StringBuilder();
            b.append("jdbc:postgresql://");
            b.append(hostname(optional(cfg, "hostname")));
            b.append(port(optional(cfg, "port")));
            b.append("/");
            b.append(required(cfg, "database"));
            url = b.toString();
            break;
          }

          case MYSQL: {
            final StringBuilder b = new StringBuilder();
            b.append("jdbc:mysql://");
            b.append(hostname(optional(cfg, "hostname")));
            b.append(port(optional(cfg, "port")));
            b.append("/");
            b.append(required(cfg, "database"));
            url = b.toString();
            break;
          }

          case JDBC:
            driver = required(cfg, "driver");
            url = required(cfg, "url");
            break;

          default:
            throw new IllegalArgumentException(type + " not supported");
        }
      }

      if (driver == null || driver.isEmpty()) {
        if (url.startsWith("jdbc:h2:")) {
          driver = "org.h2.Driver";

        } else if (url.startsWith("jdbc:postgresql:")) {
          driver = "org.postgresql.Driver";

        } else if (url.startsWith("jdbc:mysql:")) {
          driver = "com.mysql.jdbc.Driver";

        } else {
          throw new IllegalArgumentException("database.driver must be set");
        }
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
        ds.setMaxWait(ConfigUtil.getTimeUnit(cfg, "database", null,
            "poolmaxwait", MILLISECONDS.convert(30, SECONDS), MILLISECONDS));
        ds.setInitialSize(ds.getMinIdle());
        dataSource = ds;
        db = new Database<ReviewDb>(ds, ReviewDb.class);

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
          db = new Database<ReviewDb>(new SimpleDataSource(p), ReviewDb.class);
        } catch (SQLException se) {
          throw new ProvisionException("Database unavailable", se);
        }
      }
    }

    @Override
    public SchemaFactory<ReviewDb> get() {
      return db;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
      if (dataSource != null) {
        try {
          dataSource.close();
        } catch (SQLException e) {
          // Ignore the close failure.
        } finally {
          dataSource = null;
          db = null;
        }
      }
    }
  }
}
