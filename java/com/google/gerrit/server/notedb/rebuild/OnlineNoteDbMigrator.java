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

package com.google.gerrit.server.notedb.rebuild;

import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.OnlineUpgrader;
import com.google.gerrit.server.index.VersionManager;
import com.google.gerrit.server.notedb.rebuild.NoteDbMigrator.Builder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;

@Singleton
public class OnlineNoteDbMigrator implements LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String TRIAL = "OnlineNoteDbMigrator/trial";

  private static final String ONLINE_MIGRATION_THREADS = "onlineMigrationThreads";

  public static final String ONLINE_MIGRATION_PROJECTS = "onlineMigrationProjects";

  private static final String ONLINE_MIGRATION_SKIP_ALREADY_BUILT =
      "onlineMigrationSkipAlreadyBuilt";

  public static class Module extends LifecycleModule {
    private final boolean trial;

    public Module(boolean trial) {
      this.trial = trial;
    }

    @Override
    public void configure() {
      listener().to(OnlineNoteDbMigrator.class);
      bindConstant().annotatedWith(Names.named(TRIAL)).to(trial);
    }
  }

  private final GcAllUsers gcAllUsers;
  private final OnlineUpgrader indexUpgrader;
  private final Provider<NoteDbMigrator.Builder> migratorBuilderProvider;
  private final boolean upgradeIndex;
  private final boolean trial;
  private final int threads;
  private final String[] projects;
  private final boolean skipAlreadyBuilt;

  @Inject
  OnlineNoteDbMigrator(
      @GerritServerConfig Config cfg,
      GcAllUsers gcAllUsers,
      OnlineUpgrader indexUpgrader,
      Provider<NoteDbMigrator.Builder> migratorBuilderProvider,
      @Named(TRIAL) boolean trial) {
    this.gcAllUsers = gcAllUsers;
    this.indexUpgrader = indexUpgrader;
    this.migratorBuilderProvider = migratorBuilderProvider;
    this.upgradeIndex = VersionManager.getOnlineUpgrade(cfg);
    this.trial = trial || NoteDbMigrator.getTrialMode(cfg);
    this.threads = cfg.getInt(SECTION_NOTE_DB, ONLINE_MIGRATION_THREADS, 1);
    this.projects = cfg.getStringList(SECTION_NOTE_DB, null, ONLINE_MIGRATION_PROJECTS);
    this.skipAlreadyBuilt =
        cfg.getBoolean(SECTION_NOTE_DB, null, ONLINE_MIGRATION_SKIP_ALREADY_BUILT, false);
  }

  @Override
  public void start() {
    Thread t = new Thread(this::migrate);
    t.setDaemon(true);
    t.setName(getClass().getSimpleName());
    t.start();
  }

  @VisibleForTesting
  public void migrate() {
    logger.atInfo().log("Starting online NoteDb migration");
    if (upgradeIndex) {
      logger.atInfo().log(
          "Online index schema upgrades will be deferred until NoteDb migration is complete");
    }
    Stopwatch sw = Stopwatch.createStarted();

    Builder migratorBuilder =
        migratorBuilderProvider
            .get()
            .setThreads(threads)
            .setAutoMigrate(true)
            .setTrialMode(trial)
            .setSkipAlreadyBuilt(skipAlreadyBuilt);

    try {
      // TODO(dborowitz): maybe expose a progress monitor somewhere.
      if (projects.length > 0) {
        try (NoteDbMigrator migrator =
            migratorBuilder
                .setProjects(
                    Arrays.asList(projects).stream()
                        .map(Project.NameKey::new)
                        .collect(Collectors.toList()))
                .build()) {
          migrator.rebuild();
        }
      } else {
        try (NoteDbMigrator migrator = migratorBuilder.build()) {
          migrator.migrate();
        }
      }
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Error in online NoteDb migration");
    }
    gcAllUsers.runWithLogger();
    logger.atInfo().log("Online NoteDb migration completed in %ss", sw.elapsed(TimeUnit.SECONDS));

    if (upgradeIndex) {
      logger.atInfo().log("Starting deferred index schema upgrades");
      indexUpgrader.start();
    }
  }

  @Override
  public void stop() {
    // Do nothing; upgrade process uses daemon threads and knows how to recover from failures on
    // next attempt.
  }
}
