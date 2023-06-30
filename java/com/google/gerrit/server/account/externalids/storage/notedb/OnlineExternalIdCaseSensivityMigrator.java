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

package com.google.gerrit.server.account.externalids.storage.notedb;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.account.externalids.OnlineExternalIdCaseSensivityMigratiorExecutor;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.index.ReindexerAlreadyRunningException;
import com.google.gerrit.server.index.VersionManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

@Singleton
public class OnlineExternalIdCaseSensivityMigrator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private Executor executor;
  private ExternalIdCaseSensitivityMigrator.Factory migratorFactory;
  private ExternalIds externalIds;
  private VersionManager versionManager;
  private Config globalConfig;
  private Path sitePath;
  private final TextProgressMonitor monitor = new TextProgressMonitor();
  private boolean isUserNameCaseInsensitive;
  private boolean isUserNameCaseInsensitiveMigrationMode;

  @Inject
  public OnlineExternalIdCaseSensivityMigrator(
      @OnlineExternalIdCaseSensivityMigratiorExecutor ExecutorService executor,
      ExternalIdCaseSensitivityMigrator.Factory migratorFactory,
      ExternalIds externalIds,
      VersionManager versionManager,
      @GerritServerConfig Config globalConfig,
      @SitePath Path sitePath) {
    this.migratorFactory = migratorFactory;
    this.externalIds = externalIds;
    this.versionManager = versionManager;
    this.globalConfig = globalConfig;
    this.sitePath = sitePath;
    this.executor = executor;
    this.isUserNameCaseInsensitiveMigrationMode =
        globalConfig.getBoolean("auth", "userNameCaseInsensitiveMigrationMode", false);
    this.isUserNameCaseInsensitive =
        globalConfig.getBoolean("auth", "userNameCaseInsensitive", false);
  }

  public void migrate() {
    if (!isUserNameCaseInsensitive || !isUserNameCaseInsensitiveMigrationMode) {
      logger.atSevere().log(
          "External IDs online migration requires auth.userNameCaseInsensitive and"
              + " auth.userNameCaseInsensitiveMigrationMode to be set to true. Skipping"
              + " migration!");
      return;
    }
    executor.execute(
        () -> {
          try {
            Collection<ExternalId> todo = externalIds.all();
            try {
              monitor.beginTask("Converting external ID note names", todo.size());
              migratorFactory
                  .create(isUserNameCaseInsensitive, false)
                  .migrate(todo, () -> monitor.update(1));
            } finally {
              monitor.endTask();
            }
            try {
              updateGerritConfig();
              monitor.beginTask("Reindex accounts", ProgressMonitor.UNKNOWN);
              versionManager.startReindexer("accounts", true);
            } finally {
              monitor.endTask();
            }
            logger.atInfo().log("External IDs migration completed!");
          } catch (IOException | ConfigInvalidException e) {
            logger.atSevere().withCause(e).log(
                "Exception during the external ids migration, cause %s", e.getMessage());
          } catch (ReindexerAlreadyRunningException e) {
            logger.atSevere().log("Failed to reindex external ids: %s", e.getMessage());
          }
        });
  }

  private void updateGerritConfig() throws IOException, ConfigInvalidException {
    logger.atInfo().log(
        "Setting auth.userNameCaseInsensitiveMigrationMode to false in gerrit.config.");

    FileBasedConfig config =
        new FileBasedConfig(
            globalConfig, sitePath.resolve("etc/gerrit.config").toFile(), FS.DETECTED);
    config.load();
    config.setBoolean("auth", null, "userNameCaseInsensitiveMigrationMode", false);

    config.save();
  }
}
