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

package com.google.gerrit.pgm;

import static com.google.gerrit.server.schema.DataSourceProvider.Context.MULTI_USER;
import static com.google.inject.Scopes.SINGLETON;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.DisabledChangeHooks;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.rules.PrologModule;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.account.AccountByEmailCacheImpl;
import com.google.gerrit.server.account.AccountCacheImpl;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.account.GroupIncludeCacheImpl;
import com.google.gerrit.server.account.IncludingGroupMembership;
import com.google.gerrit.server.account.InternalGroupBackend;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.change.MergeabilityChecksExecutor;
import com.google.gerrit.server.change.PatchSetInserter;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitModule;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.index.ChangeBatchIndexer;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.project.AccessControlModule;
import com.google.gerrit.server.project.CommentLinkInfo;
import com.google.gerrit.server.project.CommentLinkProvider;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SectionSortCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.schema.DataSourceType;
import com.google.gerrit.solr.SolrIndexModule;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Providers;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Reindex extends SiteProgram {
  private static final Logger log = LoggerFactory.getLogger(Reindex.class);

  @Option(name = "--threads", usage = "Number of threads to use for indexing")
  private int threads = Runtime.getRuntime().availableProcessors();

  @Option(name = "--schema-version",
      usage = "Schema version to reindex; default is most recent version")
  private Integer version;

  @Option(name = "--output", usage = "Prefix for output; path for local disk index, or prefix for remote index")
  private String outputBase;

  @Option(name = "--verbose", usage = "Output debug information for each change")
  private boolean verbose;

  @Option(name = "--dry-run", usage = "Dry run: don't write anything to index")
  private boolean dryRun;

  private Injector dbInjector;
  private Injector sysInjector;
  private ChangeIndex index;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector(MULTI_USER);
    limitThreads();
    if (version == null) {
      version = ChangeSchemas.getLatest().getVersion();
    }
    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    index = sysInjector.getInstance(IndexCollection.class).getSearchIndex();
    index.markReady(false);
    index.deleteAll();
    int result = indexAll();
    index.markReady(true);

    sysManager.stop();
    dbManager.stop();
    return result;
  }

  private void limitThreads() {
    Config cfg =
        dbInjector.getInstance(Key.get(Config.class, GerritServerConfig.class));
    boolean usePool = cfg.getBoolean("database", "connectionpool",
        dbInjector.getInstance(DataSourceType.class).usePool());
    int poolLimit = cfg.getInt("database", "poollimit",
        DataSourceProvider.DEFAULT_POOL_LIMIT);
    if (usePool && threads > poolLimit) {
      log.warn("Limiting reindexing to " + poolLimit
          + " threads due to database.poolLimit");
      threads = poolLimit;
    }
  }

  private Injector createSysInjector() {
    List<Module> modules = Lists.newArrayList();
    modules.add(PatchListCacheImpl.module());
    AbstractModule changeIndexModule;
    switch (IndexModule.getIndexType(dbInjector)) {
      case LUCENE:
        changeIndexModule = new LuceneIndexModule(version, threads, outputBase);
        break;
      case SOLR:
        changeIndexModule = new SolrIndexModule(false, threads, outputBase);
        break;
      default:
        throw new IllegalStateException("unsupported index.type");
    }
    modules.add(changeIndexModule);
    modules.add(new ReviewDbModule());
    modules.add(new FactoryModule() {
      @SuppressWarnings("rawtypes")
      @Override
      protected void configure() {
        // Plugins are not loaded and we're just running through each change
        // once, so don't worry about cache removal.
        bind(new TypeLiteral<DynamicSet<CacheRemovalListener>>() {})
            .toInstance(DynamicSet.<CacheRemovalListener> emptySet());
        install(new DefaultCacheFactory.Module());
        factory(ChangeData.Factory.class);

        factory(ProjectState.Factory.class);
        bind(new TypeLiteral<List<CommentLinkInfo>>() {})
            .toProvider(CommentLinkProvider.class).in(SINGLETON);
        bind(CurrentUser.class).toProvider(Providers.<CurrentUser>of(null));
        bind(String.class).annotatedWith(CanonicalWebUrl.class)
            .toProvider(CanonicalWebUrlProvider.class);

        factory(IncludingGroupMembership.Factory.class);
        bind(GroupBackend.class).to(UniversalGroupBackend.class).in(SINGLETON);
        DynamicSet.setOf(binder(), GroupBackend.class);
        bind(InternalGroupBackend.class).in(SINGLETON);
        DynamicSet.bind(binder(), GroupBackend.class).to(SystemGroupBackend.class);
        DynamicSet.bind(binder(), GroupBackend.class).to(InternalGroupBackend.class);
        factory(InternalUser.Factory.class);

        factory(PatchSetInserter.Factory.class);
        bind(ChangeHooks.class).to(DisabledChangeHooks.class);
        bind(ReplacePatchSetSender.Factory.class).toProvider(
            Providers.<ReplacePatchSetSender.Factory>of(null));

        factory(CapabilityControl.Factory.class);
        factory(MergeUtil.Factory.class);
        DynamicSet.setOf(binder(), GitReferenceUpdatedListener.class);
        DynamicSet.setOf(binder(), CommitValidationListener.class);
        factory(CommitValidators.Factory.class);
      }
    });

    modules.add(AccountCacheImpl.module());
    modules.add(AccountByEmailCacheImpl.module());
    modules.add(ChangeKindCache.module());
    modules.add(GroupCacheImpl.module());
    modules.add(GroupIncludeCacheImpl.module());
    modules.add(ProjectCacheImpl.module());
    modules.add(SectionSortCache.module());

    modules.add(new AccessControlModule());
    modules.add(new GitModule());
    modules.add(new PrologModule());
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
      }

      @Provides
      @Singleton
      @MergeabilityChecksExecutor
      public WorkQueue.Executor createMergeabilityChecksExecutor(
          WorkQueue queues) {
        return queues.createQueue(1, "MergeabilityChecks");
      }
    });

    return dbInjector.createChildInjector(modules);
  }

  private class ReviewDbModule extends LifecycleModule {
    @Override
    protected void configure() {
      final SchemaFactory<ReviewDb> schema = dbInjector.getInstance(
          Key.get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}));
      final List<ReviewDb> dbs = Collections.synchronizedList(
          Lists.<ReviewDb> newArrayListWithCapacity(threads + 1));
      final ThreadLocal<ReviewDb> localDb = new ThreadLocal<ReviewDb>();

      bind(ReviewDb.class).toProvider(new Provider<ReviewDb>() {
        @Override
        public ReviewDb get() {
          ReviewDb db = localDb.get();
          if (db == null) {
            try {
              db = schema.open();
              dbs.add(db);
              localDb.set(db);
            } catch (OrmException e) {
              throw new ProvisionException("unable to open ReviewDb", e);
            }
          }
          return db;
        }
      });
      listener().toInstance(new LifecycleListener() {
        @Override
        public void start() {
          // Do nothing.
        }

        @Override
        public void stop() {
          for (ReviewDb db : dbs) {
            db.close();
          }
        }
      });
    }
  }

  private int indexAll() throws Exception {
    ReviewDb db = sysInjector.getInstance(ReviewDb.class);
    ProgressMonitor pm = new TextProgressMonitor();
    pm.start(1);
    pm.beginTask("Collecting projects", ProgressMonitor.UNKNOWN);
    Set<Project.NameKey> projects = Sets.newTreeSet();
    int changeCount = 0;
    try {
      for (Change change : db.changes().all()) {
        changeCount++;
        if (projects.add(change.getProject())) {
          pm.update(1);
        }
      }
    } finally {
      db.close();
    }
    pm.endTask();

    ChangeBatchIndexer batchIndexer =
        sysInjector.getInstance(ChangeBatchIndexer.class);
    ChangeBatchIndexer.Result result = batchIndexer.indexAll(
      index, projects, projects.size(), changeCount, System.err,
      verbose ? System.out : NullOutputStream.INSTANCE);
    int n = result.doneCount() + result.failedCount();
    double t = result.elapsed(TimeUnit.MILLISECONDS) / 1000d;
    System.out.format("Reindexed %d changes in %.01fs (%.01f/s)\n", n, t, n/t);
    return result.success() ? 0 : 1;
  }
}
