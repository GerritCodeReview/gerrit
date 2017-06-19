// Copyright (C) 2014 The Android Open Source Project
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
import static java.util.stream.Collectors.toList;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.RuntimeShutdown;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.pgm.util.ThreadLimiter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.index.DummyIndexModule;
import com.google.gerrit.server.index.change.ReindexAfterRefUpdate;
import com.google.gerrit.server.notedb.rebuild.NoteDbMigrator;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Option;

public class MigrateToNoteDb extends SiteProgram {
  @Option(name = "--threads", usage = "Number of threads to use for rebuilding NoteDb")
  private int threads = Runtime.getRuntime().availableProcessors();

  @Option(
    name = "--project",
    usage =
        "Only rebuild these projects, do no other migration; incompatible with --change;"
            + " recommended for debugging only"
  )
  private List<String> projects = new ArrayList<>();

  @Option(
    name = "--change",
    usage =
        "Only rebuild these changes, do no other migration; incompatible with --project;"
            + " recommended for debugging only"
  )
  private List<Integer> changes = new ArrayList<>();

  @Option(
    name = "--force",
    usage =
        "Force rebuilding changes where ReviewDb is still the source of truth, even if they"
            + " were previously migrated"
  )
  private boolean force;

  @Option(
    name = "--trial",
    usage =
        "trial mode: migrate changes and turn on reading from NoteDb, but leave ReviewDb as"
            + " the source of truth"
  )
  private boolean trial = true; // TODO(dborowitz): Default to false in 3.0.

  private Injector dbInjector;
  private Injector sysInjector;
  private LifecycleManager dbManager;
  private LifecycleManager sysManager;

  @Inject private Provider<NoteDbMigrator.Builder> migratorBuilderProvider;

  @Override
  public int run() throws Exception {
    RuntimeShutdown.add(this::stop);
    try {
      mustHaveValidSite();
      dbInjector = createDbInjector(MULTI_USER);
      threads = ThreadLimiter.limitThreads(dbInjector, threads);

      LifecycleManager dbManager = new LifecycleManager();
      dbManager.add(dbInjector);
      dbManager.start();

      sysInjector = createSysInjector();
      sysInjector.injectMembers(this);
      LifecycleManager sysManager = new LifecycleManager();
      sysManager.add(sysInjector);
      sysManager.start();

      try (NoteDbMigrator migrator =
          migratorBuilderProvider
              .get()
              .setThreads(threads)
              .setProgressOut(System.err)
              .setProjects(projects.stream().map(Project.NameKey::new).collect(toList()))
              .setChanges(changes.stream().map(Change.Id::new).collect(toList()))
              .setTrialMode(trial)
              .setForceRebuild(force)
              .build()) {
        if (!projects.isEmpty() || !changes.isEmpty()) {
          migrator.rebuild();
        } else {
          migrator.migrate();
        }
      }
    } finally {
      stop();
    }
    return 0;
  }

  private Injector createSysInjector() {
    return dbInjector.createChildInjector(
        new FactoryModule() {
          @Override
          public void configure() {
            install(dbInjector.getInstance(BatchProgramModule.class));
            DynamicSet.bind(binder(), GitReferenceUpdatedListener.class)
                .to(ReindexAfterRefUpdate.class);
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
