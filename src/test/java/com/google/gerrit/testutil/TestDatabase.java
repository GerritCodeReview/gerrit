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

package com.google.gerrit.testutil;

import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.config.SystemConfigProvider;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.jdbc.Database;
import com.google.gwtorm.jdbc.SimpleDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

/**
 * An in-memory test instance of {@link ReviewDb} database.
 * <p>
 * Test classes should create one instance of this class for each unique test
 * database they want to use. When the tests needing this instance are complete,
 * ensure that {@link #drop(TestDatabase)} is called to free the resources so
 * the JVM running the unit tests doesn't run out of heap space.
 */
public class TestDatabase implements SchemaFactory<ReviewDb> {
  private static int dbCnt;

  private static synchronized DataSource newDataSource() throws SQLException {
    final Properties p = new Properties();
    p.setProperty("driver", org.h2.Driver.class.getName());
    p.setProperty("url", "jdbc:h2:mem:" + "Test_" + (++dbCnt));
    final DataSource dataSource = new SimpleDataSource(p);
    return dataSource;
  }

  /** Drop the database from memory; does nothing if the instance was null. */
  public static void drop(final TestDatabase db) {
    if (db != null) {
      db.drop();
    }
  }

  private Connection openHandle;
  private Database<ReviewDb> database;

  public TestDatabase() throws OrmException {
    try {
      final DataSource dataSource = newDataSource();

      // Open one connection. This will peg the database into memory
      // until someone calls drop on us, allowing subsequent connections
      // opened against the same URL to go to the same set of tables.
      //
      openHandle = dataSource.getConnection();

      // Build the access layer around the connection factory.
      //
      database = new Database<ReviewDb>(dataSource, ReviewDb.class);
    } catch (SQLException e) {
      throw new OrmException(e);
    }
  }

  public Database<ReviewDb> getDatabase() {
    return database;
  }

  @Override
  public ReviewDb open() throws OrmException {
    return getDatabase().open();
  }

  /** Ensure the database schema has been created and initialized. */
  public TestDatabase create() {
    new SystemConfigProvider(this).get();
    return this;
  }

  /** Drop this database from memory so it no longer exists. */
  public void drop() {
    if (openHandle != null) {
      try {
        openHandle.close();
      } catch (SQLException e) {
        System.err.println("WARNING: Cannot close database connection");
        e.printStackTrace(System.err);
      }
      openHandle = null;
      database = null;
    }
  }
}
