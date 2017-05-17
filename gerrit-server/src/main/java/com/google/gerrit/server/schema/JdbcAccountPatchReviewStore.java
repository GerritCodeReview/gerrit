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

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtorm.server.OrmDuplicateKeyException;
import com.google.gwtorm.server.OrmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Optional;
import javax.sql.DataSource;
import org.apache.commons.dbcp.BasicDataSource;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class JdbcAccountPatchReviewStore
    implements AccountPatchReviewStore, LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(JdbcAccountPatchReviewStore.class);

  public static class Module extends LifecycleModule {
    private final Config cfg;

    public Module(Config cfg) {
      this.cfg = cfg;
    }

    @Override
    protected void configure() {
      String url = cfg.getString("accountPatchReviewDb", null, "url");
      if (url == null || url.contains("h2")) {
        DynamicItem.bind(binder(), AccountPatchReviewStore.class)
            .to(H2AccountPatchReviewStore.class);
        listener().to(H2AccountPatchReviewStore.class);
      } else if (url.contains("postgresql")) {
        DynamicItem.bind(binder(), AccountPatchReviewStore.class)
            .to(PostgresqlAccountPatchReviewStore.class);
        listener().to(PostgresqlAccountPatchReviewStore.class);
      } else if (url.contains("mysql")) {
        DynamicItem.bind(binder(), AccountPatchReviewStore.class)
            .to(MysqlAccountPatchReviewStore.class);
        listener().to(MysqlAccountPatchReviewStore.class);
      } else if (url.contains("mariadb")) {
        DynamicItem.bind(binder(), AccountPatchReviewStore.class)
            .to(MariaDBAccountPatchReviewStore.class);
        listener().to(MariaDBAccountPatchReviewStore.class);
      } else {
        throw new IllegalArgumentException(
            "unsupported driver type for account patch reviews db: " + url);
      }
    }
  }

  private final DataSource ds;

  public static JdbcAccountPatchReviewStore createAccountPatchReviewStore(
      Config cfg, SitePaths sitePaths) {
    String url = cfg.getString("accountPatchReviewDb", null, "url");
    if (url == null || url.contains("h2")) {
      return new H2AccountPatchReviewStore(cfg, sitePaths);
    } else if (url.contains("postgresql")) {
      return new PostgresqlAccountPatchReviewStore(cfg, sitePaths);
    } else if (url.contains("mysql")) {
      return new MysqlAccountPatchReviewStore(cfg, sitePaths);
    } else if (url.contains("mariadb")) {
      return new MariaDBAccountPatchReviewStore(cfg, sitePaths);
    } else {
      throw new IllegalArgumentException(
          "unsupported driver type for account patch reviews db: " + url);
    }
  }

  protected JdbcAccountPatchReviewStore(Config cfg, SitePaths sitePaths) {
    this.ds = createDataSource(getUrl(cfg, sitePaths));
  }

  protected JdbcAccountPatchReviewStore(DataSource ds) {
    this.ds = ds;
  }

  private static String getUrl(@GerritServerConfig Config cfg, SitePaths sitePaths) {
    String url = cfg.getString("accountPatchReviewDb", null, "url");
    if (url == null) {
      return H2.createUrl(sitePaths.db_dir.resolve("account_patch_reviews"));
    }
    return url;
  }

  protected static DataSource createDataSource(String url) {
    BasicDataSource datasource = new BasicDataSource();
    if (url.contains("postgresql")) {
      datasource.setDriverClassName("org.postgresql.Driver");
    } else if (url.contains("h2")) {
      datasource.setDriverClassName("org.h2.Driver");
    } else if (url.contains("mysql")) {
      datasource.setDriverClassName("com.mysql.cj.jdbc.Driver");
    } else if (url.contains("mariadb")) {
      datasource.setDriverClassName("org.mariadb.jdbc.Driver");
    }
    datasource.setUrl(url);
    datasource.setMaxActive(50);
    datasource.setMinIdle(4);
    datasource.setMaxIdle(16);
    long evictIdleTimeMs = 1000 * 60;
    datasource.setMinEvictableIdleTimeMillis(evictIdleTimeMs);
    datasource.setTimeBetweenEvictionRunsMillis(evictIdleTimeMs / 2);
    return datasource;
  }

  @Override
  public void start() {
    try {
      createTableIfNotExists();
    } catch (OrmException e) {
      log.error("Failed to create table to store account patch reviews", e);
    }
  }

  public Connection getConnection() throws SQLException {
    return ds.getConnection();
  }

  public void createTableIfNotExists() throws OrmException {
    try (Connection con = ds.getConnection();
        Statement stmt = con.createStatement()) {
      doCreateTable(stmt);
    } catch (SQLException e) {
      throw convertError("create", e);
    }
  }

  private static void doCreateTable(Statement stmt) throws SQLException {
    stmt.executeUpdate(
        "CREATE TABLE IF NOT EXISTS account_patch_reviews ("
            + "account_id INTEGER DEFAULT 0 NOT NULL, "
            + "change_id INTEGER DEFAULT 0 NOT NULL, "
            + "patch_set_id INTEGER DEFAULT 0 NOT NULL, "
            + "file_name VARCHAR(4096) DEFAULT '' NOT NULL, "
            + "CONSTRAINT primary_key_account_patch_reviews "
            + "PRIMARY KEY (account_id, change_id, patch_set_id, file_name)"
            + ")");
  }

  public void dropTableIfExists() throws OrmException {
    try (Connection con = ds.getConnection();
        Statement stmt = con.createStatement()) {
      stmt.executeUpdate("DROP TABLE IF EXISTS account_patch_reviews");
    } catch (SQLException e) {
      throw convertError("create", e);
    }
  }

  @Override
  public void stop() {}

  @Override
  public boolean markReviewed(PatchSet.Id psId, Account.Id accountId, String path)
      throws OrmException {
    try (Connection con = ds.getConnection();
        PreparedStatement stmt =
            con.prepareStatement(
                "INSERT INTO account_patch_reviews "
                    + "(account_id, change_id, patch_set_id, file_name) VALUES "
                    + "(?, ?, ?, ?)")) {
      stmt.setInt(1, accountId.get());
      stmt.setInt(2, psId.getParentKey().get());
      stmt.setInt(3, psId.get());
      stmt.setString(4, path);
      stmt.executeUpdate();
      return true;
    } catch (SQLException e) {
      OrmException ormException = convertError("insert", e);
      if (ormException instanceof OrmDuplicateKeyException) {
        return false;
      }
      throw ormException;
    }
  }

  @Override
  public void markReviewed(PatchSet.Id psId, Account.Id accountId, Collection<String> paths)
      throws OrmException {
    if (paths == null || paths.isEmpty()) {
      return;
    }

    try (Connection con = ds.getConnection();
        PreparedStatement stmt =
            con.prepareStatement(
                "INSERT INTO account_patch_reviews "
                    + "(account_id, change_id, patch_set_id, file_name) VALUES "
                    + "(?, ?, ?, ?)")) {
      for (String path : paths) {
        stmt.setInt(1, accountId.get());
        stmt.setInt(2, psId.getParentKey().get());
        stmt.setInt(3, psId.get());
        stmt.setString(4, path);
        stmt.addBatch();
      }
      stmt.executeBatch();
    } catch (SQLException e) {
      OrmException ormException = convertError("insert", e);
      if (ormException instanceof OrmDuplicateKeyException) {
        return;
      }
      throw ormException;
    }
  }

  @Override
  public void clearReviewed(PatchSet.Id psId, Account.Id accountId, String path)
      throws OrmException {
    try (Connection con = ds.getConnection();
        PreparedStatement stmt =
            con.prepareStatement(
                "DELETE FROM account_patch_reviews "
                    + "WHERE account_id = ? AND change_id = ? AND "
                    + "patch_set_id = ? AND file_name = ?")) {
      stmt.setInt(1, accountId.get());
      stmt.setInt(2, psId.getParentKey().get());
      stmt.setInt(3, psId.get());
      stmt.setString(4, path);
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw convertError("delete", e);
    }
  }

  @Override
  public void clearReviewed(PatchSet.Id psId) throws OrmException {
    try (Connection con = ds.getConnection();
        PreparedStatement stmt =
            con.prepareStatement(
                "DELETE FROM account_patch_reviews "
                    + "WHERE change_id = ? AND patch_set_id = ?")) {
      stmt.setInt(1, psId.getParentKey().get());
      stmt.setInt(2, psId.get());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw convertError("delete", e);
    }
  }

  @Override
  public Optional<PatchSetWithReviewedFiles> findReviewed(PatchSet.Id psId, Account.Id accountId)
      throws OrmException {
    try (Connection con = ds.getConnection();
        PreparedStatement stmt =
            con.prepareStatement(
                "SELECT patch_set_id, file_name FROM account_patch_reviews APR1 "
                    + "WHERE account_id = ? AND change_id = ? AND patch_set_id = "
                    + "(SELECT MAX(patch_set_id) FROM account_patch_reviews APR2 WHERE "
                    + "APR1.account_id = APR2.account_id "
                    + "AND APR1.change_id = APR2.change_id "
                    + "AND patch_set_id <= ?)")) {
      stmt.setInt(1, accountId.get());
      stmt.setInt(2, psId.getParentKey().get());
      stmt.setInt(3, psId.get());
      try (ResultSet rs = stmt.executeQuery()) {
        if (rs.next()) {
          PatchSet.Id id = new PatchSet.Id(psId.getParentKey(), rs.getInt("patch_set_id"));
          ImmutableSet.Builder<String> builder = ImmutableSet.builder();
          do {
            builder.add(rs.getString("file_name"));
          } while (rs.next());

          return Optional.of(
              AccountPatchReviewStore.PatchSetWithReviewedFiles.create(id, builder.build()));
        }

        return Optional.empty();
      }
    } catch (SQLException e) {
      throw convertError("select", e);
    }
  }

  public OrmException convertError(String op, SQLException err) {
    if (err.getCause() == null && err.getNextException() != null) {
      err.initCause(err.getNextException());
    }
    return new OrmException(op + " failure on account_patch_reviews", err);
  }

  private static String getSQLState(SQLException err) {
    String ec;
    SQLException next = err;
    do {
      ec = next.getSQLState();
      next = next.getNextException();
    } while (ec == null && next != null);
    return ec;
  }

  protected static int getSQLStateInt(SQLException err) {
    String s = getSQLState(err);
    if (s != null) {
      Integer i = Ints.tryParse(s);
      return i != null ? i : -1;
    }
    return 0;
  }
}
