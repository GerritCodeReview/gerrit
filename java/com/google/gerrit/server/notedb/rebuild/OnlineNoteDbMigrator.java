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

import com.google.common.base.Stopwatch;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.OnlineUpgrader;
import com.google.gerrit.server.index.VersionManager;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class OnlineNoteDbMigrator implements LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(OnlineNoteDbMigrator.class);

  public static class Module extends LifecycleModule {
    @Override
    public void configure() {
      listener().to(OnlineNoteDbMigrator.class);
    }
  }

  private Provider<NoteDbMigrator.Builder> migratorBuilderProvider;
  private final OnlineUpgrader indexUpgrader;
  private final boolean upgradeIndex;

  @Inject
  OnlineNoteDbMigrator(
      @GerritServerConfig Config cfg,
      Provider<NoteDbMigrator.Builder> migratorBuilderProvider,
      OnlineUpgrader indexUpgrader) {
    this.migratorBuilderProvider = migratorBuilderProvider;
    this.indexUpgrader = indexUpgrader;
    this.upgradeIndex = VersionManager.getOnlineUpgrade(cfg);
  }

  @Override
  public void start() {
    Thread t = new Thread(this::migrate);
    t.setDaemon(true);
    t.setName(getClass().getSimpleName());
    t.start();
  }

  private void migrate() {
    log.info("Starting online NoteDb migration");
    if (upgradeIndex) {
      log.info("Online index schema upgrades will be deferred until NoteDb migration is complete");
    }
    Stopwatch sw = Stopwatch.createStarted();
    // TODO(dborowitz): Tune threads, maybe expose a progress monitor somewhere.
    try (NoteDbMigrator migrator = migratorBuilderProvider.get().setAutoMigrate(true).build()) {
      migrator.migrate();
    } catch (Exception e) {
      log.error("Error in online NoteDb migration", e);
    }
    log.info("Online NoteDb migration completed in {}s", sw.elapsed(TimeUnit.SECONDS));

    if (upgradeIndex) {
      log.info("Starting deferred index schema upgrades");
      indexUpgrader.start();
    }
  }

  @Override
  public void stop() {
    // Do nothing; upgrade process uses daemon threads and knows how to recover from failures on
    // next attempt.
  }
}
