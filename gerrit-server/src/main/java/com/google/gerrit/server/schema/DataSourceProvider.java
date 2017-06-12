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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.persistence.DataSourceInterceptor;
import com.google.gerrit.metrics.CallbackMetric1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.config.ConfigSection;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.gwtorm.jdbc.SimpleDataSource;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.eclipse.jgit.lib.Config;

/** Provides access to the DataSource. */
@Singleton
public class DataSourceProvider implements Provider<DataSource>, LifecycleListener {
  private final Config cfg;
  private final MetricMaker metrics;
  private final Context ctx;
  private final DataSourceType dst;
  private final ThreadSettingsConfig threadSettingsConfig;
  private DataSource ds;

  @Inject
  protected DataSourceProvider(
      @GerritServerConfig Config cfg,
      MetricMaker metrics,
      ThreadSettingsConfig threadSettingsConfig,
      Context ctx,
      DataSourceType dst) {
    this.cfg = cfg;
    this.metrics = metrics;
    this.threadSettingsConfig = threadSettingsConfig;
    this.ctx = ctx;
    this.dst = dst;
  }

  @Override
  public synchronized DataSource get() {
    if (ds == null) {
      ds = open(cfg, ctx, dst);
    }
    return ds;
  }

  @Override
  public void start() {}

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

  public enum Context {
    SINGLE_USER,
    MULTI_USER
  }

  private DataSource open(final Config cfg, final Context context, final DataSourceType dst) {
    ConfigSection dbs = new ConfigSection(cfg, "database");
    String driver = dbs.optional("driver");
    if (Strings.isNullOrEmpty(driver)) {
      driver = dst.getDriver();
    }

    String url = dbs.optional("url");
    if (Strings.isNullOrEmpty(url)) {
      url = dst.getUrl();
    }

    String username = dbs.optional("username");
    String password = dbs.optional("password");
    String interceptor = dbs.optional("dataSourceInterceptorClass");

    boolean usePool;
    if (context == Context.SINGLE_USER) {
      usePool = false;
    } else {
      usePool = cfg.getBoolean("database", "connectionpool", dst.usePool());
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
      int poolLimit = threadSettingsConfig.getDatabasePoolLimit();
      ds.setMaxActive(poolLimit);
      ds.setMinIdle(cfg.getInt("database", "poolminidle", 4));
      ds.setMaxIdle(cfg.getInt("database", "poolmaxidle", Math.min(poolLimit, 16)));
      ds.setMaxWait(
          ConfigUtil.getTimeUnit(
              cfg,
              "database",
              null,
              "poolmaxwait",
              MILLISECONDS.convert(30, SECONDS),
              MILLISECONDS));
      ds.setInitialSize(ds.getMinIdle());
      exportPoolMetrics(ds);
      return intercept(interceptor, ds);
    }
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
      return intercept(interceptor, new SimpleDataSource(p));
    } catch (SQLException se) {
      throw new ProvisionException("Database unavailable", se);
    }
  }

  private void exportPoolMetrics(final BasicDataSource pool) {
    final CallbackMetric1<Boolean, Integer> cnt =
        metrics.newCallbackMetric(
            "sql/connection_pool/connections",
            Integer.class,
            new Description("SQL database connections").setGauge().setUnit("connections"),
            Field.ofBoolean("active"));
    metrics.newTrigger(
        cnt,
        new Runnable() {
          @Override
          public void run() {
            synchronized (pool) {
              cnt.set(true, pool.getNumActive());
              cnt.set(false, pool.getNumIdle());
            }
          }
        });
  }

  private DataSource intercept(String interceptor, DataSource ds) {
    if (interceptor == null) {
      return ds;
    }
    try {
      Constructor<?> c = Class.forName(interceptor).getConstructor();
      DataSourceInterceptor datasourceInterceptor = (DataSourceInterceptor) c.newInstance();
      return datasourceInterceptor.intercept("reviewDb", ds);
    } catch (ClassNotFoundException
        | SecurityException
        | NoSuchMethodException
        | IllegalArgumentException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException e) {
      throw new ProvisionException("Cannot intercept datasource", e);
    }
  }
}
