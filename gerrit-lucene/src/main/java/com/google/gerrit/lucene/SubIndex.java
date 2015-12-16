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

package com.google.gerrit.lucene;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.lucene.LuceneChangeIndex.GerritIndexWriterConfig;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TrackingIndexWriter;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.ReferenceManager.RefreshListener;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Piece of the change index that is implemented as a separate Lucene index. */
public class SubIndex {
  private static final Logger log = LoggerFactory.getLogger(SubIndex.class);

  private final Directory dir;
  private final TrackingIndexWriter writer;
  private final ReferenceManager<IndexSearcher> searcherManager;
  private final ControlledRealTimeReopenThread<IndexSearcher> reopenThread;
  private final Set<NrtFuture> notDoneNrtFutures;

  SubIndex(Path path, GerritIndexWriterConfig writerConfig,
      SearcherFactory searcherFactory) throws IOException {
    this(FSDirectory.open(path), path.getFileName().toString(), writerConfig,
        searcherFactory);
  }

  SubIndex(Directory dir, final String dirName,
      GerritIndexWriterConfig writerConfig,
      SearcherFactory searcherFactory) throws IOException {
    this.dir = dir;
    IndexWriter delegateWriter;
    long commitPeriod = writerConfig.getCommitWithinMs();

    if (commitPeriod < 0) {
      delegateWriter = new AutoCommitWriter(dir, writerConfig.getLuceneConfig());
    } else if (commitPeriod == 0) {
      delegateWriter =
          new AutoCommitWriter(dir, writerConfig.getLuceneConfig(), true);
    } else {
      final AutoCommitWriter autoCommitWriter =
          new AutoCommitWriter(dir, writerConfig.getLuceneConfig());
      delegateWriter = autoCommitWriter;

      new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder()
          .setNameFormat("Commit-%d " + dirName)
          .setDaemon(true)
          .build())
          .scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
              try {
                if (autoCommitWriter.hasUncommittedChanges()) {
                  autoCommitWriter.manualFlush();
                  autoCommitWriter.commit();
                }
              } catch (IOException e) {
                log.error("Error committing Lucene index " + dirName, e);
              } catch (OutOfMemoryError e) {
                log.error("Error committing Lucene index " + dirName, e);
                try {
                  autoCommitWriter.close();
                } catch (IOException e2) {
                  log.error("SEVERE: Error closing Lucene index " + dirName
                      + " after OOM; index may be corrupted.", e);
                }
              }
            }
          }, commitPeriod, commitPeriod, MILLISECONDS);
    }
    writer = new TrackingIndexWriter(delegateWriter);
    searcherManager = new WrappableSearcherManager(
        writer.getIndexWriter(), true, searcherFactory);

    notDoneNrtFutures = Sets.newConcurrentHashSet();

    reopenThread = new ControlledRealTimeReopenThread<>(
        writer, searcherManager,
        0.500 /* maximum stale age (seconds) */,
        0.010 /* minimum stale age (seconds) */);
    reopenThread.setName("NRT " + dirName);
    reopenThread.setPriority(Math.min(
        Thread.currentThread().getPriority() + 2,
        Thread.MAX_PRIORITY));
    reopenThread.setDaemon(true);

    // This must be added after the reopen thread is created. The reopen thread
    // adds its own listener which copies its internally last-refreshed
    // generation to the searching generation. removeIfDone() depends on the
    // searching generation being up to date when calling
    // reopenThread.waitForGeneration(gen, 0), therefore the reopen thread's
    // internal listener needs to be called first.
    // TODO(dborowitz): This may have been fixed by
    // http://issues.apache.org/jira/browse/LUCENE-5461
    searcherManager.addListener(new RefreshListener() {
      @Override
      public void beforeRefresh() throws IOException {
      }

      @Override
      public void afterRefresh(boolean didRefresh) throws IOException {
        for (NrtFuture f : notDoneNrtFutures) {
          f.removeIfDone();
        }
      }
    });

    reopenThread.start();
  }

  void close() {
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
      log.warn("error finishing pending Lucene writes", e);
    }

    try {
      writer.getIndexWriter().close();
    } catch (AlreadyClosedException e) {
      // Ignore.
    } catch (IOException e) {
      log.warn("error closing Lucene writer", e);
    }
    try {
      dir.close();
    } catch (IOException e) {
      log.warn("error closing Lucene directory", e);
    }
  }

  ListenableFuture<?> insert(Document doc) throws IOException {
    return new NrtFuture(writer.addDocument(doc));
  }

  ListenableFuture<?> replace(Term term, Document doc) throws IOException {
    return new NrtFuture(writer.updateDocument(term, doc));
  }

  ListenableFuture<?> delete(Term term) throws IOException {
    return new NrtFuture(writer.deleteDocuments(term));
  }

  void deleteAll() throws IOException {
    writer.deleteAll();
  }

  public TrackingIndexWriter getWriter() {
    return writer;
  }

  IndexSearcher acquire() throws IOException {
    return searcherManager.acquire();
  }

  void release(IndexSearcher searcher) throws IOException {
    searcherManager.release(searcher);
  }

  private final class NrtFuture extends AbstractFuture<Void> {
    private final long gen;

    NrtFuture(long gen) {
      this.gen = gen;
      // Tell the reopen thread we are waiting on this generation so it uses the
      // min stale time when refreshing.
      isGenAvailableNowForCurrentSearcher();
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
    public Void get(long timeout, TimeUnit unit) throws InterruptedException,
        TimeoutException, ExecutionException {
      if (!isDone()) {
        if (reopenThread.waitForGeneration(gen,
            (int) MILLISECONDS.convert(timeout, unit))) {
          set(null);
        } else {
          throw new TimeoutException();
        }
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
      try {
        return reopenThread.waitForGeneration(gen, 0);
      } catch (InterruptedException e) {
        log.warn("Interrupted waiting for searcher generation", e);
        return false;
      }
    }
  }
}
