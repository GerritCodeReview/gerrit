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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.ReindexerAlreadyRunningException;
import com.google.gerrit.server.index.VersionManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.Executor;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

@Singleton
public class OnlineExternalIdCaseSensivityMigrator implements LifecycleListener {

  public static class OnlineExternalIdCaseSensivityMigratorModule extends LifecycleModule {

    @Override
    public void configure() {
      install(
          new FactoryModuleBuilder().build(ChangeExternalIdCaseSensitivityMigrator.Factory.class));
      listener().to(OnlineExternalIdCaseSensivityMigrator.class);
    }
  }

  private Executor executor;
  private ChangeExternalIdCaseSensitivityMigrator.Factory migratorFactory;
  private ExternalIds externalIds;
  private VersionManager versionManager;
  private Config globalConfig;
  private Path sitePath;

  @Inject
  public OnlineExternalIdCaseSensivityMigrator(
      WorkQueue workQueue,
      ChangeExternalIdCaseSensitivityMigrator.Factory migratorFactory,
      ExternalIds externalIds,
      VersionManager versionManager,
      @GerritServerConfig Config globalConfig,
      @SitePath Path sitePath) {
    this.migratorFactory = migratorFactory;
    this.externalIds = externalIds;
    this.versionManager = versionManager;
    this.globalConfig = globalConfig;
    this.sitePath = sitePath;
    executor = workQueue.createQueue(1, "MigrateAccount", true);
  }

  public void migrate() {
    executor.execute(
        () -> {
          try {
            Collection<ExternalId> todo = externalIds.all();
            migratorFactory.create(true, false).migrate(todo, () -> {});
            updateGerritConfig();
            versionManager.startReindexer("accounts", true);
          } catch (IOException | ConfigInvalidException e) {
            e.printStackTrace();
          } catch (ReindexerAlreadyRunningException e) {
            e.printStackTrace();
          }
        });
  }

  private void updateGerritConfig() throws IOException, ConfigInvalidException {
    FileBasedConfig config =
        new FileBasedConfig(
            globalConfig, sitePath.resolve("etc/gerrit.config").toFile(), FS.DETECTED);
    config.load();
    config.setBoolean("auth", null, "userNameCaseInsensitive", true);
    config.setBoolean("auth", null, "userNameCaseInsensitiveMigrationMode", false);
    config.setBoolean("auth", null, "userNameCaseInsensitiveOnlineMigration", false);

    config.save();
  }

  @Override
  public void start() {
    Thread t = new Thread(this::migrate);
    t.setDaemon(true);
    t.setName(getClass().getSimpleName());
    t.start();
  }

  @Override
  public void stop() {}
}
