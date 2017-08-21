// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.RuntimeShutdown;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.pgm.util.ThreadLimiter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.DummyIndexModule;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.notedb.PrimaryStorageMigrator;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.OneOffRequestContext;
import com.google.inject.Inject;
import com.google.inject.Injector;

import java.util.Optional;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

public class MigrateToReviewDb extends SiteProgram {
  @Option(name = "--threads", usage = "Number of threads to use for rebuilding NoteDb")
  private int threads = Runtime.getRuntime().availableProcessors();

  private Injector dbInjector;
  private Injector sysInjector;
  private LifecycleManager dbManager;
  private LifecycleManager sysManager;

  @Inject @GerritServerConfig private Config cfg;
  @Inject private OneOffRequestContext requestContext;
  @Inject private ProjectCache projectCache;
  @Inject private GitRepositoryManager repoManager;
  @Inject private PrimaryStorageMigrator migrator;

  @Override
  public int run() throws Exception {
    RuntimeShutdown.add(this::stop);

    mustHaveValidSite();
    dbInjector = createDbInjector(MULTI_USER);
    threads = ThreadLimiter.limitThreads(dbInjector, threads);

    dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    sysInjector.injectMembers(this);
    sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    Optional<NotesMigrationState> s = NotesMigrationState.forConfig(cfg);
    if (!s.equals(Optional.of(NotesMigrationState.NOTE_DB))) {
      throw die("Must be in full NoteDb state, found " + s);
    }

    int i = 0;
    try (ManualRequestContext ctx = requestContext.open()) {
      for (Project.NameKey p : projectCache.all()) {
        try (Repository repo = repoManager.openRepository(p)) {
          for (String ref : repo.getRefDatabase().getRefs(RefDatabase.ALL).keySet()) {
            Change.Id id = Change.Id.fromRef(ref);
            if (id == null || !RefNames.changeMetaRef(id).equals(ref)) {
              continue;
            }
            System.err.format("(%07d) Migrating %s,%s", ++i, p, id);
            migrator.migrateToReviewDbPrimary(id, p);
          }
        }
      }
    }

    return 0;
  }

  private Injector createSysInjector() {
    return dbInjector.createChildInjector(
        new FactoryModule() {
          @Override
          public void configure() {
            bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
            install(dbInjector.getInstance(BatchProgramModule.class));
            install(new DummyIndexModule());
            factory(ChangeResource.Factory.class);
          }
        });
  }

  private void stop() {
    try {
      LifecycleManager m = sysManager;
      sysManager = null;
      if (m != null) {
        m.stop();
      }
    } finally {
      LifecycleManager m = dbManager;
      dbManager = null;
      if (m != null) {
        m.stop();
      }
    }
  }
}
