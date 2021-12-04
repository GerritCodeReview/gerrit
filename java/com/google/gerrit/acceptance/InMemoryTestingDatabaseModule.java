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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.guava.CaffeinatedGuava;
import com.google.common.cache.Cache;
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
import com.google.gerrit.server.git.BatchRefUpdateWithCacheUpdate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RefByNameCache;
import com.google.gerrit.server.git.RefRenameWithCacheUpdate;
import com.google.gerrit.server.git.RefUpdateWithCacheUpdate;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.schema.SchemaModule;
import com.google.gerrit.testing.CachedRefInMemoryRepository;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;

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
    bind(Path.class).annotatedWith(SitePath.class).toInstance(sitePath);

    if (repoManager != null) {
      bind(GitRepositoryManager.class).toInstance(repoManager);
    } else {
      install(
          new AbstractModule() {
            @Override
            protected void configure() {
              TypeLiteral<String> keyType = TypeLiteral.get(String.class);
              TypeLiteral<Optional<Ref>> valType = new TypeLiteral<Optional<Ref>>() {};
              Type type =
                  Types.newParameterizedType(Cache.class, keyType.getType(), valType.getType());
              Named named = Names.named(RefByNameCache.REF_BY_NAME);
              @SuppressWarnings("unchecked")
              Key<Cache<String, Optional<Ref>>> key =
                  (Key<Cache<String, Optional<Ref>>>) Key.get(type, named);
              bind(key).toInstance(CaffeinatedGuava.build(Caffeine.newBuilder()));
            }
          });
      factory(RefUpdateWithCacheUpdate.Factory.class);
      factory(RefRenameWithCacheUpdate.Factory.class);
      factory(BatchRefUpdateWithCacheUpdate.Factory.class);
      factory(CachedRefInMemoryRepository.RefDbFactory.class);
      bind(InMemoryRepositoryManager.RepoWrapperFactory.class)
          .to(CachedRefInMemoryRepository.Factory.class);
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
