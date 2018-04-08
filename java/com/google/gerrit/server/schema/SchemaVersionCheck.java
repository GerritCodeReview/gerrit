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

import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.ProvisionException;

/** Validates the current schema version. */
public class SchemaVersionCheck implements LifecycleListener {
  public static Module module() {
    return new LifecycleModule() {
      @Override
      protected void configure() {
        listener().to(SchemaVersionCheck.class);
      }
    };
  }

  private final SchemaFactory<ReviewDb> schema;
  private final SitePaths site;

  @Inject
  public SchemaVersionCheck(SchemaFactory<ReviewDb> schemaFactory, SitePaths site) {
    this.schema = schemaFactory;
    this.site = site;
  }

  @Override
  public void start() {
    try (ReviewDb db = schema.open()) {
      final CurrentSchemaVersion currentVer = getSchemaVersion(db);
      final int expectedVer = SchemaVersion.getBinaryVersion();

      if (currentVer == null) {
        throw new ProvisionException(
            "Schema not yet initialized."
                + "  Run init to initialize the schema:\n"
                + "$ java -jar gerrit.war init -d "
                + site.site_path.toAbsolutePath());
      }
      if (currentVer.versionNbr < expectedVer) {
        throw new ProvisionException(
            "Unsupported schema version "
                + currentVer.versionNbr
                + "; expected schema version "
                + expectedVer
                + ".  Run init to upgrade:\n"
                + "$ java -jar "
                + site.gerrit_war.toAbsolutePath()
                + " init -d "
                + site.site_path.toAbsolutePath());
      } else if (currentVer.versionNbr > expectedVer) {
        throw new ProvisionException(
            "Unsupported schema version "
                + currentVer.versionNbr
                + "; expected schema version "
                + expectedVer
                + ". Downgrade is not supported.");
      }
    } catch (OrmException e) {
      throw new ProvisionException("Cannot read schema_version", e);
    }
  }

  @Override
  public void stop() {}

  private CurrentSchemaVersion getSchemaVersion(ReviewDb db) {
    try {
      return db.schemaVersion().get(new CurrentSchemaVersion.Key());
    } catch (OrmException e) {
      return null;
    }
  }
}
