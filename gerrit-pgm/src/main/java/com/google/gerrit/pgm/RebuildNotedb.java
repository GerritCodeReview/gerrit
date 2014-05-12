// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.util.BatchGitModule;
import com.google.gerrit.pgm.util.BatchProgramModule;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.pgm.util.ThreadLimiter;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.notedb.ChangeRebuilder;
import com.google.gerrit.server.notedb.NoteDbModule;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RebuildNotedb extends SiteProgram {
  private static final Logger log =
      LoggerFactory.getLogger(RebuildNotedb.class);

  @Option(name = "--threads", usage = "Number of threads to use for indexing")
  private int threads = Runtime.getRuntime().availableProcessors();

  private Injector dbInjector;
  private Injector sysInjector;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();
    dbInjector = createDbInjector(MULTI_USER);
    threads = ThreadLimiter.limitThreads(dbInjector, threads);

    LifecycleManager dbManager = new LifecycleManager();
    dbManager.add(dbInjector);
    dbManager.start();

    sysInjector = createSysInjector();
    LifecycleManager sysManager = new LifecycleManager();
    sysManager.add(sysInjector);
    sysManager.start();

    ListeningExecutorService executor = newExecutor();
    final MultiProgressMonitor mpm =
        new MultiProgressMonitor(System.out, "Rebuilding notedb");
    final Task doneTask =
        mpm.beginSubTask("changes", MultiProgressMonitor.UNKNOWN);
    final Task failedTask =
        mpm.beginSubTask("failed", MultiProgressMonitor.UNKNOWN);
    ChangeRebuilder rebuilder = sysInjector.getInstance(ChangeRebuilder.class);

    List<Change> allChanges = getAllChanges();
    final List<ListenableFuture<?>> futures = Lists.newArrayList();
    final AtomicBoolean ok = new AtomicBoolean(true);
    Stopwatch sw = Stopwatch.createStarted();
    for (final Change c : allChanges) {
      final ListenableFuture<?> future = rebuilder.rebuildAsync(c, executor);
      futures.add(future);
      future.addListener(new Runnable() {
        @Override
        public void run() {
          try {
            future.get();
            doneTask.update(1);
          } catch (ExecutionException | InterruptedException e) {
            fail(e);
          } catch (RuntimeException e) {
            failAndThrow(e);
          } catch (Error e) {
            // Can't join with RuntimeException because "RuntimeException |
            // Error" becomes Throwable, which messes with signatures.
            failAndThrow(e);
          }
        }

        private void fail(Throwable t) {
          log.error("Failed to rebuild change " + c.getId(), t);
          ok.set(false);
          failedTask.update(1);
        }

        private void failAndThrow(RuntimeException e) {
          fail(e);
          throw e;
        }

        private void failAndThrow(Error e) {
          fail(e);
          throw e;
        }
      }, MoreExecutors.sameThreadExecutor());
    }
    try {
      mpm.waitFor(Futures.transform(Futures.successfulAsList(futures),
          new AsyncFunction<List<?>, Void>() {
            @Override
            public ListenableFuture<Void> apply(List<?> input) {
              mpm.end();
              return Futures.immediateFuture(null);
            }
      }));
    } catch (ExecutionException e) {
      log.error("Error rebuilding notedb", e);
      ok.set(false);
    }
    double t = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
    System.out.format("Rebuild %d changes in %.01fs (%.01f/s)\n",
        allChanges.size(), t, allChanges.size() / t);
    return ok.get() ? 0 : 1;
  }

  private Injector createSysInjector() {
    return dbInjector.createChildInjector(new AbstractModule() {
      @Override
      public void configure() {
        install(dbInjector.getInstance(BatchProgramModule.class));
        install(new BatchGitModule());
        install(new NoteDbModule());
        bind(NotesMigration.class).toInstance(NotesMigration.allEnabled());
      }
    });
  }

  private ListeningExecutorService newExecutor() {
    if (threads > 0) {
      return MoreExecutors.listeningDecorator(
          dbInjector.getInstance(WorkQueue.class)
            .createQueue(threads, "RebuildChange"));
    } else {
      return MoreExecutors.sameThreadExecutor();
    }
  }

  private List<Change> getAllChanges() throws OrmException {
    // Memoize all changes to a list so we can close the db connection and allow
    // rebuilder threads to use the full connection pool.
    // TODO(dborowitz): May need to batch changes, e.g. by project (though note
    // that unlike Reindex, we don't think there is an inherent benefit to
    // grouping by project), to avoid wasting too much memory here.
    SchemaFactory<ReviewDb> schemaFactory = sysInjector.getInstance(Key.get(
        new TypeLiteral<SchemaFactory<ReviewDb>>() {}));
    ReviewDb db = schemaFactory.open();
    try {
      return db.changes().all().toList();
    } finally {
      db.close();
    }
  }
}
