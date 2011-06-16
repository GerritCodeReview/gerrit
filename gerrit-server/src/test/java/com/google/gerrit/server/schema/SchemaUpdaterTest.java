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
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gerrit.testutil.InMemoryDatabase;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.client.StatementExecutor;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.TypeLiteral;

import junit.framework.TestCase;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.UUID;

public class SchemaUpdaterTest extends TestCase {
  private InMemoryDatabase db;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    db = new InMemoryDatabase();
  }

  @Override
  protected void tearDown() throws Exception {
    InMemoryDatabase.drop(db);
    super.tearDown();
  }

  public void testUpdate() throws OrmException, FileNotFoundException {
    db.create();

    final File site = new File(UUID.randomUUID().toString());
    final SitePaths paths = new SitePaths(site);
    SchemaUpdater u = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(new TypeLiteral<SchemaFactory<ReviewDb>>() {}).toInstance(db);
        bind(SitePaths.class).toInstance(paths);
        install(new SchemaVersion.Module());

        Config cfg = new Config();
        cfg.setString("gerrit", null, "basePath", "git");
        cfg.setString("user", null, "name", "Gerrit Code Review");
        cfg.setString("user", null, "email", "gerrit@localhost");

        bind(Config.class) //
            .annotatedWith(GerritServerConfig.class) //
            .toInstance(cfg);

        bind(PersonIdent.class) //
            .annotatedWith(GerritPersonIdent.class) //
            .toProvider(GerritPersonIdentProvider.class);

        bind(AllProjectsName.class)
            .toInstance(new AllProjectsName("All-Projects"));

        bind(GitRepositoryManager.class) //
            .to(LocalDiskRepositoryManager.class);
      }
    }).getInstance(SchemaUpdater.class);

    u.update(new UpdateUI() {
      @Override
      public void message(String msg) {
      }

      @Override
      public boolean yesno(boolean def, String msg) {
        return def;
      }

      @Override
      public void pruneSchema(StatementExecutor e, List<String> pruneList)
          throws OrmException {
        for (String sql : pruneList) {
          e.execute(sql);
        }
      }
    });

    db.assertSchemaVersion();
    final SystemConfig sc = db.getSystemConfig();
    assertEquals(paths.site_path.getAbsolutePath(), sc.sitePath);
  }
}
