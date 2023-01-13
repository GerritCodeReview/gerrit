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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.FieldType;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.PaginationType;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.Schema.Values;
import com.google.gerrit.index.SchemaFieldDefs;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.ListResultSet;
import com.google.gerrit.index.query.ResultSet;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.options.AutoFlush;
import com.google.gerrit.server.update.RetryHelper;
import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.function.Function;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/** Basic Lucene index implementation. */
public abstract class AbstractLuceneIndex<K, V> implements Index<K, V> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface FunctionThrows<T, R, E extends Exception> {
    R apply(T t) throws E;
  }

  public interface IndexManager {
    ListenableFuture<?> submit(FunctionThrows<IndexWriter, Long, IOException> writeTask);

    void deleteAll() throws IOException;

    IndexSearcher acquire() throws IOException;

    default void release(IndexSearcher searcher) throws IOException {}

    IndexWriter acquireSnapshotWriter() throws IOException;

    default void releaseSnaphotWriter(IndexWriter writer) throws IOException {}

    default void close() {}
  }

  static String sortFieldName(SchemaField<?, ?> f) {
    return f.getName() + "_SORT";
  }

  private final Schema<V> schema;
  private final SitePaths sitePaths;
  private final Directory dir;
  private final String name;
  private final ImmutableSet<String> skipFields;
  private final Function<V, K> valueToKeyFunction;
  private final IndexManager indexManager;

  AbstractLuceneIndex(
      Schema<V> schema,
      SitePaths sitePaths,
      Directory dir,
      String name,
      ImmutableSet<String> skipFields,
      String subIndex,
      GerritIndexWriterConfig writerConfig,
      SearcherFactory searcherFactory,
      AutoFlush autoFlush,
      Function<V, K> valueToKeyFunction,
      RetryHelper retryHelper)
      throws IOException {
    this.schema = schema;
    this.sitePaths = sitePaths;
    this.dir = dir;
    this.name = name;
    this.skipFields = skipFields;
    this.valueToKeyFunction = valueToKeyFunction;

    if (writerConfig.isShareableIndexEnabled()) {
      indexManager = new OpenCloseIndexManager(dir, writerConfig, searcherFactory, retryHelper);
    } else {
      indexManager =
          new ThreadedIndexManager(dir, name, subIndex, writerConfig, searcherFactory, autoFlush);
    }
  }

  @Override
  public void markReady(boolean ready) {
    IndexUtils.setReady(sitePaths, name, schema.getVersion(), ready);
  }

  @Override
  public void close() {
    indexManager.close();
    try {
      dir.close();
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("error closing Lucene directory");
    }
  }

  ListenableFuture<?> insert(Document doc) {
    return indexManager.submit(w -> w.addDocument(doc));
  }

  ListenableFuture<?> replace(Term term, Document doc) {
    return indexManager.submit(w -> w.updateDocument(term, doc));
  }

  ListenableFuture<?> delete(Term term) {
    return indexManager.submit(w -> w.deleteDocuments(term));
  }

  @Override
  public void deleteByValue(V value) {
    delete(valueToKeyFunction.apply(value));
  }

  @Override
  public void deleteAll() {
    try {
      indexManager.deleteAll();
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public int numDocs() {
    try {
      IndexSearcher searcher = acquire();
      try {
        return searcher.getIndexReader().numDocs();
      } finally {
        release(searcher);
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(e.getMessage());
      throw new StorageException(e);
    }
  }

  IndexSearcher acquire() throws IOException {
    return indexManager.acquire();
  }

  void release(IndexSearcher searcher) throws IOException {
    indexManager.release(searcher);
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
        doc.add(new IntPoint(name, intValue));
        if (store == Store.YES) {
          doc.add(new StoredField(name, intValue));
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
      boolean isProtoField = SchemaFieldDefs.isProtoField(values.getField());
      for (Object value : values.getValues()) {
        // Lucene stores protos as bytes
        doc.add(
            new StoredField(
                name, isProtoField ? Protos.toByteArray((MessageLite) value) : (byte[]) value));
      }
    } else {
      throw FieldType.badFieldType(type);
    }
  }

  private void addLongField(Document doc, String name, Store store, Long longValue) {
    doc.add(new LongPoint(name, longValue));
    if (store == Store.YES) {
      doc.add(new StoredField(name, longValue));
    }
  }

  protected FieldBundle toFieldBundle(Document doc) {
    ListMultimap<String, Object> rawFields = ArrayListMultimap.create();
    for (IndexableField field : doc.getFields()) {
      checkArgument(getSchema().hasField(field.name()), "Unrecognized field " + field.name());
      FieldType<?> type = getSchema().getSchemaField(field.name()).getType();
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
    return new FieldBundle(rawFields, /* storesIndexedFields= */ false);
  }

  private static Field.Store store(SchemaField<?, ?> f) {
    return f.isStored() ? Field.Store.YES : Field.Store.NO;
  }

  static int getLimitBasedOnPaginationType(QueryOptions opts, int pagesize) {
    return PaginationType.NONE == opts.config().paginationType() ? opts.limit() : pagesize;
  }

  @Override
  public Schema<V> getSchema() {
    return schema;
  }

  @Override
  public boolean snapshot(String id) throws IOException {
    IndexWriter indexWriter = indexManager.acquireSnapshotWriter();
    try {
      SnapshotDeletionPolicy snapshooter =
          (SnapshotDeletionPolicy) indexWriter.getConfig().getIndexDeletionPolicy();

      IndexCommit commit = snapshooter.snapshot();
      try {
        Path sourceDir = canonical(((FSDirectory) commit.getDirectory()).getDirectory());
        Path indexDir = canonical(sitePaths.index_dir);
        Path targetDir =
            indexDir.resolve("snapshots").resolve(id).resolve(indexDir.relativize(sourceDir));
        if (targetDir.toFile().exists()) {
          throw new FileAlreadyExistsException(targetDir.toString());
        }
        targetDir.toFile().mkdirs();
        for (String file : commit.getFileNames()) {
          Files.copy(sourceDir.resolve(file).toFile(), targetDir.resolve(file).toFile());
        }
      } finally {
        snapshooter.release(commit);
      }
    } finally {
      indexManager.releaseSnaphotWriter(indexWriter);
    }
    return true;
  }

  private static Path canonical(Path p) throws IOException {
    return p.toFile().getCanonicalFile().toPath();
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
      ScoreDoc scoreDoc = null;
      try {
        searcher = acquire();
        int realLimit =
            Ints.saturatedCast(
                (long) getLimitBasedOnPaginationType(opts, opts.pageSize()) + opts.start());
        TopFieldDocs docs =
            opts.searchAfter() != null
                ? searcher.searchAfter((ScoreDoc) opts.searchAfter(), query, realLimit, sort, false)
                : searcher.search(query, realLimit, sort);
        ImmutableList.Builder<T> b = ImmutableList.builderWithExpectedSize(docs.scoreDocs.length);
        for (int i = opts.start(); i < docs.scoreDocs.length; i++) {
          scoreDoc = docs.scoreDocs[i];
          Document doc = searcher.doc(scoreDoc.doc, opts.fields());
          T mapperResult = mapper.apply(doc);
          if (mapperResult != null) {
            b.add(mapperResult);
          }
        }
        ScoreDoc searchAfter = scoreDoc;
        return new ListResultSet<>(b.build()) {
          @Override
          public Object searchAfter() {
            return searchAfter;
          }
        };
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
