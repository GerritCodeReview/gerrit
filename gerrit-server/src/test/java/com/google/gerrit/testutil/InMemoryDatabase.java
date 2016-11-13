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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.reviewdb.client.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.schema.SchemaVersion;
import com.google.gwtorm.jdbc.Database;
import com.google.gwtorm.jdbc.SimpleDataSource;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * An in-memory test instance of {@link ReviewDb} database.
 *
 * <p>Test classes should create one instance of this class for each unique test database they want
 * to use. When the tests needing this instance are complete, ensure that {@link
 * #drop(InMemoryDatabase)} is called to free the resources so the JVM running the unit tests
 * doesn't run out of heap space.
 */
public class InMemoryDatabase implements SchemaFactory<ReviewDb> {
  public static InMemoryDatabase newDatabase(LifecycleManager lifecycle) {
    Injector injector = Guice.createInjector(new InMemoryModule());
    lifecycle.add(injector);
    return injector.getInstance(InMemoryDatabase.class);
  }

  private static int dbCnt;

  private static synchronized DataSource newDataSource() throws SQLException {
    final Properties p = new Properties();
    p.setProperty("driver", org.h2.Driver.class.getName());
    p.setProperty("url", "jdbc:h2:mem:" + "Test_" + (++dbCnt));
    return new SimpleDataSource(p);
  }

  /** Drop the database from memory; does nothing if the instance was null. */
  public static void drop(final InMemoryDatabase db) {
    if (db != null) {
      db.drop();
    }
  }

  private final SchemaCreator schemaCreator;

  private Connection openHandle;
  private Database<ReviewDb> database;
  private boolean created;

  @Inject
  InMemoryDatabase(SchemaCreator schemaCreator) throws OrmException {
    this.schemaCreator = schemaCreator;

    try {
      DataSource dataSource = newDataSource();

      // Open one connection. This will peg the database into memory
      // until someone calls drop on us, allowing subsequent connections
      // opened against the same URL to go to the same set of tables.
      //
      openHandle = dataSource.getConnection();

      // Build the access layer around the connection factory.
      //
      database = new Database<>(dataSource, ReviewDb.class);

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
  public InMemoryDatabase create() throws OrmException {
    if (!created) {
      created = true;
      try (ReviewDb c = open()) {
        schemaCreator.create(c);
      } catch (IOException | ConfigInvalidException e) {
        throw new OrmException("Cannot create in-memory database", e);
      }
    }
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

  public SystemConfig getSystemConfig() throws OrmException {
    try (ReviewDb c = open()) {
      return c.systemConfig().get(new SystemConfig.Key());
    }
  }

  public CurrentSchemaVersion getSchemaVersion() throws OrmException {
    try (ReviewDb c = open()) {
      return c.schemaVersion().get(new CurrentSchemaVersion.Key());
    }
  }

  public void assertSchemaVersion() throws OrmException {
    assertThat(getSchemaVersion().versionNbr).isEqualTo(SchemaVersion.getBinaryVersion());
  }
}
