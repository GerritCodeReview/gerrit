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
import com.google.gerrit.common.errors.EmailException;
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
import com.google.gerrit.server.PluginUser;
import com.google.gerrit.server.account.AccountByEmailCacheImpl;
import com.google.gerrit.server.account.AccountCacheImpl;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.EmailExpander;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.GroupCacheImpl;
import com.google.gerrit.server.account.GroupControl;
import com.google.gerrit.server.account.GroupIncludeCacheImpl;
import com.google.gerrit.server.account.IncludingGroupMembership;
import com.google.gerrit.server.account.InternalGroupBackend;
import com.google.gerrit.server.account.UniversalGroupBackend;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.config.AuthModule;
import com.google.gerrit.server.config.CanonicalWebUrlModule;
import com.google.gerrit.server.config.CanonicalWebUrlProvider;
import com.google.gerrit.server.config.EmailExpanderProvider;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.index.ChangeBatchIndexer;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.index.IndexModule.IndexType;
import com.google.gerrit.server.index.NoIndexModule;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.mail.EmailHeader;
import com.google.gerrit.server.mail.EmailSender;
import com.google.gerrit.server.mail.SignedTokenEmailTokenVerifier;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gerrit.server.plugins.PluginGuiceEnvironment;
import com.google.gerrit.server.plugins.PluginModule;
import com.google.gerrit.server.project.AccessControlModule;
import com.google.gerrit.server.project.CommentLinkInfo;
import com.google.gerrit.server.project.CommentLinkProvider;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SectionSortCache;
import com.google.gerrit.server.ssh.NoSshKeyCache;
import com.google.gerrit.solr.SolrIndexModule;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.kohsuke.args4j.Option;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Reindex extends SiteProgram {
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
    if (IndexModule.getIndexType(dbInjector) == IndexType.SQL) {
      throw die("index.type must be configured (or not SQL)");
    }
    if (version == null) {
      version = ChangeSchemas.getLatest().getVersion();
    }
    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();

    sysInjector.getInstance(PluginGuiceEnvironment.class)
        .setCfgInjector(dbInjector);

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

  private Injector createSysInjector() {
    return dbInjector.createChildInjector(new GlobalModule());
  }

  /**
   * Subset of GerritGlobalModule including just bindings needed for indexing.
   *
   * Does <b>not</b> include thread local request scoping behavior of ReviewDbs;
   * uses a custom {@link ReviewDbModule} instead.
   */
  private class GlobalModule extends FactoryModule {
    @SuppressWarnings("rawtypes")
    @Override
    protected void configure() {
      install(PatchListCacheImpl.module());
      install(dbInjector.getInstance(AuthModule.class));
      install(AccountByEmailCacheImpl.module());
      install(AccountCacheImpl.module());
      install(new ReviewDbModule());
      install(ProjectCacheImpl.module());
      install(new PluginModule());
      install(new PrologModule());
      install(new DefaultCacheFactory.Module());
      install(NoSshKeyCache.module());
      install(new SignedTokenEmailTokenVerifier.Module());
      install(GroupCacheImpl.module());
      install(new AccessControlModule());
      install(SectionSortCache.module());
      install(new CanonicalWebUrlModule() {
        @Override
        protected Class<? extends Provider<String>> provider() {
          return CanonicalWebUrlProvider.class;
        }
      });

      switch (IndexModule.getIndexType(dbInjector)) {
        case LUCENE:
          install(new LuceneIndexModule(version, threads, outputBase));
          break;
        case SOLR:
          install(new SolrIndexModule(false, threads, outputBase));
          break;
        default:
          install(new NoIndexModule());
      }

      // No need to support live plugin removal or other live updates of
      // caches behind our back, so don't worry about cache removal.
      bind(new TypeLiteral<DynamicSet<CacheRemovalListener>>() {})
          .toInstance(DynamicSet.<CacheRemovalListener> emptySet());

      // Assume the current user is always the Gerrit internal user; no
      // indexed data should depend on what user is doing the indexing.
      bind(CurrentUser.class).to(InternalUser.class);

      bind(ChangeHooks.class).to(DisabledChangeHooks.class);
      bind(EmailExpander.class).toProvider(EmailExpanderProvider.class).in(
          SINGLETON);
      bind(new TypeLiteral<List<CommentLinkInfo>>() {})
          .toProvider(CommentLinkProvider.class).in(SINGLETON);
      bind(ProjectControl.GenericFactory.class);

      factory(CapabilityControl.Factory.class);
      factory(IncludingGroupMembership.Factory.class);
      factory(InternalUser.Factory.class);
      factory(PluginUser.Factory.class);
      factory(ProjectState.Factory.class);

      bind(GroupBackend.class).to(UniversalGroupBackend.class).in(SINGLETON);
      install(GroupIncludeCacheImpl.module());
      bind(GroupControl.Factory.class).in(SINGLETON);
      bind(GroupControl.GenericFactory.class).in(SINGLETON);
      bind(InternalGroupBackend.class).in(SINGLETON);
      DynamicSet.setOf(binder(), GroupBackend.class);
      DynamicSet.bind(binder(), GroupBackend.class).to(InternalGroupBackend.class);

      bind(EmailSender.class).toInstance(new EmailSender() {
        @Override
        public boolean isEnabled() {
          return false;
        }

        @Override
        public boolean canEmail(String address) {
          return false;
        }

        @Override
        public void send(Address from, Collection<Address> rcpt,
            Map<String, EmailHeader> headers, String body)
            throws EmailException {
        }
      });
    }
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
