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

import com.google.gerrit.reviewdb.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;

/** Creates or updates the current database schema. */
public class SchemaUpdater {
  private final SchemaFactory<ReviewDb> schema;
  private final SitePaths site;
  private final SchemaCreator creator;
  private final Provider<SchemaVersion> updater;

  @Inject
  SchemaUpdater(final SchemaFactory<ReviewDb> schema, final SitePaths site,
      final SchemaCreator creator, @Current final Provider<SchemaVersion> update) {
    this.schema = schema;
    this.site = site;
    this.creator = creator;
    this.updater = update;
  }

  public void update(final UpdateUI ui) throws OrmException {
    final ReviewDb db = schema.open();
    try {
      final SchemaVersion u = updater.get();
      final CurrentSchemaVersion version = getSchemaVersion(db);
      if (version == null) {
        try {
          creator.create(db);
        } catch (IOException e) {
          throw new OrmException("Cannot initialize schema", e);
        } catch (ConfigInvalidException e) {
          throw new OrmException("Cannot initialize schema", e);
        }

      } else {
        try {
          u.check(ui, version, db, true);
        } catch (SQLException e) {
          throw new OrmException("Cannot upgrade schema", e);
        }

        updateSystemConfig(db);
      }
    } finally {
      db.close();
    }
  }

  private CurrentSchemaVersion getSchemaVersion(final ReviewDb db) {
    try {
      return db.schemaVersion().get(new CurrentSchemaVersion.Key());
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
      sc.sitePath = site.site_path.getCanonicalPath();
    } catch (IOException e) {
      sc.sitePath = site.site_path.getAbsolutePath();
    }
    db.systemConfig().update(Collections.singleton(sc));
  }
}
