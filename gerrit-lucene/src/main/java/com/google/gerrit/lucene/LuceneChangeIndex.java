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
import static com.google.gerrit.lucene.AbstractLuceneIndex.sortFieldName;
import static com.google.gerrit.lucene.LuceneVersionManager.CHANGES_PREFIX;
import static com.google.gerrit.server.git.QueueProvider.QueueType.INTERACTIVE;
import static com.google.gerrit.server.index.change.ChangeField.LEGACY_ID;
import static com.google.gerrit.server.index.change.ChangeField.PROJECT;
import static com.google.gerrit.server.index.change.ChangeIndexRewriter.CLOSED_STATUSES;
import static com.google.gerrit.server.index.change.ChangeIndexRewriter.OPEN_STATUSES;

import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.index.IndexUtils;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeField.ChangeProtoField;
import com.google.gerrit.server.index.change.ChangeField.PatchSetApprovalProtoField;
import com.google.gerrit.server.index.change.ChangeField.PatchSetProtoField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
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
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Secondary index implementation using Apache Lucene.
 *
 * <p>Writes are managed using a single {@link IndexWriter} per process, committed aggressively.
 * Reads use {@link SearcherManager} and periodically refresh, though there may be some lag between
 * a committed write and it showing up to other threads' searchers.
 */
public class LuceneChangeIndex implements ChangeIndex {
  private static final Logger log = LoggerFactory.getLogger(LuceneChangeIndex.class);

  public static final String CHANGES_OPEN = "open";
  public static final String CHANGES_CLOSED = "closed";

  static final String UPDATED_SORT_FIELD = sortFieldName(ChangeField.UPDATED);
  static final String ID_SORT_FIELD = sortFieldName(ChangeField.LEGACY_ID);

  private static final String ADDED_FIELD = ChangeField.ADDED.getName();
  private static final String APPROVAL_FIELD = ChangeField.APPROVAL.getName();
  private static final String CHANGE_FIELD = ChangeField.CHANGE.getName();
  private static final String DELETED_FIELD = ChangeField.DELETED.getName();
  private static final String MERGEABLE_FIELD = ChangeField.MERGEABLE.getName();
  private static final String PATCH_SET_FIELD = ChangeField.PATCH_SET.getName();
  private static final String REVIEWEDBY_FIELD = ChangeField.REVIEWEDBY.getName();
  private static final String REVIEWER_FIELD = ChangeField.REVIEWER.getName();
  private static final String HASHTAG_FIELD = ChangeField.HASHTAG_CASE_AWARE.getName();
  private static final String STAR_FIELD = ChangeField.STAR.getName();
  private static final String SUBMIT_RECORD_LENIENT_FIELD =
      ChangeField.STORED_SUBMIT_RECORD_LENIENT.getName();
  private static final String SUBMIT_RECORD_STRICT_FIELD =
      ChangeField.STORED_SUBMIT_RECORD_STRICT.getName();

  static Term idTerm(ChangeData cd) {
    return QueryBuilder.intTerm(LEGACY_ID.getName(), cd.getId().get());
  }

  static Term idTerm(Change.Id id) {
    return QueryBuilder.intTerm(LEGACY_ID.getName(), id.get());
  }

  private final FillArgs fillArgs;
  private final ListeningExecutorService executor;
  private final Provider<ReviewDb> db;
  private final ChangeData.Factory changeDataFactory;
  private final Schema<ChangeData> schema;
  private final QueryBuilder<ChangeData> queryBuilder;
  private final ChangeSubIndex openIndex;
  private final ChangeSubIndex closedIndex;

  @AssistedInject
  LuceneChangeIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      @IndexExecutor(INTERACTIVE) ListeningExecutorService executor,
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      FillArgs fillArgs,
      @Assisted Schema<ChangeData> schema)
      throws IOException {
    this.fillArgs = fillArgs;
    this.executor = executor;
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.schema = schema;

    GerritIndexWriterConfig openConfig = new GerritIndexWriterConfig(cfg, "changes_open");
    GerritIndexWriterConfig closedConfig = new GerritIndexWriterConfig(cfg, "changes_closed");

    queryBuilder = new QueryBuilder<>(schema, openConfig.getAnalyzer());

    SearcherFactory searcherFactory = new SearcherFactory();
    if (LuceneIndexModule.isInMemoryTest(cfg)) {
      openIndex =
          new ChangeSubIndex(
              schema, sitePaths, new RAMDirectory(), "ramOpen", openConfig, searcherFactory);
      closedIndex =
          new ChangeSubIndex(
              schema, sitePaths, new RAMDirectory(), "ramClosed", closedConfig, searcherFactory);
    } else {
      Path dir = LuceneVersionManager.getDir(sitePaths, CHANGES_PREFIX, schema);
      openIndex =
          new ChangeSubIndex(
              schema, sitePaths, dir.resolve(CHANGES_OPEN), openConfig, searcherFactory);
      closedIndex =
          new ChangeSubIndex(
              schema, sitePaths, dir.resolve(CHANGES_CLOSED), closedConfig, searcherFactory);
    }
  }

  @Override
  public void close() {
    List<ListenableFuture<?>> closeFutures = Lists.newArrayListWithCapacity(2);
    closeFutures.add(
        executor.submit(
            new Runnable() {
              @Override
              public void run() {
                openIndex.close();
              }
            }));
    closeFutures.add(
        executor.submit(
            new Runnable() {
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
    Term id = LuceneChangeIndex.idTerm(cd);
    // toDocument is essentially static and doesn't depend on the specific
    // sub-index, so just pick one.
    Document doc = openIndex.toDocument(cd, fillArgs);
    try {
      if (cd.change().getStatus().isOpen()) {
        Futures.allAsList(closedIndex.delete(id), openIndex.replace(id, doc)).get();
      } else {
        Futures.allAsList(openIndex.delete(id), closedIndex.replace(id, doc)).get();
      }
    } catch (OrmException | ExecutionException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void delete(Change.Id id) throws IOException {
    Term idTerm = LuceneChangeIndex.idTerm(id);
    try {
      Futures.allAsList(openIndex.delete(idTerm), closedIndex.delete(idTerm)).get();
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
    Set<Change.Status> statuses = ChangeIndexRewriter.getPossibleStatus(p);
    List<ChangeSubIndex> indexes = new ArrayList<>(2);
    if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()) {
      indexes.add(openIndex);
    }
    if (!Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
      indexes.add(closedIndex);
    }
    return new QuerySource(indexes, p, opts, getSort());
  }

  @Override
  public void markReady(boolean ready) throws IOException {
    // Arbitrary done on open index, as ready bit is set
    // per index and not sub index
    openIndex.markReady(ready);
  }

  private Sort getSort() {
    return new Sort(
        new SortField(UPDATED_SORT_FIELD, SortField.Type.LONG, true),
        new SortField(ID_SORT_FIELD, SortField.Type.LONG, true));
  }

  public ChangeSubIndex getClosedChangesIndex() {
    return closedIndex;
  }

  private class QuerySource implements ChangeDataSource {
    private final List<ChangeSubIndex> indexes;
    private final Predicate<ChangeData> predicate;
    private final Query query;
    private final QueryOptions opts;
    private final Sort sort;

    private QuerySource(
        List<ChangeSubIndex> indexes, Predicate<ChangeData> predicate, QueryOptions opts, Sort sort)
        throws QueryParseException {
      this.indexes = indexes;
      this.predicate = predicate;
      this.query = checkNotNull(queryBuilder.toQuery(predicate), "null query from Lucene");
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
      return predicate.toString();
    }

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      if (Thread.interrupted()) {
        Thread.currentThread().interrupt();
        throw new OrmException("interrupted");
      }

      final Set<String> fields = IndexUtils.fields(opts);
      return new ChangeDataResults(
          executor.submit(
              new Callable<List<Document>>() {
                @Override
                public List<Document> call() throws IOException {
                  return doRead(fields);
                }

                @Override
                public String toString() {
                  return predicate.toString();
                }
              }),
          fields);
    }

    private List<Document> doRead(Set<String> fields) throws IOException {
      IndexSearcher[] searchers = new IndexSearcher[indexes.size()];
      try {
        int realLimit = opts.start() + opts.limit();
        if (Integer.MAX_VALUE - opts.limit() < opts.start()) {
          realLimit = Integer.MAX_VALUE;
        }
        TopFieldDocs[] hits = new TopFieldDocs[indexes.size()];
        for (int i = 0; i < indexes.size(); i++) {
          searchers[i] = indexes.get(i).acquire();
          hits[i] = searchers[i].search(query, realLimit, sort);
        }
        TopDocs docs = TopDocs.merge(sort, realLimit, hits);

        List<Document> result = new ArrayList<>(docs.scoreDocs.length);
        for (int i = opts.start(); i < docs.scoreDocs.length; i++) {
          ScoreDoc sd = docs.scoreDocs[i];
          result.add(searchers[sd.shardIndex].doc(sd.doc, fields));
        }
        return result;
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

  private class ChangeDataResults implements ResultSet<ChangeData> {
    private final Future<List<Document>> future;
    private final Set<String> fields;

    ChangeDataResults(Future<List<Document>> future, Set<String> fields) {
      this.future = future;
      this.fields = fields;
    }

    @Override
    public Iterator<ChangeData> iterator() {
      return toList().iterator();
    }

    @Override
    public List<ChangeData> toList() {
      try {
        List<Document> docs = future.get();
        List<ChangeData> result = new ArrayList<>(docs.size());
        String idFieldName = LEGACY_ID.getName();
        for (Document doc : docs) {
          result.add(toChangeData(fields(doc, fields), fields, idFieldName));
        }
        return result;
      } catch (InterruptedException e) {
        close();
        throw new OrmRuntimeException(e);
      } catch (ExecutionException e) {
        Throwables.throwIfUnchecked(e.getCause());
        throw new OrmRuntimeException(e.getCause());
      }
    }

    @Override
    public void close() {
      future.cancel(false /* do not interrupt Lucene */);
    }
  }

  private static Multimap<String, IndexableField> fields(Document doc, Set<String> fields) {
    Multimap<String, IndexableField> stored = ArrayListMultimap.create(fields.size(), 4);
    for (IndexableField f : doc) {
      String name = f.name();
      if (fields.contains(name)) {
        stored.put(name, f);
      }
    }
    return stored;
  }

  private ChangeData toChangeData(
      Multimap<String, IndexableField> doc, Set<String> fields, String idFieldName) {
    ChangeData cd;
    // Either change or the ID field was guaranteed to be included in the call
    // to fields() above.
    IndexableField cb = Iterables.getFirst(doc.get(CHANGE_FIELD), null);
    if (cb != null) {
      BytesRef proto = cb.binaryValue();
      cd =
          changeDataFactory.create(
              db.get(), ChangeProtoField.CODEC.decode(proto.bytes, proto.offset, proto.length));
    } else {
      IndexableField f = Iterables.getFirst(doc.get(idFieldName), null);
      Change.Id id = new Change.Id(f.numericValue().intValue());
      IndexableField project = Iterables.getFirst(doc.get(PROJECT.getName()), null);
      if (project == null) {
        // Old schema without project field: we can safely assume NoteDb is
        // disabled.
        cd = changeDataFactory.createOnlyWhenNoteDbDisabled(db.get(), id);
      } else {
        cd = changeDataFactory.create(db.get(), new Project.NameKey(project.stringValue()), id);
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
    if (fields.contains(HASHTAG_FIELD)) {
      decodeHashtags(doc, cd);
    }
    if (fields.contains(STAR_FIELD)) {
      decodeStar(doc, cd);
    }
    if (fields.contains(REVIEWER_FIELD)) {
      decodeReviewers(doc, cd);
    }
    decodeSubmitRecords(
        doc, SUBMIT_RECORD_STRICT_FIELD, ChangeField.SUBMIT_RULE_OPTIONS_STRICT, cd);
    decodeSubmitRecords(
        doc, SUBMIT_RECORD_LENIENT_FIELD, ChangeField.SUBMIT_RULE_OPTIONS_LENIENT, cd);
    return cd;
  }

  private void decodePatchSets(Multimap<String, IndexableField> doc, ChangeData cd) {
    List<PatchSet> patchSets = decodeProtos(doc, PATCH_SET_FIELD, PatchSetProtoField.CODEC);
    if (!patchSets.isEmpty()) {
      // Will be an empty list for schemas prior to when this field was stored;
      // this cannot be valid since a change needs at least one patch set.
      cd.setPatchSets(patchSets);
    }
  }

  private void decodeApprovals(Multimap<String, IndexableField> doc, ChangeData cd) {
    cd.setCurrentApprovals(decodeProtos(doc, APPROVAL_FIELD, PatchSetApprovalProtoField.CODEC));
  }

  private void decodeChangedLines(Multimap<String, IndexableField> doc, ChangeData cd) {
    IndexableField added = Iterables.getFirst(doc.get(ADDED_FIELD), null);
    IndexableField deleted = Iterables.getFirst(doc.get(DELETED_FIELD), null);
    if (added != null && deleted != null) {
      cd.setChangedLines(added.numericValue().intValue(), deleted.numericValue().intValue());
    } else {
      // No ChangedLines stored, likely due to failure during reindexing, for
      // example due to LargeObjectException. But we know the field was
      // requested, so update ChangeData to prevent callers from trying to
      // lazily load it, as that would probably also fail.
      cd.setNoChangedLines();
    }
  }

  private void decodeMergeable(Multimap<String, IndexableField> doc, ChangeData cd) {
    IndexableField f = Iterables.getFirst(doc.get(MERGEABLE_FIELD), null);
    if (f != null) {
      String mergeable = f.stringValue();
      if ("1".equals(mergeable)) {
        cd.setMergeable(true);
      } else if ("0".equals(mergeable)) {
        cd.setMergeable(false);
      }
    }
  }

  private void decodeReviewedBy(Multimap<String, IndexableField> doc, ChangeData cd) {
    Collection<IndexableField> reviewedBy = doc.get(REVIEWEDBY_FIELD);
    if (reviewedBy.size() > 0) {
      Set<Account.Id> accounts = Sets.newHashSetWithExpectedSize(reviewedBy.size());
      for (IndexableField r : reviewedBy) {
        int id = r.numericValue().intValue();
        if (reviewedBy.size() == 1 && id == ChangeField.NOT_REVIEWED) {
          break;
        }
        accounts.add(new Account.Id(id));
      }
      cd.setReviewedBy(accounts);
    }
  }

  private void decodeHashtags(Multimap<String, IndexableField> doc, ChangeData cd) {
    Collection<IndexableField> hashtag = doc.get(HASHTAG_FIELD);
    Set<String> hashtags = Sets.newHashSetWithExpectedSize(hashtag.size());
    for (IndexableField r : hashtag) {
      hashtags.add(r.binaryValue().utf8ToString());
    }
    cd.setHashtags(hashtags);
  }

  private void decodeStar(Multimap<String, IndexableField> doc, ChangeData cd) {
    Collection<IndexableField> star = doc.get(STAR_FIELD);
    Multimap<Account.Id, String> stars = ArrayListMultimap.create();
    for (IndexableField r : star) {
      StarredChangesUtil.StarField starField = StarredChangesUtil.StarField.parse(r.stringValue());
      if (starField != null) {
        stars.put(starField.accountId(), starField.label());
      }
    }
    cd.setStars(stars);
  }

  private void decodeReviewers(Multimap<String, IndexableField> doc, ChangeData cd) {
    cd.setReviewers(
        ChangeField.parseReviewerFieldValues(
            FluentIterable.from(doc.get(REVIEWER_FIELD)).transform(IndexableField::stringValue)));
  }

  private void decodeSubmitRecords(
      Multimap<String, IndexableField> doc, String field, SubmitRuleOptions opts, ChangeData cd) {
    ChangeField.parseSubmitRecords(
        Collections2.transform(doc.get(field), f -> f.binaryValue().utf8ToString()), opts, cd);
  }

  private static <T> List<T> decodeProtos(
      Multimap<String, IndexableField> doc, String fieldName, ProtobufCodec<T> codec) {
    Collection<IndexableField> fields = doc.get(fieldName);
    if (fields.isEmpty()) {
      return Collections.emptyList();
    }

    List<T> result = new ArrayList<>(fields.size());
    for (IndexableField f : fields) {
      BytesRef r = f.binaryValue();
      result.add(codec.decode(r.bytes, r.offset, r.length));
    }
    return result;
  }
}
