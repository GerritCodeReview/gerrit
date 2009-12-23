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

package com.google.gerrit.server.config;

import com.google.gerrit.reviewdb.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.schema.Current;
import com.google.gerrit.server.schema.SchemaVersion;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import java.util.List;

/** Loads the {@link SystemConfig} from the database. */
public class SystemConfigProvider implements Provider<SystemConfig> {
  private final SchemaFactory<ReviewDb> schema;

  @Current
  private final Provider<SchemaVersion> version;

  @Inject
  public SystemConfigProvider(final SchemaFactory<ReviewDb> schemaFactory,
      @Current final Provider<SchemaVersion> version) {
    this.schema = schemaFactory;
    this.version = version;
  }

  @Override
  public SystemConfig get() {
    try {
      final ReviewDb db = schema.open();
      try {
        final CurrentSchemaVersion sVer = getSchemaVersion(db);
        final int eVer = version.get().getVersionNbr();

        if (sVer == null) {
          throw new OrmException("Schema not yet initialized."
              + "  Run init to initialize the schema.");
        }
        if (sVer.versionNbr != eVer) {
          throw new OrmException("Unsupported schema version "
              + sVer.versionNbr + "; expected schema version " + eVer
              + ".  Run init to upgrade.");
        }

        final List<SystemConfig> all = db.systemConfig().all().toList();
        switch (all.size()) {
          case 1:
            return all.get(0);
          case 0:
            throw new OrmException("system_config table is empty");
          default:
            throw new OrmException("system_config must have exactly 1 row;"
                + " found " + all.size() + " rows instead");
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new ProvisionException("Cannot read system_config", e);
    }
  }

  private CurrentSchemaVersion getSchemaVersion(final ReviewDb db) {
    try {
      return db.schemaVersion().get(new CurrentSchemaVersion.Key());
    } catch (OrmException e) {
      return null;
    }
  }
}
