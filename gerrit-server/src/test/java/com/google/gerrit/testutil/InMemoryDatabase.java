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

import com.google.gerrit.reviewdb.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SystemConfigProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.server.schema.Current;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.schema.SchemaVersion;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.jdbc.Database;
import com.google.gwtorm.jdbc.SimpleDataSource;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Provider;

import junit.framework.TestCase;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

/**
 * An in-memory test instance of {@link ReviewDb} database.
 * <p>
 * Test classes should create one instance of this class for each unique test
 * database they want to use. When the tests needing this instance are complete,
 * ensure that {@link #drop(InMemoryDatabase)} is called to free the resources so
 * the JVM running the unit tests doesn't run out of heap space.
 */
public class InMemoryDatabase implements SchemaFactory<ReviewDb> {
  private static int dbCnt;

  private static synchronized DataSource newDataSource() throws SQLException {
    final Properties p = new Properties();
    p.setProperty("driver", org.h2.Driver.class.getName());
    p.setProperty("url", "jdbc:h2:mem:" + "Test_" + (++dbCnt));
    final DataSource dataSource = new SimpleDataSource(p);
    return dataSource;
  }

  /** Drop the database from memory; does nothing if the instance was null. */
  public static void drop(final InMemoryDatabase db) {
    if (db != null) {
      db.drop();
    }
  }

  private Connection openHandle;
  private Database<ReviewDb> database;
  private boolean created;
  private SchemaVersion schemaVersion;

  public InMemoryDatabase() throws OrmException {
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

      schemaVersion =
          Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
              install(new SchemaVersion.Module());

              bind(File.class) //
                  .annotatedWith(SitePath.class) //
                  .toInstance(new File("."));

              Config cfg = new Config();
              cfg.setString("gerrit", null, "basePath", "git");
              cfg.setString("user", null, "name", "Gerrit Code Review");
              cfg.setString("user", null, "email", "gerrit@localhost");

              bind(Config.class) //
                  .annotatedWith(GerritServerConfig.class) //
                  .toInstance(cfg);

              bind(PersonIdent.class) //
                  .annotatedWith(GerritPersonIdent.class) //
                  .toProvider(GerritPersonIdentProvider.class);

              bind(GitRepositoryManager.class) //
                  .to(LocalDiskRepositoryManager.class);
            }
          }).getBinding(Key.get(SchemaVersion.class, Current.class))
              .getProvider().get();
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
      final ReviewDb c = open();
      try {
        try {
          new SchemaCreator(
              new File("."),
              schemaVersion,
              null,
              new AllProjectsName("Test-Projects"),
              new PersonIdent("name", "email@site")).create(c);
        } catch (IOException e) {
          throw new OrmException("Cannot create in-memory database", e);
        } catch (ConfigInvalidException e) {
          throw new OrmException("Cannot create in-memory database", e);
        }
      } finally {
        c.close();
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

  public SystemConfig getSystemConfig() {
    return new SystemConfigProvider(this, new Provider<SchemaVersion>() {
      public SchemaVersion get() {
        return schemaVersion;
      }
    }).get();
  }

  public CurrentSchemaVersion getSchemaVersion() throws OrmException {
    final ReviewDb c = open();
    try {
      return c.schemaVersion().get(new CurrentSchemaVersion.Key());
    } finally {
      c.close();
    }
  }

  public void assertSchemaVersion() throws OrmException {
    final CurrentSchemaVersion act = getSchemaVersion();
    TestCase.assertEquals(schemaVersion.getVersionNbr(), act.versionNbr);
  }
}
