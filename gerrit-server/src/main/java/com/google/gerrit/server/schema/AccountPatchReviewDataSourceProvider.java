// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.eclipse.jgit.lib.Config;

/** Provides access to the AccountPatchReview DataSource. */
@Singleton
public class AccountPatchReviewDataSourceProvider
    implements Provider<DataSource>, LifecycleListener {
  private static final String ACCOUNT_PATCH_REVIEW_DB = "accountPatchReviewDb";
  private final Config cfg;
  private DataSource ds;
  private int poolLimit;
  private String url;

  @Inject
  protected AccountPatchReviewDataSourceProvider(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      ThreadSettingsConfig threadSettingsConfig) {
    this.cfg = cfg;
    this.poolLimit = threadSettingsConfig.getDatabasePoolLimit();
    this.url = cfg.getString(ACCOUNT_PATCH_REVIEW_DB, null, "url");
    if (url == null) {
      url = H2.createUrl(sitePaths.db_dir.resolve("account_patch_reviews"));
    }
  }

  @Override
  public synchronized DataSource get() {
    if (ds == null) {
      ds = open(cfg);
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

  private DataSource open(Config cfg) {
    BasicDataSource datasource = new BasicDataSource();
    datasource.setUrl(url);
    datasource.setDriverClassName(getDriverFromUrl(url));
    datasource.setMaxActive(cfg.getInt(ACCOUNT_PATCH_REVIEW_DB, "poolLimit", poolLimit));
    datasource.setMinIdle(cfg.getInt(ACCOUNT_PATCH_REVIEW_DB, "poolminidle", 4));
    datasource.setMaxIdle(
        cfg.getInt(ACCOUNT_PATCH_REVIEW_DB, "poolmaxidle", Math.min(poolLimit, 16)));
    datasource.setInitialSize(datasource.getMinIdle());
    datasource.setMaxWait(
        ConfigUtil.getTimeUnit(
            cfg,
            ACCOUNT_PATCH_REVIEW_DB,
            null,
            "poolmaxwait",
            MILLISECONDS.convert(30, SECONDS),
            MILLISECONDS));
    long evictIdleTimeMs = 1000L * 60;
    datasource.setMinEvictableIdleTimeMillis(evictIdleTimeMs);
    datasource.setTimeBetweenEvictionRunsMillis(evictIdleTimeMs / 2);
    return datasource;
  }

  private String getDriverFromUrl(String url) {
    if (url.contains("postgresql")) {
      return "org.postgresql.Driver";
    }
    if (url.contains("mysql")) {
      return "com.mysql.jdbc.Driver";
    }
    if (url.contains("mariadb")) {
      return "org.mariadb.jdbc.Driver";
    }
    return "org.h2.Driver";
  }
}
