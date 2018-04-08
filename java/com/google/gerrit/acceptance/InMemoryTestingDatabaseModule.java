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
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePath;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.config.TrackingFooters;
import com.google.gerrit.config.TrackingFootersProvider;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.GwtormChangeBundleReader;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.schema.DataSourceType;
import com.google.gerrit.server.schema.NotesMigrationSchemaFactory;
import com.google.gerrit.server.schema.ReviewDbFactory;
import com.google.gerrit.server.schema.SchemaModule;
import com.google.gerrit.server.schema.SchemaVersion;
import com.google.gerrit.testing.InMemoryDatabase;
import com.google.gerrit.testing.InMemoryH2Type;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.eclipse.jgit.lib.Config;

class InMemoryTestingDatabaseModule extends LifecycleModule {
  private final Config cfg;
  private final Path sitePath;
  @Nullable private final InMemoryRepositoryManager repoManager;
  @Nullable private final InMemoryDatabase.Instance inMemoryDatabaseInstance;

  InMemoryTestingDatabaseModule(
      Config cfg,
      Path sitePath,
      @Nullable InMemoryRepositoryManager repoManager,
      @Nullable InMemoryDatabase.Instance inMemoryDatabaseInstance) {
    this.cfg = cfg;
    this.sitePath = sitePath;
    this.repoManager = repoManager;
    this.inMemoryDatabaseInstance = inMemoryDatabaseInstance;
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
    bind(DataSourceType.class).to(InMemoryH2Type.class);

    install(new NotesMigration.Module());
    TypeLiteral<SchemaFactory<ReviewDb>> schemaFactory =
        new TypeLiteral<SchemaFactory<ReviewDb>>() {};
    bind(schemaFactory).to(NotesMigrationSchemaFactory.class);
    bind(InMemoryDatabase.Instance.class).toProvider(Providers.of(inMemoryDatabaseInstance));
    bind(Key.get(schemaFactory, ReviewDbFactory.class)).to(InMemoryDatabase.class);
    bind(InMemoryDatabase.class).in(SINGLETON);
    bind(ChangeBundleReader.class).to(GwtormChangeBundleReader.class);

    listener().to(CreateDatabase.class);

    bind(SitePaths.class);
    bind(TrackingFooters.class).toProvider(TrackingFootersProvider.class).in(SINGLETON);

    install(new SchemaModule());
    bind(SchemaVersion.class).to(SchemaVersion.C);
  }

  @Provides
  @Singleton
  KeyPairProvider createHostKey() {
    return getHostKeys();
  }

  private static SimpleGeneratorHostKeyProvider keys;

  private static synchronized KeyPairProvider getHostKeys() {
    if (keys == null) {
      keys = new SimpleGeneratorHostKeyProvider();
      keys.setAlgorithm("RSA");
      keys.loadKeys();
    }
    return keys;
  }

  static class CreateDatabase implements LifecycleListener {
    private final InMemoryDatabase mem;

    @Inject
    CreateDatabase(InMemoryDatabase mem) {
      this.mem = mem;
    }

    @Override
    public void start() {
      try {
        mem.create();
      } catch (OrmException e) {
        throw new OrmRuntimeException(e);
      }
    }

    @Override
    public void stop() {
      mem.getDbInstance().drop();
    }
  }

  private static void makeSiteDirs(Path p) {
    try {
      Files.createDirectories(p.resolve("etc"));
    } catch (IOException e) {
      ProvisionException pe = new ProvisionException(e.getMessage());
      pe.initCause(e);
      throw pe;
    }
  }
}
