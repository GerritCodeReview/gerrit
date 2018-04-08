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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testing.InMemoryDatabase;
import com.google.gerrit.testing.InMemoryH2Type;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.TestUpdateUI;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SchemaUpdaterTest {
  private LifecycleManager lifecycle;
  private InMemoryDatabase db;

  @Before
  public void setUp() throws Exception {
    lifecycle = new LifecycleManager();
    db = InMemoryDatabase.newDatabase(lifecycle);
    lifecycle.start();
  }

  @After
  public void tearDown() throws Exception {
    if (lifecycle != null) {
      lifecycle.stop();
    }
    InMemoryDatabase.drop(db);
  }

  @Test
  public void update() throws OrmException, FileNotFoundException, IOException {
    db.create();

    final Path site = Paths.get(UUID.randomUUID().toString());
    final SitePaths paths = new SitePaths(site);
    SchemaUpdater u =
        Guice.createInjector(
                new FactoryModule() {
                  @Override
                  protected void configure() {
                    TypeLiteral<SchemaFactory<ReviewDb>> schemaFactory =
                        new TypeLiteral<SchemaFactory<ReviewDb>>() {};
                    bind(schemaFactory).to(NotesMigrationSchemaFactory.class);
                    bind(Key.get(schemaFactory, ReviewDbFactory.class)).toInstance(db);
                    bind(SitePaths.class).toInstance(paths);

                    Config cfg = new Config();
                    cfg.setString("user", null, "name", "Gerrit Code Review");
                    cfg.setString("user", null, "email", "gerrit@localhost");

                    bind(Config.class) //
                        .annotatedWith(GerritServerConfig.class) //
                        .toInstance(cfg);

                    bind(PersonIdent.class) //
                        .annotatedWith(GerritPersonIdent.class) //
                        .toProvider(GerritPersonIdentProvider.class);

                    bind(String.class).annotatedWith(GerritServerId.class).toInstance("gerrit");

                    bind(AllProjectsName.class).toInstance(new AllProjectsName("All-Projects"));
                    bind(AllUsersName.class).toInstance(new AllUsersName("All-Users"));

                    bind(GitRepositoryManager.class).toInstance(new InMemoryRepositoryManager());

                    bind(String.class) //
                        .annotatedWith(AnonymousCowardName.class) //
                        .toProvider(AnonymousCowardNameProvider.class);

                    bind(DataSourceType.class).to(InMemoryH2Type.class);

                    bind(SystemGroupBackend.class);
                    install(new NotesMigration.Module());
                    bind(MetricMaker.class).to(DisabledMetricMaker.class);
                  }
                })
            .getInstance(SchemaUpdater.class);

    for (SchemaVersion s = u.getLatestSchemaVersion(); s.getVersionNbr() > 1; s = s.getPrior()) {
      try {
        assertThat(s.getPrior().getVersionNbr())
            .named(
                "schema %s has prior version %s. Not true that",
                s.getVersionNbr(), s.getPrior().getVersionNbr())
            .isEqualTo(s.getVersionNbr() - 1);
      } catch (ProvisionException e) {
        // Ignored
        // The oldest supported schema version doesn't have a prior schema
        // version.
        break;
      }
    }

    u.update(new TestUpdateUI());

    db.assertSchemaVersion();
    final SystemConfig sc = db.getSystemConfig();
    assertThat(sc.sitePath).isEqualTo(paths.site_path.toAbsolutePath().toString());
  }
}
