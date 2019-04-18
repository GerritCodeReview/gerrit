// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.config.TrackingFootersProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.schema.SchemaModule;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

class InMemoryTestingDatabaseModule extends LifecycleModule {
  private final Config cfg;
  private final Path sitePath;
  @Nullable private final InMemoryRepositoryManager repoManager;

  InMemoryTestingDatabaseModule(
      Config cfg, Path sitePath, @Nullable InMemoryRepositoryManager repoManager) {
    this.cfg = cfg;
    this.sitePath = sitePath;
    this.repoManager = repoManager;
    makeSiteDirs(sitePath);
  }

  @Override
  protected void configure() {
    bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(cfg);

    // TODO(dborowitz): Use jimfs.
    bind(Path.class).annotatedWith(SitePath.class).toInstance(sitePath);

    if (repoManager != null) {
      bind(GitRepositoryManager.class).toInstance(repoManager);
    } else {
      bind(GitRepositoryManager.class).to(InMemoryRepositoryManager.class);
      bind(InMemoryRepositoryManager.class).in(SINGLETON);
    }

    bind(MetricMaker.class).to(DisabledMetricMaker.class);

    listener().to(CreateSchema.class);

    bind(SitePaths.class);
    bind(TrackingFooters.class).toProvider(TrackingFootersProvider.class).in(SINGLETON);

    install(new SchemaModule());

    install(new SshdModule());
  }

  static class CreateSchema implements LifecycleListener {
    private final SchemaCreator schemaCreator;

    @Inject
    CreateSchema(SchemaCreator schemaCreator) {
      this.schemaCreator = schemaCreator;
    }

    @Override
    public void start() {
      try {
        schemaCreator.ensureCreated();
      } catch (IOException | ConfigInvalidException e) {
        throw new StorageException(e);
      }
    }

    @Override
    public void stop() {}
  }

  private static void makeSiteDirs(Path p) {
    try {
      Files.createDirectories(p.resolve("etc"));
    } catch (IOException e) {
      throw new ProvisionException(e.getMessage(), e);
    }
  }
}
