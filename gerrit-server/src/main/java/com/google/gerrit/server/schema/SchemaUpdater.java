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

import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SchemaVersion;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

/** Creates or updates the current database schema. */
public class SchemaUpdater {
  private final SchemaFactory<ReviewDb> schema;
  private final File sitePath;
  private final SchemaCreator creator;

  @Inject
  SchemaUpdater(final SchemaFactory<ReviewDb> schema,
      final @SitePath File sitePath,
      final SchemaCreator creator) {
    this.schema = schema;
    this.sitePath = sitePath;
    this.creator = creator;
  }

  public void update() throws OrmException {
    final ReviewDb db = schema.open();
    try {
      final SchemaVersion version = getSchemaVersion(db);
      if (version == null) {
        creator.create(db);

      } else {
        updateSystemConfig(db);
      }
    } finally {
      db.close();
    }
  }

  private SchemaVersion getSchemaVersion(final ReviewDb db) {
    try {
      return db.schemaVersion().get(new SchemaVersion.Key());
    } catch (OrmException e) {
      return null;
    }
  }

  private void updateSystemConfig(final ReviewDb db) throws OrmException {
    final SystemConfig sc = db.systemConfig().get(new SystemConfig.Key());
    if (sc == null) {
      throw new OrmException("No record in system_config table");
    }
    try {
      sc.sitePath = sitePath.getCanonicalPath();
    } catch (IOException e) {
      sc.sitePath = sitePath.getAbsolutePath();
    }
    db.systemConfig().update(Collections.singleton(sc));
  }
}
