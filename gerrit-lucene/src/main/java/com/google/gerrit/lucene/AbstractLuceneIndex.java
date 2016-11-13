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

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.index.IndexUtils;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.Schema.Values;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Basic Lucene index implementation. */
public abstract class AbstractLuceneIndex<K, V> implements Index<K, V> {
  private static final Logger log = LoggerFactory.getLogger(AbstractLuceneIndex.class);

  static String sortFieldName(FieldDef<?, ?> f) {
    return f.getName() + "_SORT";
  }

  private final Schema<V> schema;
  private final SitePaths sitePaths;
  private final Directory dir;
  private final String name;
  private final ListeningExecutorService writerThread;
  private final TrackingIndexWriter writer;
  private final ReferenceManager<IndexSearcher> searcherManager;
  private final ControlledRealTimeReopenThread<IndexSearcher> reopenThread;
  private final Set<NrtFuture> notDoneNrtFutures;
  private ScheduledThreadPoolExecutor autoCommitExecutor;

  AbstractLuceneIndex(
      Schema<V> schema,
      SitePaths sitePaths,
      Directory dir,
      String name,
      String subIndex,
      GerritIndexWriterConfig writerConfig,
      SearcherFactory searcherFactory)
      throws IOException {
    this.schema = schema;
    this.sitePaths = sitePaths;
    this.dir = dir;
    this.name = name;
    final String index = Joiner.on('_').skipNulls().join(name, subIndex);
    IndexWriter delegateWriter;
    long commitPeriod = writerConfig.getCommitWithinMs();

    if (commitPeriod < 0) {
      delegateWriter = new AutoCommitWriter(dir, writerConfig.getLuceneConfig());
    } else if (commitPeriod == 0) {
      delegateWriter = new AutoCommitWriter(dir, writerConfig.getLuceneConfig(), true);
    } else {
      final AutoCommitWriter autoCommitWriter =
          new AutoCommitWriter(dir, writerConfig.getLuceneConfig());
      delegateWriter = autoCommitWriter;

      autoCommitExecutor =
          new ScheduledThreadPoolExecutor(
              1,
              new ThreadFactoryBuilder()
                  .setNameFormat(index + " Commit-%d")
                  .setDaemon(true)
                  .build());
      autoCommitExecutor.scheduleAtFixedRate(
          new Runnable() {
            @Override
            public void run() {
              try {
                if (autoCommitWriter.hasUncommittedChanges()) {
                  autoCommitWriter.manualFlush();
                  autoCommitWriter.commit();
                }
              } catch (IOException e) {
                log.error("Error committing " + index + " Lucene index", e);
              } catch (OutOfMemoryError e) {
                log.error("Error committing " + index + " Lucene index", e);
                try {
                  autoCommitWriter.close();
                } catch (IOException e2) {
                  log.error(
                      "SEVERE: Error closing "
                          + index
                          + " Lucene index  after OOM; index may be corrupted.",
                      e);
                }
              }
            }
          },
          commitPeriod,
          commitPeriod,
          MILLISECONDS);
    }
    writer = new TrackingIndexWriter(delegateWriter);
    searcherManager = new WrappableSearcherManager(writer.getIndexWriter(), true, searcherFactory);

    notDoneNrtFutures = Sets.newConcurrentHashSet();

    writerThread =
        MoreExecutors.listeningDecorator(
            Executors.newFixedThreadPool(
                1,
                new ThreadFactoryBuilder()
                    .setNameFormat(index + " Write-%d")
                    .setDaemon(true)
                    .build()));

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

    reopenThread.start();
  }

  @Override
  public void markReady(boolean ready) throws IOException {
    IndexUtils.setReady(sitePaths, name, schema.getVersion(), ready);
  }

  @Override
  public void close() {
    if (autoCommitExecutor != null) {
      autoCommitExecutor.shutdown();
    }

    writerThread.shutdown();
    try {
      if (!writerThread.awaitTermination(5, TimeUnit.SECONDS)) {
        log.warn("shutting down " + name + " index with pending Lucene writes");
      }
    } catch (InterruptedException e) {
      log.warn("interrupted waiting for pending Lucene writes of " + name + " index", e);
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

  ListenableFuture<?> insert(final Document doc) {
    return submit(
        new Callable<Long>() {
          @Override
          public Long call() throws IOException, InterruptedException {
            return writer.addDocument(doc);
          }
        });
  }

  ListenableFuture<?> replace(final Term term, final Document doc) {
    return submit(
        new Callable<Long>() {
          @Override
          public Long call() throws IOException, InterruptedException {
            return writer.updateDocument(term, doc);
          }
        });
  }

  ListenableFuture<?> delete(final Term term) {
    return submit(
        new Callable<Long>() {
          @Override
          public Long call() throws IOException, InterruptedException {
            return writer.deleteDocuments(term);
          }
        });
  }

  private ListenableFuture<?> submit(Callable<Long> task) {
    ListenableFuture<Long> future = Futures.nonCancellationPropagating(writerThread.submit(task));
    return Futures.transformAsync(
        future,
        new AsyncFunction<Long, Void>() {
          @Override
          public ListenableFuture<Void> apply(Long gen) throws InterruptedException {
            // Tell the reopen thread a future is waiting on this
            // generation so it uses the min stale time when refreshing.
            reopenThread.waitForGeneration(gen, 0);
            return new NrtFuture(gen);
          }
        });
  }

  @Override
  public void deleteAll() throws IOException {
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

  Document toDocument(V obj, FillArgs fillArgs) {
    Document result = new Document();
    for (Values<V> vs : schema.buildFields(obj, fillArgs)) {
      if (vs.getValues() != null) {
        add(result, vs);
      }
    }
    return result;
  }

  void add(Document doc, Values<V> values) {
    String name = values.getField().getName();
    FieldType<?> type = values.getField().getType();
    Store store = store(values.getField());

    if (type == FieldType.INTEGER || type == FieldType.INTEGER_RANGE) {
      for (Object value : values.getValues()) {
        doc.add(new IntField(name, (Integer) value, store));
      }
    } else if (type == FieldType.LONG) {
      for (Object value : values.getValues()) {
        doc.add(new LongField(name, (Long) value, store));
      }
    } else if (type == FieldType.TIMESTAMP) {
      for (Object value : values.getValues()) {
        doc.add(new LongField(name, ((Timestamp) value).getTime(), store));
      }
    } else if (type == FieldType.EXACT || type == FieldType.PREFIX) {
      for (Object value : values.getValues()) {
        doc.add(new StringField(name, (String) value, store));
      }
    } else if (type == FieldType.FULL_TEXT) {
      for (Object value : values.getValues()) {
        doc.add(new TextField(name, (String) value, store));
      }
    } else if (type == FieldType.STORED_ONLY) {
      for (Object value : values.getValues()) {
        doc.add(new StoredField(name, (byte[]) value));
      }
    } else {
      throw FieldType.badFieldType(type);
    }
  }

  private static Field.Store store(FieldDef<?, ?> f) {
    return f.isStored() ? Field.Store.YES : Field.Store.NO;
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
      try {
        return reopenThread.waitForGeneration(gen, 0);
      } catch (InterruptedException e) {
        log.warn("Interrupted waiting for searcher generation", e);
        return false;
      }
    }
  }

  @Override
  public Schema<V> getSchema() {
    return schema;
  }
}
