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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.client.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Stage;
import java.io.IOException;
import java.sql.SQLException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

/** Creates or updates the current database schema. */
public class SchemaUpdater {
  private final SchemaFactory<ReviewDb> schema;
  private final SchemaCreator creator;
  private final Provider<SchemaVersion> updater;

  @Inject
  SchemaUpdater(
      @ReviewDbFactory SchemaFactory<ReviewDb> schema, SchemaCreator creator, Injector parent) {
    this.schema = schema;
    this.creator = creator;
    this.updater = buildInjector(parent).getProvider(SchemaVersion.class);
  }

  private static Injector buildInjector(Injector parent) {
    // Use DEVELOPMENT mode to allow lazy initialization of the
    // graph. This avoids touching ancient schema versions that
    // are behind this installation's current version.
    return Guice.createInjector(
        Stage.DEVELOPMENT,
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(SchemaVersion.class).to(SchemaVersion.C);

            for (Key<?> k :
                new Key<?>[] {
                  Key.get(PersonIdent.class, GerritPersonIdent.class),
                  Key.get(String.class, AnonymousCowardName.class),
                  Key.get(Config.class, GerritServerConfig.class),
                }) {
              rebind(parent, k);
            }

            for (Class<?> c :
                new Class<?>[] {
                  AllProjectsName.class,
                  AllUsersCreator.class,
                  AllUsersName.class,
                  GitRepositoryManager.class,
                  SitePaths.class,
                  SystemGroupBackend.class,
                }) {
              rebind(parent, Key.get(c));
            }
          }

          private <T> void rebind(Injector parent, Key<T> c) {
            bind(c).toProvider(parent.getProvider(c));
          }
        });
  }

  public void update(UpdateUI ui) throws OrmException {
    try (ReviewDb db = ReviewDbUtil.unwrapDb(schema.open())) {

      final SchemaVersion u = updater.get();
      final CurrentSchemaVersion version = getSchemaVersion(db);
      if (version == null) {
        try {
          creator.create(db);
        } catch (IOException | ConfigInvalidException e) {
          throw new OrmException("Cannot initialize schema", e);
        }

      } else {
        try {
          u.check(ui, version, db);
        } catch (SQLException e) {
          throw new OrmException("Cannot upgrade schema", e);
        }
      }
    }
  }

  @VisibleForTesting
  public SchemaVersion getLatestSchemaVersion() {
    return updater.get();
  }

  private CurrentSchemaVersion getSchemaVersion(ReviewDb db) {
    try {
      return db.schemaVersion().get(new CurrentSchemaVersion.Key());
    } catch (OrmException e) {
      return null;
    }
  }
}
