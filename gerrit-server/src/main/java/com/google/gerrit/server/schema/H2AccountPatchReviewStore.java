// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;
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
import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class H2AccountPatchReviewStore implements AccountPatchReviewStore, LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(H2AccountPatchReviewStore.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicItem.bind(binder(), AccountPatchReviewStore.class).to(H2AccountPatchReviewStore.class);
      listener().to(H2AccountPatchReviewStore.class);
    }
  }

  @VisibleForTesting
  public static class InMemoryModule extends LifecycleModule {
    @Override
    protected void configure() {
      H2AccountPatchReviewStore inMemoryStore = new H2AccountPatchReviewStore();
      DynamicItem.bind(binder(), AccountPatchReviewStore.class).toInstance(inMemoryStore);
      listener().toInstance(inMemoryStore);
    }
  }

  private final String url;

  @Inject
  H2AccountPatchReviewStore(@GerritServerConfig Config cfg, SitePaths sitePaths) {
    this.url = H2.appendUrlOptions(cfg, getUrl(sitePaths));
  }

  public static String getUrl(SitePaths sitePaths) {
    return H2.createUrl(sitePaths.db_dir.resolve("account_patch_reviews"));
  }

  /**
   * Creates an in-memory H2 database to store the reviewed flags. This should be used for tests
   * only.
   */
  @VisibleForTesting
  private H2AccountPatchReviewStore() {
    // DB_CLOSE_DELAY=-1: By default the content of an in-memory H2 database is
    // lost at the moment the last connection is closed. This option keeps the
    // content as long as the vm lives.
    this.url = "jdbc:h2:mem:account_patch_reviews;DB_CLOSE_DELAY=-1";
  }

  @Override
  public void start() {
    try {
      createTableIfNotExists(url);
    } catch (OrmException e) {
      log.error("Failed to create table to store account patch reviews", e);
    }
  }

  public static void createTableIfNotExists(String url) throws OrmException {
    try (Connection con = DriverManager.getConnection(url);
        Statement stmt = con.createStatement()) {
      stmt.executeUpdate(
          "CREATE TABLE IF NOT EXISTS ACCOUNT_PATCH_REVIEWS ("
              + "ACCOUNT_ID INTEGER DEFAULT 0 NOT NULL, "
              + "CHANGE_ID INTEGER DEFAULT 0 NOT NULL, "
              + "PATCH_SET_ID INTEGER DEFAULT 0 NOT NULL, "
              + "FILE_NAME VARCHAR(255) DEFAULT '' NOT NULL, "
              + "CONSTRAINT PRIMARY_KEY_ACCOUNT_PATCH_REVIEWS "
              + "PRIMARY KEY (ACCOUNT_ID, CHANGE_ID, PATCH_SET_ID, FILE_NAME)"
              + ")");
    } catch (SQLException e) {
      throw convertError("create", e);
    }
  }

  public static void dropTableIfExists(String url) throws OrmException {
    try (Connection con = DriverManager.getConnection(url);
        Statement stmt = con.createStatement()) {
      stmt.executeUpdate("DROP TABLE IF EXISTS ACCOUNT_PATCH_REVIEWS");
    } catch (SQLException e) {
      throw convertError("create", e);
    }
  }

  @Override
  public void stop() {}

  @Override
  public boolean markReviewed(PatchSet.Id psId, Account.Id accountId, String path)
      throws OrmException {
    try (Connection con = DriverManager.getConnection(url);
        PreparedStatement stmt =
            con.prepareStatement(
                "INSERT INTO ACCOUNT_PATCH_REVIEWS "
                    + "(ACCOUNT_ID, CHANGE_ID, PATCH_SET_ID, FILE_NAME) VALUES "
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

    try (Connection con = DriverManager.getConnection(url);
        PreparedStatement stmt =
            con.prepareStatement(
                "INSERT INTO ACCOUNT_PATCH_REVIEWS "
                    + "(ACCOUNT_ID, CHANGE_ID, PATCH_SET_ID, FILE_NAME) VALUES "
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
    try (Connection con = DriverManager.getConnection(url);
        PreparedStatement stmt =
            con.prepareStatement(
                "DELETE FROM ACCOUNT_PATCH_REVIEWS "
                    + "WHERE ACCOUNT_ID = ? AND CHANGE_ID + ? AND "
                    + "PATCH_SET_ID = ? AND FILE_NAME = ?")) {
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
    try (Connection con = DriverManager.getConnection(url);
        PreparedStatement stmt =
            con.prepareStatement(
                "DELETE FROM ACCOUNT_PATCH_REVIEWS "
                    + "WHERE CHANGE_ID + ? AND PATCH_SET_ID = ?")) {
      stmt.setInt(1, psId.getParentKey().get());
      stmt.setInt(2, psId.get());
      stmt.executeUpdate();
    } catch (SQLException e) {
      throw convertError("delete", e);
    }
  }

  @Override
  public Collection<String> findReviewed(PatchSet.Id psId, Account.Id accountId)
      throws OrmException {
    try (Connection con = DriverManager.getConnection(url);
        PreparedStatement stmt =
            con.prepareStatement(
                "SELECT FILE_NAME FROM ACCOUNT_PATCH_REVIEWS "
                    + "WHERE ACCOUNT_ID = ? AND CHANGE_ID = ? AND PATCH_SET_ID = ?")) {
      stmt.setInt(1, accountId.get());
      stmt.setInt(2, psId.getParentKey().get());
      stmt.setInt(3, psId.get());
      try (ResultSet rs = stmt.executeQuery()) {
        List<String> files = new ArrayList<>();
        while (rs.next()) {
          files.add(rs.getString("FILE_NAME"));
        }
        return files;
      }
    } catch (SQLException e) {
      throw convertError("select", e);
    }
  }

  public static OrmException convertError(String op, SQLException err) {
    switch (getSQLStateInt(err)) {
      case 23001: // UNIQUE CONSTRAINT VIOLATION
      case 23505: // DUPLICATE_KEY_1
        return new OrmDuplicateKeyException("ACCOUNT_PATCH_REVIEWS", err);

      default:
        if (err.getCause() == null && err.getNextException() != null) {
          err.initCause(err.getNextException());
        }
        return new OrmException(op + " failure on ACCOUNT_PATCH_REVIEWS", err);
    }
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

  private static int getSQLStateInt(SQLException err) {
    String s = getSQLState(err);
    if (s != null) {
      Integer i = Ints.tryParse(s);
      return i != null ? i : -1;
    }
    return 0;
  }
}
