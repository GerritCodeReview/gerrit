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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.RuntimeShutdown;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.pgm.util.ThreadLimiter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.index.DummyIndexModule;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.notedb.rebuild.GcAllUsers;
import com.google.gerrit.server.notedb.rebuild.NoteDbMigrator;
import com.google.gerrit.server.schema.DataSourceType;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.ExplicitBooleanOptionHandler;

public class MigrateToNoteDb extends SiteProgram {
  static final String TRIAL_USAGE =
      "Trial mode: migrate changes and turn on reading from NoteDb, but leave ReviewDb as the"
          + " source of truth";

  private static final int ISSUE_8022_THREAD_LIMIT = 4;

  @Option(name = "--threads", usage = "Number of threads to use for rebuilding NoteDb")
  private Integer threads;

  @Option(
      name = "--project",
      usage =
          "Only rebuild these projects, do no other migration; incompatible with --change"
              + " and --skip-project; recommended for debugging only")
  private List<String> projects = new ArrayList<>();

  @Option(
      name = "--skip-project",
      usage = "Rebuild all projects except these; incompatible with the --project and --change")
  private List<String> skipProjects = new ArrayList<>();

  @Option(
      name = "--change",
      usage =
          "Only rebuild these changes, do no other migration; incompatible with --project and"
              + " --skip-project; recommended for debugging only")
  private List<Integer> changes = new ArrayList<>();

  @Option(
      name = "--force",
      usage =
          "Force rebuilding changes where ReviewDb is still the source of truth, even if they"
              + " were previously migrated")
  private boolean force;

  @Option(name = "--trial", usage = TRIAL_USAGE)
  private boolean trial;

  @Option(
      name = "--sequence-gap",
      usage =
          "gap in change sequence numbers between last ReviewDb number and first NoteDb number;"
              + " negative indicates using the value of noteDb.changes.initialSequenceGap (default"
              + " 1000)")
  private int sequenceGap;

  @Option(
      name = "--reindex",
      usage =
          "Reindex all changes after migration; defaults to false in trial mode, true otherwise",
      handler = ExplicitBooleanOptionHandler.class)
  private Boolean reindex;

  private Injector dbInjector;
  private Injector sysInjector;
  private LifecycleManager dbManager;
  private LifecycleManager sysManager;

  @Inject private GcAllUsers gcAllUsers;
  @Inject private Provider<NoteDbMigrator.Builder> migratorBuilderProvider;

  @Override
  public int run() throws Exception {
    RuntimeShutdown.add(this::stop);
    try {
      mustHaveValidSite();
      dbInjector = createDbInjector(MULTI_USER);

      dbManager = new LifecycleManager();
      dbManager.add(dbInjector);
      dbManager.start();

      threads = limitThreads();

      sysInjector = createSysInjector();
      sysInjector.injectMembers(this);
      sysManager = new LifecycleManager();
      sysManager.add(sysInjector);
      sysManager.start();

      try (NoteDbMigrator migrator =
          migratorBuilderProvider
              .get()
              .setThreads(threads)
              .setProgressOut(System.err)
              .setProjects(projects.stream().map(Project.NameKey::new).collect(toList()))
              .setSkipProjects(skipProjects.stream().map(Project.NameKey::new).collect(toList()))
              .setChanges(changes.stream().map(Change.Id::new).collect(toList()))
              .setTrialMode(trial)
              .setForceRebuild(force)
              .setSequenceGap(sequenceGap)
              .build()) {
        if (!projects.isEmpty() || !changes.isEmpty() || !skipProjects.isEmpty()) {
          migrator.rebuild();
        } else {
          migrator.migrate();
        }
      }
      try (PrintWriter w = new PrintWriter(new OutputStreamWriter(System.out, UTF_8), true)) {
        gcAllUsers.run(w);
      }
    } finally {
      stop();
    }

    boolean reindex = firstNonNull(this.reindex, !trial);
    if (!reindex) {
      return 0;
    }
    // Reindex all indices, to save the user from having to run yet another program by hand while
    // their server is offline.
    List<String> reindexArgs =
        ImmutableList.of(
            "--site-path",
            getSitePath().toString(),
            "--threads",
            Integer.toString(threads),
            "--index",
            ChangeSchemaDefinitions.NAME);
    System.out.println("Migration complete, reindexing changes with:");
    System.out.println("  reindex " + reindexArgs.stream().collect(joining(" ")));
    Reindex reindexPgm = new Reindex();
    return reindexPgm.main(reindexArgs.stream().toArray(String[]::new));
  }

  private int limitThreads() {
    if (threads != null) {
      return threads;
    }
    int actualThreads;
    int procs = Runtime.getRuntime().availableProcessors();
    DataSourceType dsType = dbInjector.getInstance(DataSourceType.class);
    if (dsType.getDriver().equals("org.h2.Driver") && procs > ISSUE_8022_THREAD_LIMIT) {
      System.out.println(
          "Not using more than "
              + ISSUE_8022_THREAD_LIMIT
              + " threads due to http://crbug.com/gerrit/8022");
      System.out.println("Can be increased by passing --threads, but may cause errors");
      actualThreads = ISSUE_8022_THREAD_LIMIT;
    } else {
      actualThreads = procs;
    }
    actualThreads = ThreadLimiter.limitThreads(dbInjector, actualThreads);
    return actualThreads;
  }

  private Injector createSysInjector() {
    return dbInjector.createChildInjector(
        new FactoryModule() {
          @Override
          public void configure() {
            install(dbInjector.getInstance(BatchProgramModule.class));
            install(new DummyIndexModule());
            factory(ChangeResource.Factory.class);
            factory(GarbageCollection.Factory.class);
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
