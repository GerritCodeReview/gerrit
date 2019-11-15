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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.FieldType;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.Schema.Values;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.ListResultSet;
import com.google.gerrit.index.query.ResultSet;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.logging.LoggingContextAwareExecutorService;
import com.google.gerrit.server.logging.LoggingContextAwareScheduledExecutorService;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LegacyIntField;
import org.apache.lucene.document.LegacyLongField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ReferenceManager;
import org.apache.lucene.search.ReferenceManager.RefreshListener;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;

/** Basic Lucene index implementation. */
@SuppressWarnings("deprecation")
public abstract class AbstractLuceneIndex<K, V> implements Index<K, V> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static String sortFieldName(FieldDef<?, ?> f) {
    return f.getName() + "_SORT";
  }

  private final Schema<V> schema;
  private final SitePaths sitePaths;
  private final Directory dir;
  private final String name;
  private final ImmutableSet<String> skipFields;
  private final ListeningExecutorService writerThread;
  private final IndexWriter writer;
  private final ReferenceManager<IndexSearcher> searcherManager;
  private final ControlledRealTimeReopenThread<IndexSearcher> reopenThread;
  private final Set<NrtFuture> notDoneNrtFutures;
  private ScheduledExecutorService autoCommitExecutor;

  AbstractLuceneIndex(
      Schema<V> schema,
      SitePaths sitePaths,
      Directory dir,
      String name,
      ImmutableSet<String> skipFields,
      String subIndex,
      GerritIndexWriterConfig writerConfig,
      SearcherFactory searcherFactory)
      throws IOException {
    this.schema = schema;
    this.sitePaths = sitePaths;
    this.dir = dir;
    this.name = name;
    this.skipFields = skipFields;
    String index = Joiner.on('_').skipNulls().join(name, subIndex);
    long commitPeriod = writerConfig.getCommitWithinMs();

    if (commitPeriod < 0) {
      writer = new AutoCommitWriter(dir, writerConfig.getLuceneConfig());
    } else if (commitPeriod == 0) {
      writer = new AutoCommitWriter(dir, writerConfig.getLuceneConfig(), true);
    } else {
      final AutoCommitWriter autoCommitWriter =
          new AutoCommitWriter(dir, writerConfig.getLuceneConfig());
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

    reopenThread.start();
  }

  @Override
  public void markReady(boolean ready) {
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
    try {
      dir.close();
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("error closing Lucene directory");
    }
  }

  ListenableFuture<?> insert(Document doc) {
    return submit(() -> writer.addDocument(doc));
  }

  ListenableFuture<?> replace(Term term, Document doc) {
    return submit(() -> writer.updateDocument(term, doc));
  }

  ListenableFuture<?> delete(Term term) {
    return submit(() -> writer.deleteDocuments(term));
  }

  private ListenableFuture<?> submit(Callable<Long> task) {
    ListenableFuture<Long> future = Futures.nonCancellationPropagating(writerThread.submit(task));
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
  public void deleteAll() {
    try {
      writer.deleteAll();
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public IndexWriter getWriter() {
    return writer;
  }

  IndexSearcher acquire() throws IOException {
    return searcherManager.acquire();
  }

  void release(IndexSearcher searcher) throws IOException {
    searcherManager.release(searcher);
  }

  Document toDocument(V obj) {
    Document result = new Document();
    for (Values<V> vs : schema.buildFields(obj, skipFields)) {
      if (vs.getValues() != null) {
        add(result, vs);
      }
    }
    return result;
  }

  /**
   * Trasform an index document into a target object type.
   *
   * @param doc index document
   * @return target object, or null if the target object was not found or failed to load from the
   *     underlying store.
   */
  @Nullable
  protected abstract V fromDocument(Document doc);

  void add(Document doc, Values<V> values) {
    String name = values.getField().getName();
    FieldType<?> type = values.getField().getType();
    Store store = store(values.getField());

    if (type == FieldType.INTEGER || type == FieldType.INTEGER_RANGE) {
      for (Object value : values.getValues()) {
        Integer intValue = (Integer) value;
        if (schema.useLegacyNumericFields()) {
          doc.add(new LegacyIntField(name, intValue, store));
        } else {
          doc.add(new IntPoint(name, intValue));
          if (store == Store.YES) {
            doc.add(new StoredField(name, intValue));
          }
        }
      }
    } else if (type == FieldType.LONG) {
      for (Object value : values.getValues()) {
        addLongField(doc, name, store, (Long) value);
      }
    } else if (type == FieldType.TIMESTAMP) {
      for (Object value : values.getValues()) {
        addLongField(doc, name, store, ((Timestamp) value).getTime());
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

  private void addLongField(Document doc, String name, Store store, Long longValue) {
    if (schema.useLegacyNumericFields()) {
      doc.add(new LegacyLongField(name, longValue, store));
    } else {
      doc.add(new LongPoint(name, longValue));
      if (store == Store.YES) {
        doc.add(new StoredField(name, longValue));
      }
    }
  }

  protected FieldBundle toFieldBundle(Document doc) {
    Map<String, FieldDef<V, ?>> allFields = getSchema().getFields();
    ListMultimap<String, Object> rawFields = ArrayListMultimap.create();
    for (IndexableField field : doc.getFields()) {
      checkArgument(allFields.containsKey(field.name()), "Unrecognized field " + field.name());
      FieldType<?> type = allFields.get(field.name()).getType();
      if (type == FieldType.EXACT || type == FieldType.FULL_TEXT || type == FieldType.PREFIX) {
        rawFields.put(field.name(), field.stringValue());
      } else if (type == FieldType.INTEGER || type == FieldType.INTEGER_RANGE) {
        rawFields.put(field.name(), field.numericValue().intValue());
      } else if (type == FieldType.LONG) {
        rawFields.put(field.name(), field.numericValue().longValue());
      } else if (type == FieldType.TIMESTAMP) {
        rawFields.put(field.name(), new Timestamp(field.numericValue().longValue()));
      } else if (type == FieldType.STORED_ONLY) {
        rawFields.put(field.name(), field.binaryValue().bytes);
      } else {
        throw FieldType.badFieldType(type);
      }
    }
    return new FieldBundle(rawFields);
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
        logger.atWarning().withCause(e).log("Interrupted waiting for searcher generation");
        return false;
      }
    }
  }

  @Override
  public Schema<V> getSchema() {
    return schema;
  }

  protected class LuceneQuerySource implements DataSource<V> {
    private final QueryOptions opts;
    private final Query query;
    private final Sort sort;

    LuceneQuerySource(QueryOptions opts, Query query, Sort sort) {
      this.opts = opts;
      this.query = query;
      this.sort = sort;
    }

    @Override
    public int getCardinality() {
      return 10;
    }

    @Override
    public ResultSet<V> read() {
      return readImpl(AbstractLuceneIndex.this::fromDocument);
    }

    @Override
    public ResultSet<FieldBundle> readRaw() {
      return readImpl(AbstractLuceneIndex.this::toFieldBundle);
    }

    private <T> ResultSet<T> readImpl(Function<Document, T> mapper) {
      IndexSearcher searcher = null;
      try {
        searcher = acquire();
        int realLimit = opts.start() + opts.limit();
        TopFieldDocs docs = searcher.search(query, realLimit, sort);
        ImmutableList.Builder<T> b = ImmutableList.builderWithExpectedSize(docs.scoreDocs.length);
        for (int i = opts.start(); i < docs.scoreDocs.length; i++) {
          ScoreDoc sd = docs.scoreDocs[i];
          Document doc = searcher.doc(sd.doc, opts.fields());
          T mapperResult = mapper.apply(doc);
          if (mapperResult != null) {
            b.add(mapperResult);
          }
        }
        return new ListResultSet<>(b.build());
      } catch (IOException e) {
        throw new StorageException(e);
      } finally {
        if (searcher != null) {
          try {
            release(searcher);
          } catch (IOException e) {
            logger.atWarning().withCause(e).log("cannot release Lucene searcher");
          }
        }
      }
    }
  }
}
