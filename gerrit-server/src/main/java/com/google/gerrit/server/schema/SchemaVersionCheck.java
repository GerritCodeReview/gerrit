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

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

/** Validates the current schema version. */
public class SchemaVersionCheck implements LifecycleListener {
  public static Module module () {
    return new LifecycleModule() {
      @Override
      protected void configure() {
        listener().to(SchemaVersionCheck.class);
      }
    };
  }

  private final SchemaFactory<ReviewDb> schema;

  @Current
  private final Provider<SchemaVersion> version;

  @Inject
  public SchemaVersionCheck(SchemaFactory<ReviewDb> schemaFactory,
      @Current Provider<SchemaVersion> version) {
    this.schema = schemaFactory;
    this.version = version;
  }

  public void start() {
    try {
      final ReviewDb db = schema.open();
      try {
        final CurrentSchemaVersion sVer = getSchemaVersion(db);
        final int eVer = version.get().getVersionNbr();

        if (sVer == null) {
          throw new ProvisionException("Schema not yet initialized."
              + "  Run init to initialize the schema.");
        }
        if (sVer.versionNbr != eVer) {
          throw new ProvisionException("Unsupported schema version "
              + sVer.versionNbr + "; expected schema version " + eVer
              + ".  Run init to upgrade.");
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new ProvisionException("Cannot read schema_version", e);
    }
  }

  public void stop() {
  }

  private CurrentSchemaVersion getSchemaVersion(final ReviewDb db) {
    try {
      return db.schemaVersion().get(new CurrentSchemaVersion.Key());
    } catch (OrmException e) {
      return null;
    }
  }
}
