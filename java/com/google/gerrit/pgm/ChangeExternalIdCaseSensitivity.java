// Copyright (C) 2021 The Android Open Source Project
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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.storage.notedb.DisabledExternalIdCache;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdCaseSensitivityMigrator;
import com.google.gerrit.server.account.externalids.storage.notedb.ExternalIdNoteDbStorageModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.schema.NoteDbSchemaVersionCheck;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;

/**
 * Changes the case sensitivity of `username:` and `gerrit:` external IDs by recomputing the SHA-1
 * sums used as note names.
 */
public class ChangeExternalIdCaseSensitivity extends SiteProgram {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  @Option(name = "--batch", usage = "Don't ask for confirmation before migrating.")
  private boolean batch;

  @Option(name = "--dryrun", usage = "Do a dryrun of the migration.")
  private boolean dryrun;

  private final LifecycleManager manager = new LifecycleManager();
  private final TextProgressMonitor monitor = new TextProgressMonitor();

  private Config globalConfig;
  private boolean isUserNameCaseInsensitive;
  private ConsoleUI ui;

  @Inject private ExternalIds externalIds;
  @Inject private ExternalIdCaseSensitivityMigrator.Factory migratorFactory;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    ui = ConsoleUI.getInstance(batch);

    Injector dbInjector = createDbInjector();
    manager.add(dbInjector, dbInjector.createChildInjector(NoteDbSchemaVersionCheck.module()));
    dbInjector
        .createChildInjector(
            new FactoryModule() {
              @Override
              protected void configure() {
                bind(GitReferenceUpdated.class).toInstance(GitReferenceUpdated.DISABLED);
                install(
                    new FactoryModuleBuilder()
                        .build(ExternalIdCaseSensitivityMigrator.Factory.class));
                factory(MetaDataUpdate.InternalFactory.class);
                install(new ExternalIdNoteDbStorageModule());

                // The ChangeExternalIdCaseSensitivity program needs to access all external IDs only
                // once to update them. After the update they are not accessed again. Hence the
                // LocalUsernamesToLowerCase program doesn't benefit from caching external IDs and
                // the external ID cache can be disabled.
                install(DisabledExternalIdCache.module());
              }
            })
        .injectMembers(this);
    globalConfig = dbInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));

    this.isUserNameCaseInsensitive =
        globalConfig.getBoolean("auth", "userNameCaseInsensitive", false);

    String message =
        "auth.userNameCaseInsensitive is set to %b. "
            + "External IDs will be migrated to be case %ssensitive. Continue?";
    if (!ui.yesno(
        true, message, isUserNameCaseInsensitive, isUserNameCaseInsensitive ? "" : "in")) {
      return 0;
    }

    Collection<ExternalId> todo = externalIds.all();
    monitor.beginTask("Converting external ID note names", todo.size());

    manager.start();
    try {
      migratorFactory
          .create(!isUserNameCaseInsensitive, dryrun)
          .migrate(todo, () -> monitor.update(1));
    } finally {
      manager.stop();
      monitor.endTask();
    }

    int exitCode;
    if (!dryrun) {
      updateGerritConfig();

      exitCode = reindexAccounts();
    } else {
      exitCode = 0;
    }
    return exitCode;
  }

  private void updateGerritConfig() throws IOException, ConfigInvalidException {
    logger.atInfo().log("Setting auth.userNameCaseInsensitive to true in gerrit.config.");
    FileBasedConfig config =
        new FileBasedConfig(
            globalConfig, getSitePath().resolve("etc/gerrit.config").toFile(), FS.DETECTED);
    config.load();
    config.setBoolean("auth", null, "userNameCaseInsensitive", !isUserNameCaseInsensitive);
    config.save();
  }

  private int reindexAccounts() throws Exception {
    monitor.beginTask("Reindex accounts", ProgressMonitor.UNKNOWN);
    String[] reindexArgs = {
      "--site-path", getSitePath().toString(), "--index", AccountSchemaDefinitions.NAME
    };
    logger.atInfo().log(
        "Migration complete, reindexing accounts with: reindex %s", String.join(" ", reindexArgs));
    Reindex reindexPgm = new Reindex();
    int exitCode = reindexPgm.main(reindexArgs);
    monitor.endTask();
    return exitCode;
  }
}
