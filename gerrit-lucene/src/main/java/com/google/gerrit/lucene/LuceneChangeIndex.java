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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.git.QueueProvider.QueueType.INTERACTIVE;
import static com.google.gerrit.server.index.IndexRewriteImpl.CLOSED_STATUSES;
import static com.google.gerrit.server.index.IndexRewriteImpl.OPEN_STATUSES;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeField.ChangeProtoField;
import com.google.gerrit.server.index.ChangeField.PatchSetApprovalProtoField;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.IndexRewriteImpl;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.Schema.Values;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;
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
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

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

  public static final String CHANGES_OPEN = "open";
  public static final String CHANGES_CLOSED = "closed";
  public static final Map<String, String> CUSTOM_CHAR_MAPPING = ImmutableMap.of(
      "_", " ", ".", " ");

  private static final String ADDED_FIELD = ChangeField.ADDED.getName();
  private static final String APPROVAL_FIELD = ChangeField.APPROVAL.getName();
  private static final String CHANGE_FIELD = ChangeField.CHANGE.getName();
  private static final String DELETED_FIELD = ChangeField.DELETED.getName();
  private static final String ID_FIELD = ChangeField.LEGACY_ID.getName();
  private static final String MERGEABLE_FIELD = ChangeField.MERGEABLE.getName();
  private static final ImmutableSet<String> FIELDS = ImmutableSet.of(
      ADDED_FIELD, APPROVAL_FIELD, CHANGE_FIELD, DELETED_FIELD, ID_FIELD,
      MERGEABLE_FIELD);

  private static final Map<Schema<ChangeData>, Version> LUCENE_VERSIONS;
  static {
    ImmutableMap.Builder<Schema<ChangeData>, Version> versions =
        ImmutableMap.builder();
    @SuppressWarnings("deprecation")
    Version lucene43 = Version.LUCENE_43;
    @SuppressWarnings("deprecation")
    Version lucene44 = Version.LUCENE_44;
    @SuppressWarnings("deprecation")
    Version lucene46 = Version.LUCENE_46;
    @SuppressWarnings("deprecation")
    Version lucene47 = Version.LUCENE_47;
    @SuppressWarnings("deprecation")
    Version lucene48 = Version.LUCENE_48;
    @SuppressWarnings("deprecation")
    Version lucene410 = Version.LUCENE_4_10_0;
    // We are using 4.10.2 but there is no difference in the index
    // format since 4.10.1, so we reuse the version here.
    @SuppressWarnings("deprecation")
    Version lucene4101 = Version.LUCENE_4_10_1;
    for (Map.Entry<Integer, Schema<ChangeData>> e
        : ChangeSchemas.ALL.entrySet()) {
      if (e.getKey() <= 3) {
        versions.put(e.getValue(), lucene43);
      } else if (e.getKey() <= 5) {
        versions.put(e.getValue(), lucene44);
      } else if (e.getKey() <= 8) {
        versions.put(e.getValue(), lucene46);
      } else if (e.getKey() <= 10) {
        versions.put(e.getValue(), lucene47);
      } else if (e.getKey() <= 11) {
        versions.put(e.getValue(), lucene48);
      } else if (e.getKey() <= 13) {
        versions.put(e.getValue(), lucene410);
      } else {
        versions.put(e.getValue(), lucene4101);
      }
    }
    LUCENE_VERSIONS = versions.build();
  }

  public static void setReady(SitePaths sitePaths, int version, boolean ready)
      throws IOException {
    try {
      FileBasedConfig cfg =
          LuceneVersionManager.loadGerritIndexConfig(sitePaths);
      LuceneVersionManager.setReady(cfg, version, ready);
      cfg.save();
    } catch (ConfigInvalidException e) {
      throw new IOException(e);
    }
  }

  static interface Factory {
    LuceneChangeIndex create(Schema<ChangeData> schema, String base);
  }

  static class GerritIndexWriterConfig {
    private final IndexWriterConfig luceneConfig;
    private long commitWithinMs;

    private GerritIndexWriterConfig(Version version, Config cfg, String name) {
      CustomMappingAnalyzer analyzer =
          new CustomMappingAnalyzer(new StandardAnalyzer(
              CharArraySet.EMPTY_SET), CUSTOM_CHAR_MAPPING);
      luceneConfig = new IndexWriterConfig(version, analyzer);
      luceneConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
      double m = 1 << 20;
      luceneConfig.setRAMBufferSizeMB(cfg.getLong(
          "index", name, "ramBufferSize",
          (long) (IndexWriterConfig.DEFAULT_RAM_BUFFER_SIZE_MB * m)) / m);
      luceneConfig.setMaxBufferedDocs(cfg.getInt(
          "index", name, "maxBufferedDocs",
          IndexWriterConfig.DEFAULT_MAX_BUFFERED_DOCS));
      try {
        commitWithinMs =
            ConfigUtil.getTimeUnit(cfg, "index", name, "commitWithin",
                MILLISECONDS.convert(5, MINUTES), MILLISECONDS);
      } catch (IllegalArgumentException e) {
        commitWithinMs = cfg.getLong("index", name, "commitWithin", 0);
      }
    }

    IndexWriterConfig getLuceneConfig() {
      return luceneConfig;
    }

    long getCommitWithinMs() {
      return commitWithinMs;
    }
  }

  private final SitePaths sitePaths;
  private final FillArgs fillArgs;
  private final ListeningExecutorService executor;
  private final Provider<ReviewDb> db;
  private final ChangeData.Factory changeDataFactory;
  private final File dir;
  private final Schema<ChangeData> schema;
  private final QueryBuilder queryBuilder;
  private final SubIndex openIndex;
  private final SubIndex closedIndex;

  @AssistedInject
  LuceneChangeIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      @IndexExecutor(INTERACTIVE)  ListeningExecutorService executor,
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      FillArgs fillArgs,
      @Assisted Schema<ChangeData> schema,
      @Assisted @Nullable String base) throws IOException {
    this.sitePaths = sitePaths;
    this.fillArgs = fillArgs;
    this.executor = executor;
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.schema = schema;

    if (base == null) {
      dir = LuceneVersionManager.getDir(sitePaths, schema);
    } else {
      dir = new File(base);
    }
    Version luceneVersion = checkNotNull(
        LUCENE_VERSIONS.get(schema),
        "unknown Lucene version for index schema: %s", schema);
    CustomMappingAnalyzer analyzer =
        new CustomMappingAnalyzer(new StandardAnalyzer(CharArraySet.EMPTY_SET),
            CUSTOM_CHAR_MAPPING);
    queryBuilder = new QueryBuilder(analyzer);

    BooleanQuery.setMaxClauseCount(cfg.getInt("index", "defaultMaxClauseCount",
        BooleanQuery.getMaxClauseCount()));

    GerritIndexWriterConfig openConfig =
        new GerritIndexWriterConfig(luceneVersion, cfg, "changes_open");
    GerritIndexWriterConfig closedConfig =
        new GerritIndexWriterConfig(luceneVersion, cfg, "changes_closed");

    if (cfg.getBoolean("index", "lucene", "testInmemory", false)) {
      openIndex = new SubIndex(new RAMDirectory(), "ramOpen", openConfig);
      closedIndex = new SubIndex(new RAMDirectory(), "ramClosed", closedConfig);
    } else {
      openIndex = new SubIndex(new File(dir, CHANGES_OPEN), openConfig);
      closedIndex = new SubIndex(new File(dir, CHANGES_CLOSED), closedConfig);
    }
  }

  @Override
  public void close() {
    List<ListenableFuture<?>> closeFutures = Lists.newArrayListWithCapacity(2);
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
    Futures.getUnchecked(Futures.allAsList(closeFutures));
  }

  @Override
  public Schema<ChangeData> getSchema() {
    return schema;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void replace(ChangeData cd) throws IOException {
    Term id = QueryBuilder.idTerm(cd);
    Document doc = toDocument(cd);
    try {
      if (cd.change().getStatus().isOpen()) {
        Futures.allAsList(
            closedIndex.delete(id),
            openIndex.replace(id, doc)).get();
      } else {
        Futures.allAsList(
            openIndex.delete(id),
            closedIndex.replace(id, doc)).get();
      }
    } catch (OrmException | ExecutionException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void delete(Change.Id id) throws IOException {
    Term idTerm = QueryBuilder.idTerm(id);
    try {
      Futures.allAsList(
          openIndex.delete(idTerm),
          closedIndex.delete(idTerm)).get();
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void deleteAll() throws IOException {
    openIndex.deleteAll();
    closedIndex.deleteAll();
  }

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p, int start,
      int limit) throws QueryParseException {
    Set<Change.Status> statuses = IndexRewriteImpl.getPossibleStatus(p);
    List<SubIndex> indexes = Lists.newArrayListWithCapacity(2);
    if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()) {
      indexes.add(openIndex);
    }
    if (!Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
      indexes.add(closedIndex);
    }
    return new QuerySource(indexes, queryBuilder.toQuery(p), start, limit,
        getSort());
  }

  @Override
  public void markReady(boolean ready) throws IOException {
    setReady(sitePaths, schema.getVersion(), ready);
  }

  private static Sort getSort() {
    return new Sort(
        new SortField(
          ChangeField.UPDATED.getName(), SortField.Type.LONG, true),
        new SortField(
          ChangeField.LEGACY_ID.getName(), SortField.Type.INT, true));
  }

  private class QuerySource implements ChangeDataSource {
    private final List<SubIndex> indexes;
    private final Query query;
    private final int start;
    private final int limit;
    private final Sort sort;

    private QuerySource(List<SubIndex> indexes, Query query, int start,
        int limit, Sort sort) {
      this.indexes = indexes;
      this.query = query;
      this.start = start;
      this.limit = limit;
      this.sort = sort;
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
      try {
        int realLimit = start + limit;
        TopDocs[] hits = new TopDocs[indexes.size()];
        for (int i = 0; i < indexes.size(); i++) {
          searchers[i] = indexes.get(i).acquire();
          hits[i] = searchers[i].search(query, realLimit, sort);
        }
        TopDocs docs = TopDocs.merge(sort, realLimit, hits);

        List<ChangeData> result =
            Lists.newArrayListWithCapacity(docs.scoreDocs.length);
        for (int i = start; i < docs.scoreDocs.length; i++) {
          ScoreDoc sd = docs.scoreDocs[i];
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

  private ChangeData toChangeData(Document doc) {
    BytesRef cb = doc.getBinaryValue(CHANGE_FIELD);
    if (cb == null) {
      int id = doc.getField(ID_FIELD).numericValue().intValue();
      return changeDataFactory.create(db.get(), new Change.Id(id));
    }

    // Change proto.
    Change change = ChangeProtoField.CODEC.decode(
        cb.bytes, cb.offset, cb.length);
    ChangeData cd = changeDataFactory.create(db.get(), change);

    // Approvals.
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

    // Changed lines.
    IndexableField added = doc.getField(ADDED_FIELD);
    IndexableField deleted = doc.getField(DELETED_FIELD);
    if (added != null && deleted != null) {
      cd.setChangedLines(
          added.numericValue().intValue(),
          deleted.numericValue().intValue());
    }

    // Mergeable.
    String mergeable = doc.get(MERGEABLE_FIELD);
    if ("1".equals(mergeable)) {
      cd.setMergeable(true);
    } else if ("0".equals(mergeable)) {
      cd.setMergeable(false);
    }

    return cd;
  }

  private Document toDocument(ChangeData cd) {
    Document result = new Document();
    for (Values<ChangeData> vs : schema.buildFields(cd, fillArgs)) {
      if (vs.getValues() != null) {
        add(result, vs);
      }
    }
    return result;
  }

  private void add(Document doc, Values<ChangeData> values) {
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
    } else if (type == FieldType.EXACT
        || type == FieldType.PREFIX) {
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
      throw QueryBuilder.badFieldType(type);
    }
  }

  private static Field.Store store(FieldDef<?, ?> f) {
    return f.isStored() ? Field.Store.YES : Field.Store.NO;
  }
}
