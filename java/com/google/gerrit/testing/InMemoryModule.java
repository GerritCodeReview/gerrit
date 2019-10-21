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

package com.google.gerrit.testing;

import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.Scopes.SINGLETON;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperationsImpl;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.gpg.GpgModule;
import com.google.gerrit.index.IndexType;
import com.google.gerrit.index.SchemaDefinitions;
import com.google.gerrit.index.project.ProjectSchemaDefinitions;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.FanOutExecutor;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.api.GerritApiModule;
import com.google.gerrit.server.api.PluginApiModule;
import com.google.gerrit.server.audit.AuditModule;
import com.google.gerrit.server.cache.h2.H2CacheModule;
import com.google.gerrit.server.cache.mem.DefaultMemoryCacheModule;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.ChangeUpdateExecutor;
import com.google.gerrit.server.config.DefaultUrlFormatter;
import com.google.gerrit.server.config.GerritGlobalModule;
import com.google.gerrit.server.config.GerritInstanceNameModule;
import com.google.gerrit.server.config.GerritOptions;
import com.google.gerrit.server.config.GerritRuntime;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.config.GerritServerIdProvider;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.config.TrackingFootersProvider;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.PerThreadRequestScope;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.account.AccountSchemaDefinitions;
import com.google.gerrit.server.index.account.AllAccountsIndexer;
import com.google.gerrit.server.index.change.AllChangesIndexer;
import com.google.gerrit.server.index.change.ChangeSchemaDefinitions;
import com.google.gerrit.server.index.group.AllGroupsIndexer;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.gerrit.server.index.group.GroupSchemaDefinitions;
import com.google.gerrit.server.mail.SignedTokenEmailTokenVerifier;
import com.google.gerrit.server.patch.DiffExecutor;
import com.google.gerrit.server.permissions.DefaultPermissionBackendModule;
import com.google.gerrit.server.plugins.ServerInformationImpl;
import com.google.gerrit.server.project.DefaultProjectNameLockManager;
import com.google.gerrit.server.restapi.RestApiModule;
import com.google.gerrit.server.schema.JdbcAccountPatchReviewStore;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gerrit.server.schema.SchemaCreatorImpl;
import com.google.gerrit.server.securestore.DefaultSecureStore;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.ssh.NoSshKeyCache;
import com.google.gerrit.server.submit.LocalMergeSuperSetComputation;
import com.google.gerrit.server.util.ReplicaUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.util.Providers;
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
    cfg.setString(
        "accountPatchReviewDb", null, "url", JdbcAccountPatchReviewStore.TEST_IN_MEMORY_URL);
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

  public InMemoryModule() {
    this(newDefaultConfig());
  }

  public InMemoryModule(Config cfg) {
    this.cfg = cfg;
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
    bind(GerritRuntime.class).toInstance(GerritRuntime.DAEMON);
    bind(MetricMaker.class).to(DisabledMetricMaker.class);
    install(cfgInjector.getInstance(GerritGlobalModule.class));
    install(new GerritApiModule());
    factory(PluginUser.Factory.class);
    install(new PluginApiModule());
    install(new DefaultPermissionBackendModule());
    install(new SearchingChangeCacheImpl.Module());
    factory(GarbageCollection.Factory.class);
    install(new AuditModule());

    bindScope(RequestScoped.class, PerThreadRequestScope.REQUEST);

    // It would be nice to use Jimfs for the SitePath, but the biggest blocker is that JGit does not
    // support Path-based Configs, only FileBasedConfig.
    bind(Path.class).annotatedWith(SitePath.class).toInstance(Paths.get("."));
    bind(Config.class).annotatedWith(GerritServerConfig.class).toInstance(cfg);
    bind(GerritOptions.class).toInstance(new GerritOptions(false, false, false));

    bind(GitRepositoryManager.class).to(InMemoryRepositoryManager.class);
    bind(InMemoryRepositoryManager.class).in(SINGLETON);
    bind(TrackingFooters.class).toProvider(TrackingFootersProvider.class).in(SINGLETON);
    bind(ListeningExecutorService.class)
        .annotatedWith(ChangeUpdateExecutor.class)
        .toInstance(MoreExecutors.newDirectExecutorService());
    bind(SecureStore.class).to(DefaultSecureStore.class);

    install(new InMemorySchemaModule());
    install(NoSshKeyCache.module());
    install(new GerritInstanceNameModule());
    install(
        new CanonicalWebUrlModule() {
          @Override
          protected Class<? extends Provider<String>> provider() {
            return CanonicalWebUrlProvider.class;
          }
        });
    install(new DefaultUrlFormatter.Module());
    // Replacement of DiffExecutorModule to not use thread pool in the tests
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
    install(new DefaultMemoryCacheModule());
    install(new H2CacheModule());
    install(new FakeEmailSender.Module());
    install(new SignedTokenEmailTokenVerifier.Module());
    install(new GpgModule(cfg));
    install(new LocalMergeSuperSetComputation.Module());

    bind(AllAccountsIndexer.class).toProvider(Providers.of(null));
    bind(AllChangesIndexer.class).toProvider(Providers.of(null));
    bind(AllGroupsIndexer.class).toProvider(Providers.of(null));

    IndexType indexType = new IndexType(cfg.getString("index", null, "type"));
    // For custom index types, callers must provide their own module.
    if (indexType.isLucene()) {
      install(luceneIndexModule());
    } else if (indexType.isElasticsearch()) {
      install(elasticIndexModule());
    }
    bind(ServerInformationImpl.class);
    bind(ServerInformation.class).to(ServerInformationImpl.class);
    install(new RestApiModule());
    install(new DefaultProjectNameLockManager.Module());

    bind(ProjectOperations.class).to(ProjectOperationsImpl.class);
  }

  /** Copy of SchemaModule with a slightly different server ID provider. */
  // TODO(dborowitz): Better code sharing.
  private class InMemorySchemaModule extends FactoryModule {
    @Override
    public void configure() {
      bind(PersonIdent.class)
          .annotatedWith(GerritPersonIdent.class)
          .toProvider(GerritPersonIdentProvider.class);

      bind(AllProjectsName.class).toProvider(AllProjectsNameProvider.class).in(SINGLETON);

      bind(AllUsersName.class).toProvider(AllUsersNameProvider.class).in(SINGLETON);

      bind(String.class)
          .annotatedWith(AnonymousCowardName.class)
          .toProvider(AnonymousCowardNameProvider.class);

      bind(GroupIndexCollection.class);
      bind(SchemaCreator.class).to(SchemaCreatorImpl.class);
    }

    @Provides
    @Singleton
    @GerritServerId
    public String createServerId() {
      String serverId =
          cfg.getString(GerritServerIdProvider.SECTION, null, GerritServerIdProvider.KEY);
      if (!Strings.isNullOrEmpty(serverId)) {
        return serverId;
      }

      return "gerrit";
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
  @FanOutExecutor
  public ExecutorService createFanOutExecutor(WorkQueue queues) {
    return queues.createQueue(2, "FanOut");
  }

  private Module luceneIndexModule() {
    return indexModule("com.google.gerrit.lucene.LuceneIndexModule");
  }

  private Module elasticIndexModule() {
    return indexModule("com.google.gerrit.elasticsearch.ElasticIndexModule");
  }

  private Module indexModule(String moduleClassName) {
    try {
      Class<?> clazz = Class.forName(moduleClassName);
      Method m =
          clazz.getMethod("singleVersionWithExplicitVersions", Map.class, int.class, boolean.class);
      return (Module) m.invoke(null, getSingleSchemaVersions(), 0, ReplicaUtil.isReplica(cfg));
    } catch (ClassNotFoundException
        | SecurityException
        | NoSuchMethodException
        | IllegalArgumentException
        | IllegalAccessException
        | InvocationTargetException e) {
      e.printStackTrace();
      throw new ProvisionException(e.getMessage(), e);
    }
  }

  private Map<String, Integer> getSingleSchemaVersions() {
    Map<String, Integer> singleVersions = new HashMap<>();
    putSchemaVersion(singleVersions, AccountSchemaDefinitions.INSTANCE);
    putSchemaVersion(singleVersions, ChangeSchemaDefinitions.INSTANCE);
    putSchemaVersion(singleVersions, GroupSchemaDefinitions.INSTANCE);
    putSchemaVersion(singleVersions, ProjectSchemaDefinitions.INSTANCE);
    return singleVersions;
  }

  private void putSchemaVersion(
      Map<String, Integer> singleVersions, SchemaDefinitions<?> schemaDef) {
    String schemaName = schemaDef.getName();
    int version = cfg.getInt("index", "lucene", schemaName + "TestVersion", -1);
    if (version > 0) {
      checkState(
          !singleVersions.containsKey(schemaName),
          "version for schema %s was alreay set",
          schemaName);
      singleVersions.put(schemaName, version);
    }
  }
}
