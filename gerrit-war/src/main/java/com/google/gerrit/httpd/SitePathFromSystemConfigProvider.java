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

package com.google.gerrit.httpd;

import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.schema.ReviewDbFactory;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Provides {@link Path} annotated with {@link SitePath}. */
class SitePathFromSystemConfigProvider implements Provider<Path> {
  private final Path path;

  @Inject
  SitePathFromSystemConfigProvider(@ReviewDbFactory SchemaFactory<ReviewDb> schemaFactory)
      throws OrmException {
    path = read(schemaFactory);
  }

  @Override
  public Path get() {
    return path;
  }

  private static Path read(SchemaFactory<ReviewDb> schemaFactory)
      throws OrmException {
    try (ReviewDb db = schemaFactory.open()) {
      List<SystemConfig> all = db.systemConfig().all().toList();
      switch (all.size()) {
        case 1:
          return Paths.get(all.get(0).sitePath);
        case 0:
          throw new OrmException("system_config table is empty");
        default:
          throw new OrmException("system_config must have exactly 1 row;"
              + " found " + all.size() + " rows instead");
      }
    }
  }
}
