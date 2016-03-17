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
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.gerrit.server.git.QueueProvider.QueueType.INTERACTIVE;
import static com.google.gerrit.server.index.change.ChangeField.CHANGE;
import static com.google.gerrit.server.index.change.ChangeField.LEGACY_ID;
import static com.google.gerrit.server.index.change.ChangeField.PROJECT;
import static com.google.gerrit.server.index.change.IndexRewriter.CLOSED_STATUSES;
import static com.google.gerrit.server.index.change.IndexRewriter.OPEN_STATUSES;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.Schema.Values;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeField.ChangeProtoField;
import com.google.gerrit.server.index.change.ChangeField.PatchSetApprovalProtoField;
import com.google.gerrit.server.index.change.ChangeField.PatchSetProtoField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.IndexRewriter;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gwtorm.protobuf.ProtobufCodec;
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
import org.apache.lucene.document.NumericDocValuesField;
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
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
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

  private static final String ADDED_FIELD = ChangeField.ADDED.getName();
  private static final String APPROVAL_FIELD = ChangeField.APPROVAL.getName();
  private static final String CHANGE_FIELD = ChangeField.CHANGE.getName();
  private static final String DELETED_FIELD = ChangeField.DELETED.getName();
  private static final String MERGEABLE_FIELD = ChangeField.MERGEABLE.getName();
  private static final String PATCH_SET_FIELD = ChangeField.PATCH_SET.getName();
  private static final String REVIEWEDBY_FIELD =
      ChangeField.REVIEWEDBY.getName();
  private static final String UPDATED_SORT_FIELD =
      sortFieldName(ChangeField.UPDATED);
  private static final String ID_SORT_FIELD =
      sortFieldName(ChangeField.LEGACY_ID);

  private static final Map<String, String> CUSTOM_CHAR_MAPPING = ImmutableMap.of(
      "_", " ", ".", " ");

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

  private static String sortFieldName(FieldDef<?, ?> f) {
    return f.getName() + "_SORT";
  }

  static interface Factory {
    LuceneChangeIndex create(Schema<ChangeData> schema);
  }

  static class GerritIndexWriterConfig {
    private final IndexWriterConfig luceneConfig;
    private long commitWithinMs;

    private GerritIndexWriterConfig(Config cfg, String name) {
      CustomMappingAnalyzer analyzer =
          new CustomMappingAnalyzer(new StandardAnalyzer(
              CharArraySet.EMPTY_SET), CUSTOM_CHAR_MAPPING);
      luceneConfig = new IndexWriterConfig(analyzer)
          .setOpenMode(OpenMode.CREATE_OR_APPEND)
          .setCommitOnClose(true);
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
      @Assisted Schema<ChangeData> schema) throws IOException {
    this.sitePaths = sitePaths;
    this.fillArgs = fillArgs;
    this.executor = executor;
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.schema = schema;

    CustomMappingAnalyzer analyzer =
        new CustomMappingAnalyzer(new StandardAnalyzer(CharArraySet.EMPTY_SET),
            CUSTOM_CHAR_MAPPING);
    queryBuilder = new QueryBuilder(analyzer);

    BooleanQuery.setMaxClauseCount(cfg.getInt("index", "maxTerms",
        BooleanQuery.getMaxClauseCount()));

    GerritIndexWriterConfig openConfig =
        new GerritIndexWriterConfig(cfg, "changes_open");
    GerritIndexWriterConfig closedConfig =
        new GerritIndexWriterConfig(cfg, "changes_closed");

    SearcherFactory searcherFactory = new SearcherFactory();
    if (cfg.getBoolean("index", "lucene", "testInmemory", false)) {
      openIndex = new SubIndex(new RAMDirectory(), "ramOpen", openConfig,
          searcherFactory);
      closedIndex = new SubIndex(new RAMDirectory(), "ramClosed", closedConfig,
          searcherFactory);
    } else {
      Path dir = LuceneVersionManager.getDir(sitePaths, schema);
      openIndex = new SubIndex(dir.resolve(CHANGES_OPEN), openConfig,
          searcherFactory);
      closedIndex = new SubIndex(dir.resolve(CHANGES_CLOSED), closedConfig,
          searcherFactory);
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
  public ChangeDataSource getSource(Predicate<ChangeData> p, QueryOptions opts)
      throws QueryParseException {
    Set<Change.Status> statuses = IndexRewriter.getPossibleStatus(p);
    List<SubIndex> indexes = Lists.newArrayListWithCapacity(2);
    if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()) {
      indexes.add(openIndex);
    }
    if (!Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
      indexes.add(closedIndex);
    }
    return new QuerySource(indexes, queryBuilder.toQuery(p), opts, getSort());
  }

  @Override
  public void markReady(boolean ready) throws IOException {
    setReady(sitePaths, schema.getVersion(), ready);
  }

  private Sort getSort() {
    return new Sort(
        new SortField(UPDATED_SORT_FIELD, SortField.Type.LONG, true),
        new SortField(ID_SORT_FIELD, SortField.Type.LONG, true));
  }

  public SubIndex getOpenChangesIndex() {
    return openIndex;
  }

  public SubIndex getClosedChangesIndex() {
    return closedIndex;
  }

  private class QuerySource implements ChangeDataSource {
    private final List<SubIndex> indexes;
    private final Query query;
    private final QueryOptions opts;
    private final Sort sort;

    private QuerySource(List<SubIndex> indexes, Query query, QueryOptions opts,
        Sort sort) {
      this.indexes = indexes;
      this.query = checkNotNull(query, "null query from Lucene");
      this.opts = opts;
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
        int realLimit = opts.start() + opts.limit();
        TopFieldDocs[] hits = new TopFieldDocs[indexes.size()];
        for (int i = 0; i < indexes.size(); i++) {
          searchers[i] = indexes.get(i).acquire();
          hits[i] = searchers[i].search(query, realLimit, sort);
        }
        TopDocs docs = TopDocs.merge(sort, realLimit, hits);

        List<ChangeData> result =
            Lists.newArrayListWithCapacity(docs.scoreDocs.length);
        Set<String> fields = fields(opts);
        String idFieldName = LEGACY_ID.getName();
        for (int i = opts.start(); i < docs.scoreDocs.length; i++) {
          ScoreDoc sd = docs.scoreDocs[i];
          Document doc = searchers[sd.shardIndex].doc(sd.doc, fields);
          result.add(toChangeData(doc, fields, idFieldName));
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

  private Set<String> fields(QueryOptions opts) {
    // Ensure we request enough fields to construct a ChangeData.
    Set<String> fs = opts.fields();
    if (fs.contains(CHANGE.getName())) {
      // A Change is always sufficient.
      return fs;
    }

    if (!schema.hasField(PROJECT)) {
      // Schema is not new enough to have project field. Ensure we have ID
      // field, and call createOnlyWhenNotedbDisabled from toChangeData below.
      if (fs.contains(LEGACY_ID.getName())) {
        return fs;
      } else {
        return Sets.union(fs, ImmutableSet.of(LEGACY_ID.getName()));
      }
    }

    // New enough schema to have project field, so ensure that is requested.
    if (fs.contains(PROJECT.getName()) && fs.contains(LEGACY_ID.getName())) {
      return fs;
    } else {
      return Sets.union(fs,
          ImmutableSet.of(LEGACY_ID.getName(), PROJECT.getName()));
    }
  }

  private ChangeData toChangeData(Document doc, Set<String> fields,
      String idFieldName) {
    ChangeData cd;
    // Either change or the ID field was guaranteed to be included in the call
    // to fields() above.
    BytesRef cb = doc.getBinaryValue(CHANGE_FIELD);
    if (cb != null) {
      cd = changeDataFactory.create(db.get(),
          ChangeProtoField.CODEC.decode(cb.bytes, cb.offset, cb.length));
    } else {
      Change.Id id =
          new Change.Id(doc.getField(idFieldName).numericValue().intValue());
      IndexableField project = doc.getField(PROJECT.getName());
      if (project == null) {
        // Old schema without project field: we can safely assume notedb is
        // disabled.
        cd = changeDataFactory.createOnlyWhenNotedbDisabled(db.get(), id);
      } else {
        cd = changeDataFactory.create(
            db.get(), new Project.NameKey(project.stringValue()), id);
      }
    }

    if (fields.contains(PATCH_SET_FIELD)) {
      decodePatchSets(doc, cd);
    }
    if (fields.contains(APPROVAL_FIELD)) {
      decodeApprovals(doc, cd);
    }
    if (fields.contains(ADDED_FIELD) && fields.contains(DELETED_FIELD)) {
      decodeChangedLines(doc, cd);
    }
    if (fields.contains(MERGEABLE_FIELD)) {
      decodeMergeable(doc, cd);
    }
    if (fields.contains(REVIEWEDBY_FIELD)) {
      decodeReviewedBy(doc, cd);
    }
    return cd;
  }

  private void decodePatchSets(Document doc, ChangeData cd) {
    List<PatchSet> patchSets =
        decodeProtos(doc, PATCH_SET_FIELD, PatchSetProtoField.CODEC);
    if (!patchSets.isEmpty()) {
      // Will be an empty list for schemas prior to when this field was stored;
      // this cannot be valid since a change needs at least one patch set.
      cd.setPatchSets(patchSets);
    }
  }

  private void decodeApprovals(Document doc, ChangeData cd) {
    cd.setCurrentApprovals(
        decodeProtos(doc, APPROVAL_FIELD, PatchSetApprovalProtoField.CODEC));
  }

  private void decodeChangedLines(Document doc, ChangeData cd) {
    IndexableField added = doc.getField(ADDED_FIELD);
    IndexableField deleted = doc.getField(DELETED_FIELD);
    if (added != null && deleted != null) {
      cd.setChangedLines(
          added.numericValue().intValue(),
          deleted.numericValue().intValue());
    }
  }

  private void decodeMergeable(Document doc, ChangeData cd) {
    String mergeable = doc.get(MERGEABLE_FIELD);
    if ("1".equals(mergeable)) {
      cd.setMergeable(true);
    } else if ("0".equals(mergeable)) {
      cd.setMergeable(false);
    }
  }

  private void decodeReviewedBy(Document doc, ChangeData cd) {
    IndexableField[] reviewedBy = doc.getFields(REVIEWEDBY_FIELD);
    if (reviewedBy.length > 0) {
      Set<Account.Id> accounts =
          Sets.newHashSetWithExpectedSize(reviewedBy.length);
      for (IndexableField r : reviewedBy) {
        int id = r.numericValue().intValue();
        if (reviewedBy.length == 1 && id == ChangeField.NOT_REVIEWED) {
          break;
        }
        accounts.add(new Account.Id(id));
      }
      cd.setReviewedBy(accounts);
    }
  }

  private static <T> List<T> decodeProtos(Document doc, String fieldName,
      ProtobufCodec<T> codec) {
    BytesRef[] bytesRefs = doc.getBinaryValues(fieldName);
    if (bytesRefs.length == 0) {
      return Collections.emptyList();
    }
    List<T> result = new ArrayList<>(bytesRefs.length);
    for (BytesRef r : bytesRefs) {
      result.add(codec.decode(r.bytes, r.offset, r.length));
    }
    return result;
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

    FieldDef<ChangeData, ?> f = values.getField();

    // Add separate DocValues fields for those fields needed for sorting.
    if (f == ChangeField.LEGACY_ID) {
      int v = (Integer) getOnlyElement(values.getValues());
      doc.add(new NumericDocValuesField(sortFieldName(f), v));
    } else if (f == ChangeField.UPDATED) {
      long t = ((Timestamp) getOnlyElement(values.getValues())).getTime();
      doc.add(new NumericDocValuesField(UPDATED_SORT_FIELD, t));
    }

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
      throw FieldType.badFieldType(type);
    }
  }

  private static Field.Store store(FieldDef<?, ?> f) {
    return f.isStored() ? Field.Store.YES : Field.Store.NO;
  }
}
