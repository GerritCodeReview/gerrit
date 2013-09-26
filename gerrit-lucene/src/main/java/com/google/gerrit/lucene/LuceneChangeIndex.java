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

import static com.google.gerrit.server.index.IndexRewriteImpl.CLOSED_STATUSES;
import static com.google.gerrit.server.index.IndexRewriteImpl.OPEN_STATUSES;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeField.ChangeProtoField;
import com.google.gerrit.server.index.ChangeField.PatchSetApprovalProtoField;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.IndexRewriteImpl;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Secondary index implementation using Apache Lucene.
 * <p>
 * Writes are managed using a single {@link IndexWriter} per process, committed
 * aggressively. Reads use {@link SearcherManager} and periodically refresh,
 * though there may be some lag between a committed write and it showing up to
 * other threads' searchers.
 */
public class LuceneChangeIndex implements ChangeIndex {
  private static final Logger log =
      LoggerFactory.getLogger(LuceneChangeIndex.class);

  public static final Version LUCENE_VERSION = Version.LUCENE_43;
  public static final String CHANGES_OPEN = "open";
  public static final String CHANGES_CLOSED = "closed";
  private static final String ID_FIELD = ChangeField.LEGACY_ID.getName();
  private static final String CHANGE_FIELD = ChangeField.CHANGE.getName();
  private static final String APPROVAL_FIELD = ChangeField.APPROVAL.getName();

  static interface Factory {
    LuceneChangeIndex create(Schema<ChangeData> schema, String base);
  }

  private static IndexWriterConfig getIndexWriterConfig(Config cfg, String name) {
    IndexWriterConfig writerConfig = new IndexWriterConfig(LUCENE_VERSION,
        new StandardAnalyzer(LUCENE_VERSION, CharArraySet.EMPTY_SET));
    writerConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
    double m = 1 << 20;
    writerConfig.setRAMBufferSizeMB(cfg.getLong("index", name, "ramBufferSize",
          (long) (IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB * m)) / m);
    writerConfig.setMaxBufferedDocs(cfg.getInt("index", name, "maxBufferedDocs",
          IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS));
    return writerConfig;
  }

  private final SitePaths sitePaths;
  private final FillArgs fillArgs;
  private final ExecutorService executor;
  private final File dir;
  private final Schema<ChangeData> schema;
  private final SubIndex openIndex;
  private final SubIndex closedIndex;

  @AssistedInject
  LuceneChangeIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      @IndexExecutor ListeningScheduledExecutorService executor,
      FillArgs fillArgs,
      @Assisted Schema<ChangeData> schema,
      @Assisted @Nullable String base) throws IOException {
    this.sitePaths = sitePaths;
    this.fillArgs = fillArgs;
    this.executor = executor;
    this.schema = schema;

    if (base == null) {
      dir = LuceneVersionManager.getDir(sitePaths, schema);
    } else {
      dir = new File(base);
    }
    openIndex = new SubIndex(new File(dir, CHANGES_OPEN),
        getIndexWriterConfig(cfg, "changes_open"));
    closedIndex = new SubIndex(new File(dir, CHANGES_CLOSED),
        getIndexWriterConfig(cfg, "changes_closed"));
  }

  @Override
  public void close() {
    List<Future<?>> closeFutures = Lists.newArrayListWithCapacity(2);
    closeFutures.add(executor.submit(new Runnable() {
      @Override
      public void run() {
        openIndex.close();
      }
    }));
    closeFutures.add(executor.submit(new Runnable() {
      @Override
      public void run() {
        closedIndex.close();
      }
    }));
    for (Future<?> future : closeFutures) {
      Futures.getUnchecked(future);
    }
  }

  @Override
  public Schema<ChangeData> getSchema() {
    return schema;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ListenableFuture<Void> insert(ChangeData cd) throws IOException {
    Term id = QueryBuilder.idTerm(cd);
    Document doc = toDocument(cd);
    if (cd.getChange().getStatus().isOpen()) {
      return allOf(
          closedIndex.delete(id),
          openIndex.insert(doc));
    } else {
      return allOf(
          openIndex.delete(id),
          closedIndex.insert(doc));
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public ListenableFuture<Void> replace(ChangeData cd) throws IOException {
    Term id = QueryBuilder.idTerm(cd);
    Document doc = toDocument(cd);
    if (cd.getChange().getStatus().isOpen()) {
      return allOf(
          closedIndex.delete(id),
          openIndex.replace(id, doc));
    } else {
      return allOf(
          openIndex.delete(id),
          closedIndex.replace(id, doc));
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public ListenableFuture<Void> delete(ChangeData cd) throws IOException {
    Term id = QueryBuilder.idTerm(cd);
    return allOf(
        openIndex.delete(id),
        closedIndex.delete(id));
  }

  private static <V> ListenableFuture<Void> allOf(ListenableFuture<V>... f) {
    return Futures.transform(
        Futures.allAsList(f),
        new Function<List<V>, Void>() {
          @Override
          public Void apply(List<V> input) {
            return null;
          }
        });
  }

  @Override
  public void deleteAll() throws IOException {
    openIndex.deleteAll();
    closedIndex.deleteAll();
  }

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p, int limit)
      throws QueryParseException {
    Set<Change.Status> statuses = IndexRewriteImpl.getPossibleStatus(p);
    List<SubIndex> indexes = Lists.newArrayListWithCapacity(2);
    if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()) {
      indexes.add(openIndex);
    }
    if (!Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
      indexes.add(closedIndex);
    }
    return new QuerySource(indexes, QueryBuilder.toQuery(schema, p), limit,
        ChangeQueryBuilder.hasNonTrivialSortKeyAfter(schema, p));
  }

  @Override
  public void markReady(boolean ready) throws IOException {
    try {
      FileBasedConfig cfg = LuceneVersionManager.loadGerritIndexConfig(sitePaths);
      LuceneVersionManager.setReady(cfg, schema.getVersion(), ready);
      cfg.save();
    } catch (ConfigInvalidException e) {
      throw new IOException(e);
    }
  }

  private static class QuerySource implements ChangeDataSource {
    private static final ImmutableSet<String> FIELDS =
        ImmutableSet.of(ID_FIELD, CHANGE_FIELD, APPROVAL_FIELD);

    private final List<SubIndex> indexes;
    private final Query query;
    private final int limit;
    private final boolean reverse;

    private QuerySource(List<SubIndex> indexes, Query query, int limit,
        boolean reverse) {
      this.indexes = indexes;
      this.query = query;
      this.limit = limit;
      this.reverse = reverse;
    }

    @Override
    public int getCardinality() {
      return 10; // TODO(dborowitz): estimate from Lucene?
    }

    @Override
    public boolean hasChange() {
      return false;
    }

    @Override
    public String toString() {
      return query.toString();
    }

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      IndexSearcher[] searchers = new IndexSearcher[indexes.size()];
      Sort sort = new Sort(
          new SortField(
              ChangeField.SORTKEY.getName(),
              SortField.Type.LONG,
              // Standard order is descending by sort key, unless reversed due
              // to a sortkey_before predicate.
              !reverse));
      try {
        TopDocs[] hits = new TopDocs[indexes.size()];
        for (int i = 0; i < indexes.size(); i++) {
          searchers[i] = indexes.get(i).acquire();
          hits[i] = searchers[i].search(query, limit, sort);
        }
        TopDocs docs = TopDocs.merge(sort, limit, hits);

        List<ChangeData> result =
            Lists.newArrayListWithCapacity(docs.scoreDocs.length);
        for (ScoreDoc sd : docs.scoreDocs) {
          Document doc = searchers[sd.shardIndex].doc(sd.doc, FIELDS);
          result.add(toChangeData(doc));
        }

        final List<ChangeData> r = Collections.unmodifiableList(result);
        return new ResultSet<ChangeData>() {
          @Override
          public Iterator<ChangeData> iterator() {
            return r.iterator();
          }

          @Override
          public List<ChangeData> toList() {
            return r;
          }

          @Override
          public void close() {
            // Do nothing.
          }
        };
      } catch (IOException e) {
        throw new OrmException(e);
      } finally {
        for (int i = 0; i < indexes.size(); i++) {
          if (searchers[i] != null) {
            try {
              indexes.get(i).release(searchers[i]);
            } catch (IOException e) {
              log.warn("cannot release Lucene searcher", e);
            }
          }
        }
      }
    }
  }

  private static ChangeData toChangeData(Document doc) {
    BytesRef cb = doc.getBinaryValue(CHANGE_FIELD);
    if (cb == null) {
      int id = doc.getField(ID_FIELD).numericValue().intValue();
      return new ChangeData(new Change.Id(id));
    }

    Change change = ChangeProtoField.CODEC.decode(
        cb.bytes, cb.offset, cb.length);
    ChangeData cd = new ChangeData(change);

    BytesRef[] approvalsBytes = doc.getBinaryValues(APPROVAL_FIELD);
    if (approvalsBytes != null) {
      List<PatchSetApproval> approvals =
          Lists.newArrayListWithCapacity(approvalsBytes.length);
      for (BytesRef ab : approvalsBytes) {
        approvals.add(PatchSetApprovalProtoField.CODEC.decode(
            ab.bytes, ab.offset, ab.length));
      }
      cd.setCurrentApprovals(approvals);
    }
    return cd;
  }

  private Document toDocument(ChangeData cd) throws IOException {
    try {
      Document result = new Document();
      for (FieldDef<ChangeData, ?> f : schema.getFields().values()) {
        if (f.isRepeatable()) {
          add(result, f, (Iterable<?>) f.get(cd, fillArgs));
        } else {
          Object val = f.get(cd, fillArgs);
          if (val != null) {
            add(result, f, Collections.singleton(val));
          }
        }
      }
      return result;
    } catch (OrmException e) {
      throw new IOException(e);
    }
  }

  private void add(Document doc, FieldDef<ChangeData, ?> f,
      Iterable<?> values) throws OrmException {
    String name = f.getName();
    Store store = store(f);

    if (f.getType() == FieldType.INTEGER) {
      for (Object value : values) {
        doc.add(new IntField(name, (Integer) value, store));
      }
    } else if (f.getType() == FieldType.LONG) {
      for (Object value : values) {
        doc.add(new LongField(name, (Long) value, store));
      }
    } else if (f.getType() == FieldType.TIMESTAMP) {
      for (Object v : values) {
        int t = QueryBuilder.toIndexTime((Timestamp) v);
        doc.add(new IntField(name, t, store));
      }
    } else if (f.getType() == FieldType.EXACT
        || f.getType() == FieldType.PREFIX) {
      for (Object value : values) {
        doc.add(new StringField(name, (String) value, store));
      }
    } else if (f.getType() == FieldType.FULL_TEXT) {
      for (Object value : values) {
        doc.add(new TextField(name, (String) value, store));
      }
    } else if (f.getType() == FieldType.STORED_ONLY) {
      for (Object value : values) {
        doc.add(new StoredField(name, (byte[]) value));
      }
    } else {
      throw QueryBuilder.badFieldType(f.getType());
    }
  }

  private static Field.Store store(FieldDef<?, ?> f) {
    return f.isStored() ? Field.Store.YES : Field.Store.NO;
  }
}
