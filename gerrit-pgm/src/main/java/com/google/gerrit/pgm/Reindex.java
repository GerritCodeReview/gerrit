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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.pgm;

import static com.google.gerrit.lucene.IndexVersionCheck.SCHEMA_VERSIONS;
import static com.google.gerrit.lucene.IndexVersionCheck.gerritIndexConfig;
import static com.google.gerrit.lucene.LuceneChangeIndex.LUCENE_VERSION;
import static com.google.gerrit.server.schema.DataSourceProvider.Context.SINGLE_USER;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.lucene.LuceneIndexModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheRemovalListener;
import com.google.gerrit.server.cache.h2.DefaultCacheFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.patch.PatchListCacheImpl;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Reindex extends SiteProgram {
  private static final Logger log = LoggerFactory.getLogger(Reindex.class);

  @Option(name = "--threads", usage = "Number of threads to use for indexing")
  private int threads = Runtime.getRuntime().availableProcessors();

  @Option(name = "--dry-run", usage = "Dry run: don't write anything to index")
  private boolean dryRun;

  private Injector dbInjector;
  private Injector sysInjector;
  private SitePaths sitePaths;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector(SINGLE_USER);
    if (!IndexModule.isEnabled(dbInjector)) {
      throw die("Secondary index not enabled");
    }
    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();
    sitePaths = dbInjector.getInstance(SitePaths.class);

    // Delete before any LuceneChangeIndex may be created.
    deleteAll();

    sysInjector = createSysInjector();
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    int result = indexAll();
    writeVersion();

    sysManager.stop();
    dbManager.stop();
    return result;
  }

  private Injector createSysInjector() {
    List<Module> modules = Lists.newArrayList();
    modules.add(PatchListCacheImpl.module());
    modules.add(new LuceneIndexModule(false, threads, dryRun));
    modules.add(new ReviewDbModule());
    modules.add(new AbstractModule() {
      @SuppressWarnings("rawtypes")
      @Override
      protected void configure() {
        // Plugins are not loaded and we're just running through each change
        // once, so don't worry about cache removal.
        bind(new TypeLiteral<DynamicSet<CacheRemovalListener>>() {})
            .toInstance(DynamicSet.<CacheRemovalListener> emptySet());
        install(new DefaultCacheFactory.Module());
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

  private void deleteAll() throws IOException {
    if (dryRun) {
      return;
    }
    for (String index : SCHEMA_VERSIONS.keySet()) {
      File file = new File(sitePaths.index_dir, index);
      if (file.exists()) {
        Directory dir = FSDirectory.open(file);
        try {
          for (String name : dir.listAll()) {
            dir.deleteFile(name);
          }
        } finally {
          dir.close();
        }
      }
    }
  }

  private int indexAll() throws Exception {
    ReviewDb db = sysInjector.getInstance(ReviewDb.class);
    ChangeIndexer indexer = sysInjector.getInstance(ChangeIndexer.class);
    Stopwatch sw = new Stopwatch().start();
    final int queueLen = 2 * threads;
    final Semaphore sem = new Semaphore(queueLen);
    final AtomicBoolean ok = new AtomicBoolean(true);

    final MultiProgressMonitor pm =
        new MultiProgressMonitor(System.out, "Reindexing changes");
    final Task done = pm.beginSubTask(null, MultiProgressMonitor.UNKNOWN);
    final Task failed = pm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);

    int i = 0;
    for (final Change change : db.changes().all()) {
      sem.acquire();
      final ListenableFuture<?> future = indexer.index(change);
      future.addListener(new Runnable() {
        @Override
        public void run() {
          try {
            future.get();
            done.update(1);
          } catch (InterruptedException e) {
            fail(change, e);
          } catch (ExecutionException e) {
            fail(change, e);
          } catch (RuntimeException e) {
            failAndThrow(change, e);
          } catch (Error e) {
            failAndThrow(change, e);
          } finally {
            sem.release();
          }
        }

        private void fail(Change change, Throwable t) {
          ok.set(false);
          failed.update(1);
        }

        private void failAndThrow(Change change, RuntimeException e) {
          fail(change, e);
          throw e;
        }

        private void failAndThrow(Change change, Error e) {
          fail(change, e);
          throw e;
        }
      }, MoreExecutors.sameThreadExecutor());
      i++;
    }

    pm.waitFor(sysInjector.getInstance(WorkQueue.class).getDefaultQueue()
        .submit(new Runnable() {
          @Override
          public void run() {
            try {
              sem.acquire(queueLen);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
            pm.end();
          }
        }));
    double elapsed = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
    System.out.format("Reindexed %d changes in %.02fs (%.01f/s)\n",
        i, elapsed, i/elapsed);

    return ok.get() ? 0 : 1;
  }

  private void writeVersion() throws IOException,
      ConfigInvalidException {
    if (dryRun) {
      return;
    }
    FileBasedConfig cfg =
        new FileBasedConfig(gerritIndexConfig(sitePaths), FS.detect());
    cfg.load();

    for (Map.Entry<String, Integer> e : SCHEMA_VERSIONS.entrySet()) {
      cfg.setInt("index", e.getKey(), "schemaVersion", e.getValue());
    }
    cfg.setEnum("lucene", null, "version", LUCENE_VERSION);
    cfg.save();
  }
}
