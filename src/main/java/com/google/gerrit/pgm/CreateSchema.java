// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.pgm;

import static com.google.inject.Stage.PRODUCTION;

import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SchemaVersion;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.server.config.DatabaseModule;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * Creates the Gerrit 2 database schema.
 */
public class CreateSchema extends AbstractProgram {
  @Inject
  private SystemConfig systemConfig;

  @Inject
  private SchemaFactory<ReviewDb> schema;

  @Override
  public int run() throws Exception {
    final Injector injector =
        Guice.createInjector(PRODUCTION, new DatabaseModule());
    injector.injectMembers(this);

    final SchemaVersion sv;
    final ReviewDb db = schema.open();
    try {
      sv = db.schemaVersion().get(new SchemaVersion.Key());
    } finally {
      db.close();
    }
    if (sv == null) {
      System.err.println("Schema failed to initialize");
      return 1;
    }

    System.out.println("Gerrit Code Review initialized.");
    System.out.println("Current settings:");
    System.out.println("  schema version =  " + sv.versionNbr);
    System.out.println("  admin group    =  " + systemConfig.adminGroupId);
    System.out.println("  site_path      =  " + systemConfig.sitePath);
    System.out.println();
    return 0;
  }
}
