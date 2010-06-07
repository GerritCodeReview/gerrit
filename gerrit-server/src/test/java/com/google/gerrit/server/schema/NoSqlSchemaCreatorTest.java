// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.config.SystemConfigProvider;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.nosql.heap.MemoryDatabase;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Provider;

import junit.framework.TestCase;

import java.io.File;

public class NoSqlSchemaCreatorTest extends TestCase {
  private MemoryDatabase<ReviewDb> db;
  private SchemaVersion schemaVersion;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    db = new MemoryDatabase<ReviewDb>(ReviewDb.class);
    schemaVersion =
        Guice.createInjector(new SchemaVersion.Module()).getBinding(
            Key.get(SchemaVersion.class, Current.class)).getProvider().get();
  }

  private CurrentSchemaVersion getSchemaVersion() throws OrmException {
    final ReviewDb c = db.open();
    try {
      return c.schemaVersion().get(new CurrentSchemaVersion.Key());
    } finally {
      c.close();
    }
  }

  private void assertSchemaVersion() throws OrmException {
    final CurrentSchemaVersion act = getSchemaVersion();
    TestCase.assertEquals(schemaVersion.getVersionNbr(), act.versionNbr);
  }

  private SystemConfig getSystemConfig() {
    return new SystemConfigProvider(db, new Provider<SchemaVersion>() {
      public SchemaVersion get() {
        return schemaVersion;
      }
    }).get();
  }

  public void testCreateSchema() throws OrmException {
    final ReviewDb c = db.open();
    try {
      new SchemaCreator(new File("."), schemaVersion).create(c);
    } finally {
      c.close();
    }

    assertSchemaVersion();
    final SystemConfig config = getSystemConfig();
    assertNotNull(config);
    assertNotNull(config.adminGroupId);
    assertNotNull(config.anonymousGroupId);
    assertNotNull(config.registeredGroupId);

    // By default sitePath is set to the current working directory.
    //
    File sitePath = new File(".").getAbsoluteFile();
    if (sitePath.getName().equals(".")) {
      sitePath = sitePath.getParentFile();
    }
    assertEquals(sitePath.getAbsolutePath(), config.sitePath);

    // This is randomly generated and should be at least 20 bytes long.
    //
    assertNotNull(config.registerEmailPrivateKey);
    assertTrue(20 < config.registerEmailPrivateKey.length());
  }
}
