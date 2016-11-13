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

package com.google.gerrit.testutil;

import static com.google.inject.Scopes.SINGLETON;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.gpg.GpgModule;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.config.GerritOptions;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.config.TrackingFootersProvider;
import com.google.gerrit.server.git.ChangeUpdateExecutor;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.PerThreadRequestScope;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.SendEmailExecutor;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.mail.SignedTokenEmailTokenVerifier;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.GwtormChangeBundleReader;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.patch.DiffExecutor;
import com.google.gerrit.server.schema.DataSourceType;
import com.google.gerrit.server.schema.H2AccountPatchReviewStore;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.securestore.DefaultSecureStore;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.ssh.NoSshKeyCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.servlet.RequestScoped;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;

public class InMemoryModule extends FactoryModule {
  public static Config newDefaultConfig() {
    Config cfg = new Config();
    setDefaults(cfg);
    return cfg;
  }

  public static void setDefaults(Config cfg) {
    cfg.setEnum("auth", null, "type", AuthType.DEVELOPMENT_BECOME_ANY_ACCOUNT);
    cfg.setString("gerrit", null, "allProjects", "Test-Projects");
    cfg.setString("gerrit", null, "basePath", "git");
    cfg.setString("gerrit", null, "canonicalWebUrl", "http://test/");
    cfg.setString("user", null, "name", "Gerrit Code Review");
    cfg.setString("user", null, "email", "gerrit@localhost");
    cfg.unset("cache", null, "directory");
    cfg.setString("index", null, "type", "lucene");
    cfg.setBoolean("index", "lucene", "testInmemory", true);
    cfg.setInt("sendemail", null, "threadPoolSize", 0);
    cfg.setBoolean("receive", null, "enableSignedPush", false);
    cfg.setString("receive", null, "certNonceSeed", "sekret");
  }

  private final Config cfg;
  private final TestNotesMigration notesMigration;

  public InMemoryModule() {
    this(newDefaultConfig(), new TestNotesMigration());
  }

  public InMemoryModule(Config cfg, TestNotesMigration notesMigration) {
    this.cfg = cfg;
    this.notesMigration = notesMigration;
  }

  public void inject(Object instance) {
    Guice.createInjector(this).injectMembers(instance);
  }

  @Override
  protected void configure() {
    // Do NOT bind @RemotePeer, as it is bound in a child injector of
    // ChangeMergeQueue (bound via GerritGlobalModule below), so there cannot be
    // a binding in the parent injector. If you need @RemotePeer, you must bind
    // it in a child injector of the one containing InMemoryModule. But unless
    // you really need to test something request-scoped, you likely don't
    // actually need it.

    // For simplicity, don't create child injectors, just use this one to get a
    // few required modules.
    Injector cfgInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(cfg);
              }
            });
    bind(MetricMaker.class).to(DisabledMetricMaker.class);
    install(cfgInjector.getInstance(GerritGlobalModule.class));
    install(new SearchingChangeCacheImpl.Module());
    factory(GarbageCollection.Factory.class);

    bindScope(RequestScoped.class, PerThreadRequestScope.REQUEST);

    // TODO(dborowitz): Use jimfs.
    bind(Path.class).annotatedWith(SitePath.class).toInstance(Paths.get("."));
    bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(cfg);
    bind(GerritOptions.class).toInstance(new GerritOptions(cfg, false, false, false));
    bind(PersonIdent.class)
        .annotatedWith(GerritPersonIdent.class)
        .toProvider(GerritPersonIdentProvider.class);
    bind(String.class)
        .annotatedWith(AnonymousCowardName.class)
        .toProvider(AnonymousCowardNameProvider.class);
    bind(String.class).annotatedWith(GerritServerId.class).toInstance("gerrit");
    bind(AllProjectsName.class).toProvider(AllProjectsNameProvider.class);
    bind(AllUsersName.class).toProvider(AllUsersNameProvider.class);
    bind(GitRepositoryManager.class).to(InMemoryRepositoryManager.class);
    bind(InMemoryRepositoryManager.class).in(SINGLETON);
    bind(TrackingFooters.class).toProvider(TrackingFootersProvider.class).in(SINGLETON);
    bind(NotesMigration.class).toInstance(notesMigration);
    bind(ListeningExecutorService.class)
        .annotatedWith(ChangeUpdateExecutor.class)
        .toInstance(MoreExecutors.newDirectExecutorService());

    bind(DataSourceType.class).to(InMemoryH2Type.class);
    bind(new TypeLiteral<SchemaFactory<ReviewDb>>() {}).to(InMemoryDatabase.class);
    bind(ChangeBundleReader.class).to(GwtormChangeBundleReader.class);

    bind(SecureStore.class).to(DefaultSecureStore.class);

    install(NoSshKeyCache.module());
    install(
        new CanonicalWebUrlModule() {
          @Override
          protected Class<? extends Provider<String>> provider() {
            return CanonicalWebUrlProvider.class;
          }
        });
    //Replacement of DiffExecutorModule to not use thread pool in the tests
    install(
        new AbstractModule() {
          @Override
          protected void configure() {}

          @Provides
          @Singleton
          @DiffExecutor
          public ExecutorService createDiffExecutor() {
            return MoreExecutors.newDirectExecutorService();
          }
        });
    install(new DefaultCacheFactory.Module());
    install(new FakeEmailSender.Module());
    install(new SignedTokenEmailTokenVerifier.Module());
    install(new GpgModule(cfg));
    install(new H2AccountPatchReviewStore.InMemoryModule());

    IndexType indexType = null;
    try {
      indexType = cfg.getEnum("index", null, "type", IndexType.LUCENE);
    } catch (IllegalArgumentException e) {
      // Custom index type, caller must provide their own module.
    }
    if (indexType != null) {
      switch (indexType) {
        case LUCENE:
          install(luceneIndexModule());
          break;
        case ELASTICSEARCH:
          install(elasticIndexModule());
          break;
        default:
          throw new ProvisionException("index type unsupported in tests: " + indexType);
      }
    }
  }

  @Provides
  @Singleton
  @SendEmailExecutor
  public ExecutorService createSendEmailExecutor() {
    return MoreExecutors.newDirectExecutorService();
  }

  @Provides
  @Singleton
  InMemoryDatabase getInMemoryDatabase(SchemaCreator schemaCreator) throws OrmException {
    return new InMemoryDatabase(schemaCreator);
  }

  private Module luceneIndexModule() {
    return indexModule("com.google.gerrit.lucene.LuceneIndexModule");
  }

  private Module elasticIndexModule() {
    return indexModule("com.google.gerrit.elasticsearch.ElasticIndexModule");
  }

  private Module indexModule(String moduleClassName) {
    try {
      Map<String, Integer> singleVersions = new HashMap<>();
      int version = cfg.getInt("index", "lucene", "testVersion", -1);
      if (version > 0) {
        singleVersions.put(ChangeSchemaDefinitions.INSTANCE.getName(), version);
      }
      Class<?> clazz = Class.forName(moduleClassName);
      Method m = clazz.getMethod("singleVersionWithExplicitVersions", Map.class, int.class);
      return (Module) m.invoke(null, singleVersions, 0);
    } catch (ClassNotFoundException
        | SecurityException
        | NoSuchMethodException
        | IllegalArgumentException
        | IllegalAccessException
        | InvocationTargetException e) {
      e.printStackTrace();
      ProvisionException pe = new ProvisionException(e.getMessage());
      pe.initCause(e);
      throw pe;
    }
  }
}
