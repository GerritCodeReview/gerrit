// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.lucene;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.lucene.AbstractLuceneIndex.FunctionThrows;
import com.google.gerrit.lucene.AbstractLuceneIndex.IndexManager;
import com.google.gerrit.server.index.options.AutoFlush;
import com.google.gerrit.server.logging.LoggingContextAwareExecutorService;
import com.google.gerrit.server.logging.LoggingContextAwareScheduledExecutorService;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.ReferenceManager.RefreshListener;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;

public class ThreadedIndexManager implements IndexManager {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String name;
  private final IndexWriter writer;
  private final ReferenceManager<IndexSearcher> searcherManager;
  private final Set<NrtFuture> notDoneNrtFutures;
  private final ListeningExecutorService writerThread;
  private final ControlledRealTimeReopenThread<IndexSearcher> reopenThread;
  private final AutoFlush autoFlush;
  private final IndexWriterConfig luceneConfig;
  private ScheduledExecutorService autoCommitExecutor;

  @SuppressWarnings("ThreadPriorityCheck")
  ThreadedIndexManager(
      Directory dir,
      String name,
      String subIndex,
      GerritIndexWriterConfig writerConfig,
      SearcherFactory searcherFactory,
      AutoFlush autoFlush)
      throws IOException {
    this.name = name;
    this.autoFlush = autoFlush;
    luceneConfig = writerConfig.getLuceneConfig();

    String index = Joiner.on('_').skipNulls().join(name, subIndex);
    long commitPeriod = writerConfig.getCommitWithinMs();

    luceneConfig.setIndexDeletionPolicy(
        new SnapshotDeletionPolicy(luceneConfig.getIndexDeletionPolicy()));

    if (commitPeriod < 0) {
      writer = new AutoCommitWriter(dir, luceneConfig);
    } else if (commitPeriod == 0) {
      writer = new AutoCommitWriter(dir, luceneConfig, true);
    } else {
      final AutoCommitWriter autoCommitWriter = new AutoCommitWriter(dir, luceneConfig);
      writer = autoCommitWriter;

      autoCommitExecutor =
          new LoggingContextAwareScheduledExecutorService(
              new ScheduledThreadPoolExecutor(
                  1,
                  new ThreadFactoryBuilder()
                      .setNameFormat(index + " Commit-%d")
                      .setDaemon(true)
                      .build()));
      @SuppressWarnings("unused") // Error handling within Runnable.
      Future<?> possiblyIgnoredError =
          autoCommitExecutor.scheduleAtFixedRate(
              () -> {
                try {
                  if (autoCommitWriter.hasUncommittedChanges()) {
                    autoCommitWriter.manualFlush();
                    autoCommitWriter.commit();
                  }
                } catch (IOException e) {
                  logger.atSevere().withCause(e).log("Error committing %s Lucene index", index);
                } catch (OutOfMemoryError e) {
                  logger.atSevere().withCause(e).log("Error committing %s Lucene index", index);
                  try {
                    autoCommitWriter.close();
                  } catch (IOException e2) {
                    logger.atSevere().withCause(e).log(
                        "SEVERE: Error closing %s Lucene index after OOM;"
                            + " index may be corrupted.",
                        index);
                  }
                }
              },
              commitPeriod,
              commitPeriod,
              MILLISECONDS);
    }
    searcherManager = new WrappableSearcherManager(writer, true, searcherFactory);

    notDoneNrtFutures = Sets.newConcurrentHashSet();

    writerThread =
        MoreExecutors.listeningDecorator(
            new LoggingContextAwareExecutorService(
                Executors.newFixedThreadPool(
                    1,
                    new ThreadFactoryBuilder()
                        .setNameFormat(index + " Write-%d")
                        .setDaemon(true)
                        .build())));

    reopenThread =
        new ControlledRealTimeReopenThread<>(
            writer,
            searcherManager,
            0.500 /* maximum stale age (seconds) */,
            0.010 /* minimum stale age (seconds) */);
    reopenThread.setName(index + " NRT");
    reopenThread.setPriority(
        Math.min(Thread.currentThread().getPriority() + 2, Thread.MAX_PRIORITY));
    reopenThread.setDaemon(true);

    // This must be added after the reopen thread is created. The reopen thread
    // adds its own listener which copies its internally last-refreshed
    // generation to the searching generation. removeIfDone() depends on the
    // searching generation being up to date when calling
    // reopenThread.waitForGeneration(gen, 0), therefore the reopen thread's
    // internal listener needs to be called first.
    // TODO(dborowitz): This may have been fixed by
    // http://issues.apache.org/jira/browse/LUCENE-5461
    searcherManager.addListener(
        new RefreshListener() {
          @Override
          public void beforeRefresh() throws IOException {}

          @Override
          public void afterRefresh(boolean didRefresh) throws IOException {
            for (NrtFuture f : notDoneNrtFutures) {
              f.removeIfDone();
            }
          }
        });

    if (autoFlush.equals(AutoFlush.ENABLED)) {
      reopenThread.start();
    }
  }

  @Override
  public ListenableFuture<?> submit(FunctionThrows<IndexWriter, Long, IOException> writeTask) {
    ListenableFuture<Long> future =
        Futures.nonCancellationPropagating(writerThread.submit(() -> writeTask.apply(writer)));
    return Futures.transformAsync(
        future,
        gen -> {
          // Tell the reopen thread a future is waiting on this
          // generation so it uses the min stale time when refreshing.
          reopenThread.waitForGeneration(gen, 0);
          return new NrtFuture(gen);
        },
        directExecutor());
  }

  @Override
  public void deleteAll() throws IOException {
    writer.deleteAll();
  }

  @Override
  public IndexWriter acquireSnapshotWriter() throws IOException {
    return writer;
  }

  @Override
  public IndexSearcher acquire() throws IOException {
    return searcherManager.acquire();
  }

  @Override
  public void release(IndexSearcher searcher) throws IOException {
    searcherManager.release(searcher);
  }

  @Override
  public void close() {
    if (autoCommitExecutor != null) {
      autoCommitExecutor.shutdown();
    }

    writerThread.shutdown();
    try {
      if (!writerThread.awaitTermination(5, TimeUnit.SECONDS)) {
        logger.atWarning().log("shutting down %s index with pending Lucene writes", name);
      }
    } catch (InterruptedException e) {
      logger.atWarning().withCause(e).log(
          "interrupted waiting for pending Lucene writes of %s index", name);
    }
    reopenThread.close();

    // Closing the reopen thread sets its generation to Long.MAX_VALUE, but we
    // still need to refresh the searcher manager to let pending NrtFutures
    // know.
    //
    // Any futures created after this method (which may happen due to undefined
    // shutdown ordering behavior) will finish immediately, even though they may
    // not have flushed.
    try {
      searcherManager.maybeRefreshBlocking();
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("error finishing pending Lucene writes");
    }

    try {
      writer.close();
    } catch (AlreadyClosedException e) {
      // Ignore.
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("error closing Lucene writer");
    }
  }

  private final class NrtFuture extends AbstractFuture<Void> {
    private final long gen;

    NrtFuture(long gen) {
      this.gen = gen;
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
      if (!isDone()) {
        reopenThread.waitForGeneration(gen);
        set(null);
      }
      return super.get();
    }

    @Override
    public Void get(long timeout, TimeUnit unit)
        throws InterruptedException, TimeoutException, ExecutionException {
      if (!isDone()) {
        if (!reopenThread.waitForGeneration(gen, (int) unit.toMillis(timeout))) {
          throw new TimeoutException();
        }
        set(null);
      }
      return super.get(timeout, unit);
    }

    @Override
    public boolean isDone() {
      if (super.isDone()) {
        return true;
      } else if (isGenAvailableNowForCurrentSearcher()) {
        set(null);
        return true;
      } else if (!reopenThread.isAlive()) {
        setException(new IllegalStateException("NRT thread is dead"));
        return true;
      }
      return false;
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
      if (isGenAvailableNowForCurrentSearcher() && !isCancelled()) {
        set(null);
      } else if (!isDone()) {
        notDoneNrtFutures.add(this);
      }
      super.addListener(listener, executor);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      boolean result = super.cancel(mayInterruptIfRunning);
      if (result) {
        notDoneNrtFutures.remove(this);
      }
      return result;
    }

    void removeIfDone() {
      if (isGenAvailableNowForCurrentSearcher()) {
        notDoneNrtFutures.remove(this);
        if (!isCancelled()) {
          set(null);
        }
      }
    }

    private boolean isGenAvailableNowForCurrentSearcher() {
      if (autoFlush.equals(AutoFlush.DISABLED)) {
        return true;
      }
      try {
        return reopenThread.waitForGeneration(gen, 0);
      } catch (InterruptedException e) {
        logger.atWarning().withCause(e).log("Interrupted waiting for searcher generation");
        return false;
      }
    }
  }
}
