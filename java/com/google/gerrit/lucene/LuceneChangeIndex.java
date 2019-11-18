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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.lucene.AbstractLuceneIndex.sortFieldName;
import static com.google.gerrit.server.git.QueueProvider.QueueType.INTERACTIVE;
import static com.google.gerrit.server.index.change.ChangeField.LEGACY_ID;
import static com.google.gerrit.server.index.change.ChangeField.LEGACY_ID_STR;
import static com.google.gerrit.server.index.change.ChangeField.PROJECT;
import static com.google.gerrit.server.index.change.ChangeIndexRewriter.CLOSED_STATUSES;
import static com.google.gerrit.server.index.change.ChangeIndexRewriter.OPEN_STATUSES;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Throwables;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.converter.ChangeProtoConverter;
import com.google.gerrit.entities.converter.PatchSetApprovalProtoConverter;
import com.google.gerrit.entities.converter.PatchSetProtoConverter;
import com.google.gerrit.entities.converter.ProtoConverter;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.ResultSet;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
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

/**
 * Secondary index implementation using Apache Lucene.
 *
 * <p>Writes are managed using a single {@link IndexWriter} per process, committed aggressively.
 * Reads use {@link SearcherManager} and periodically refresh, though there may be some lag between
 * a committed write and it showing up to other threads' searchers.
 */
public class LuceneChangeIndex implements ChangeIndex {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String UPDATED_SORT_FIELD = sortFieldName(ChangeField.UPDATED);
  static final String ID_SORT_FIELD = sortFieldName(ChangeField.LEGACY_ID);
  static final String ID2_SORT_FIELD = sortFieldName(ChangeField.LEGACY_ID_STR);

  private static final String CHANGES = "changes";
  private static final String CHANGES_OPEN = "open";
  private static final String CHANGES_CLOSED = "closed";
  private static final String ADDED_FIELD = ChangeField.ADDED.getName();
  private static final String APPROVAL_FIELD = ChangeField.APPROVAL.getName();
  private static final String CHANGE_FIELD = ChangeField.CHANGE.getName();
  private static final String DELETED_FIELD = ChangeField.DELETED.getName();
  private static final String MERGEABLE_FIELD = ChangeField.MERGEABLE.getName();
  private static final String PATCH_SET_FIELD = ChangeField.PATCH_SET.getName();
  private static final String PENDING_REVIEWER_FIELD = ChangeField.PENDING_REVIEWER.getName();
  private static final String PENDING_REVIEWER_BY_EMAIL_FIELD =
      ChangeField.PENDING_REVIEWER_BY_EMAIL.getName();
  private static final String REF_STATE_FIELD = ChangeField.REF_STATE.getName();
  private static final String REF_STATE_PATTERN_FIELD = ChangeField.REF_STATE_PATTERN.getName();
  private static final String REVIEWEDBY_FIELD = ChangeField.REVIEWEDBY.getName();
  private static final String REVIEWER_FIELD = ChangeField.REVIEWER.getName();
  private static final String REVIEWER_BY_EMAIL_FIELD = ChangeField.REVIEWER_BY_EMAIL.getName();
  private static final String HASHTAG_FIELD = ChangeField.HASHTAG_CASE_AWARE.getName();
  private static final String STAR_FIELD = ChangeField.STAR.getName();
  private static final String SUBMIT_RECORD_LENIENT_FIELD =
      ChangeField.STORED_SUBMIT_RECORD_LENIENT.getName();
  private static final String SUBMIT_RECORD_STRICT_FIELD =
      ChangeField.STORED_SUBMIT_RECORD_STRICT.getName();
  private static final String TOTAL_COMMENT_COUNT_FIELD = ChangeField.TOTAL_COMMENT_COUNT.getName();
  private static final String UNRESOLVED_COMMENT_COUNT_FIELD =
      ChangeField.UNRESOLVED_COMMENT_COUNT.getName();

  @FunctionalInterface
  static interface IdTerm {
    Term get(String name, int id);
  }

  static Term idTerm(IdTerm idTerm, FieldDef<ChangeData, ?> idField, ChangeData cd) {
    return idTerm(idTerm, idField, cd.getId());
  }

  static Term idTerm(IdTerm idTerm, FieldDef<ChangeData, ?> idField, Change.Id id) {
    return idTerm.get(idField.getName(), id.get());
  }

  @FunctionalInterface
  static interface ChangeIdExtractor {
    Change.Id extract(IndexableField f);
  }

  private final ListeningExecutorService executor;
  private final ChangeData.Factory changeDataFactory;
  private final Schema<ChangeData> schema;
  private final QueryBuilder<ChangeData> queryBuilder;
  private final ChangeSubIndex openIndex;
  private final ChangeSubIndex closedIndex;

  // TODO(davido): Remove the below fields when support for legacy numeric fields is removed.
  private final FieldDef<ChangeData, ?> idField;
  private final String idSortFieldName;
  private final IdTerm idTerm;
  private final ChangeIdExtractor extractor;
  private final ImmutableSet<String> skipFields;

  @Inject
  LuceneChangeIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      @IndexExecutor(INTERACTIVE) ListeningExecutorService executor,
      ChangeData.Factory changeDataFactory,
      @Assisted Schema<ChangeData> schema)
      throws IOException {
    this.executor = executor;
    this.changeDataFactory = changeDataFactory;
    this.schema = schema;
    this.skipFields =
        cfg.getBoolean("index", "change", "indexMergeable", true)
            ? ImmutableSet.of()
            : ImmutableSet.of(ChangeField.MERGEABLE.getName());

    GerritIndexWriterConfig openConfig = new GerritIndexWriterConfig(cfg, "changes_open");
    GerritIndexWriterConfig closedConfig = new GerritIndexWriterConfig(cfg, "changes_closed");

    queryBuilder = new QueryBuilder<>(schema, openConfig.getAnalyzer());

    SearcherFactory searcherFactory = new SearcherFactory();
    if (LuceneIndexModule.isInMemoryTest(cfg)) {
      openIndex =
          new ChangeSubIndex(
              schema,
              sitePaths,
              new RAMDirectory(),
              "ramOpen",
              skipFields,
              openConfig,
              searcherFactory);
      closedIndex =
          new ChangeSubIndex(
              schema,
              sitePaths,
              new RAMDirectory(),
              "ramClosed",
              skipFields,
              closedConfig,
              searcherFactory);
    } else {
      Path dir = LuceneVersionManager.getDir(sitePaths, CHANGES, schema);
      openIndex =
          new ChangeSubIndex(
              schema,
              sitePaths,
              dir.resolve(CHANGES_OPEN),
              skipFields,
              openConfig,
              searcherFactory);
      closedIndex =
          new ChangeSubIndex(
              schema,
              sitePaths,
              dir.resolve(CHANGES_CLOSED),
              skipFields,
              closedConfig,
              searcherFactory);
    }

    idField = this.schema.useLegacyNumericFields() ? LEGACY_ID : LEGACY_ID_STR;
    idSortFieldName = schema.useLegacyNumericFields() ? ID_SORT_FIELD : ID2_SORT_FIELD;
    idTerm =
        (name, id) ->
            this.schema.useLegacyNumericFields()
                ? QueryBuilder.intTerm(name, id)
                : QueryBuilder.stringTerm(name, Integer.toString(id));
    extractor =
        (f) ->
            Change.id(
                this.schema.useLegacyNumericFields()
                    ? f.numericValue().intValue()
                    : Integer.valueOf(f.stringValue()));
  }

  @Override
  public void close() {
    try {
      openIndex.close();
    } finally {
      closedIndex.close();
    }
  }

  @Override
  public Schema<ChangeData> getSchema() {
    return schema;
  }

  @Override
  public void replace(ChangeData cd) {
    Term id = LuceneChangeIndex.idTerm(idTerm, idField, cd);
    // toDocument is essentially static and doesn't depend on the specific
    // sub-index, so just pick one.
    Document doc = openIndex.toDocument(cd);
    try {
      if (cd.change().isNew()) {
        Futures.allAsList(closedIndex.delete(id), openIndex.replace(id, doc)).get();
      } else {
        Futures.allAsList(openIndex.delete(id), closedIndex.replace(id, doc)).get();
      }
    } catch (ExecutionException | InterruptedException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void delete(Change.Id changeId) {
    Term id = LuceneChangeIndex.idTerm(idTerm, idField, changeId);
    try {
      Futures.allAsList(openIndex.delete(id), closedIndex.delete(id)).get();
    } catch (ExecutionException | InterruptedException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void deleteAll() {
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
    return new QuerySource(indexes, p, opts, getSort(), openIndex::toFieldBundle);
  }

  @Override
  public void markReady(boolean ready) {
    // Arbitrary done on open index, as ready bit is set
    // per index and not sub index
    openIndex.markReady(ready);
  }

  private Sort getSort() {
    return new Sort(
        new SortField(UPDATED_SORT_FIELD, SortField.Type.LONG, true),
        new SortField(idSortFieldName, SortField.Type.LONG, true));
  }

  private class QuerySource implements ChangeDataSource {
    private final List<ChangeSubIndex> indexes;
    private final Predicate<ChangeData> predicate;
    private final Query query;
    private final QueryOptions opts;
    private final Sort sort;
    private final Function<Document, FieldBundle> rawDocumentMapper;

    private QuerySource(
        List<ChangeSubIndex> indexes,
        Predicate<ChangeData> predicate,
        QueryOptions opts,
        Sort sort,
        Function<Document, FieldBundle> rawDocumentMapper)
        throws QueryParseException {
      this.indexes = indexes;
      this.predicate = predicate;
      this.query = requireNonNull(queryBuilder.toQuery(predicate), "null query from Lucene");
      this.opts = opts;
      this.sort = sort;
      this.rawDocumentMapper = rawDocumentMapper;
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
    public ResultSet<ChangeData> read() {
      if (Thread.interrupted()) {
        Thread.currentThread().interrupt();
        throw new StorageException("interrupted");
      }

      final Set<String> fields = IndexUtils.changeFields(opts, schema.useLegacyNumericFields());
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

    @Override
    public ResultSet<FieldBundle> readRaw() {
      List<Document> documents;
      try {
        documents = doRead(IndexUtils.changeFields(opts, schema.useLegacyNumericFields()));
      } catch (IOException e) {
        throw new StorageException(e);
      }
      ImmutableList<FieldBundle> fieldBundles =
          documents.stream().map(rawDocumentMapper).collect(toImmutableList());
      return new ResultSet<FieldBundle>() {
        @Override
        public Iterator<FieldBundle> iterator() {
          return fieldBundles.iterator();
        }

        @Override
        public ImmutableList<FieldBundle> toList() {
          return fieldBundles;
        }

        @Override
        public void close() {
          // Do nothing.
        }
      };
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
              logger.atWarning().withCause(e).log("cannot release Lucene searcher");
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
    public ImmutableList<ChangeData> toList() {
      try {
        List<Document> docs = future.get();
        ImmutableList.Builder<ChangeData> result =
            ImmutableList.builderWithExpectedSize(docs.size());
        for (Document doc : docs) {
          result.add(toChangeData(fields(doc, fields), fields, idField.getName()));
        }
        return result.build();
      } catch (InterruptedException e) {
        close();
        throw new StorageException(e);
      } catch (ExecutionException e) {
        Throwables.throwIfUnchecked(e.getCause());
        throw new StorageException(e.getCause());
      }
    }

    @Override
    public void close() {
      future.cancel(false /* do not interrupt Lucene */);
    }
  }

  private static ListMultimap<String, IndexableField> fields(Document doc, Set<String> fields) {
    ListMultimap<String, IndexableField> stored =
        MultimapBuilder.hashKeys(fields.size()).arrayListValues(4).build();
    for (IndexableField f : doc) {
      String name = f.name();
      if (fields.contains(name)) {
        stored.put(name, f);
      }
    }
    return stored;
  }

  private ChangeData toChangeData(
      ListMultimap<String, IndexableField> doc, Set<String> fields, String idFieldName) {
    ChangeData cd;
    // Either change or the ID field was guaranteed to be included in the call
    // to fields() above.
    IndexableField cb = Iterables.getFirst(doc.get(CHANGE_FIELD), null);
    if (cb != null) {
      BytesRef proto = cb.binaryValue();
      cd = changeDataFactory.create(parseProtoFrom(proto, ChangeProtoConverter.INSTANCE));
    } else {
      IndexableField f = Iterables.getFirst(doc.get(idFieldName), null);

      // IndexUtils#changeFields ensures either CHANGE or PROJECT is always present.
      IndexableField project = doc.get(PROJECT.getName()).iterator().next();
      cd = changeDataFactory.create(Project.nameKey(project.stringValue()), extractor.extract(f));
    }

    // Any decoding that is done here must also be done in {@link ElasticChangeIndex}.

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
    if (fields.contains(REVIEWER_BY_EMAIL_FIELD)) {
      decodeReviewersByEmail(doc, cd);
    }
    if (fields.contains(PENDING_REVIEWER_FIELD)) {
      decodePendingReviewers(doc, cd);
    }
    if (fields.contains(PENDING_REVIEWER_BY_EMAIL_FIELD)) {
      decodePendingReviewersByEmail(doc, cd);
    }
    decodeSubmitRecords(
        doc, SUBMIT_RECORD_STRICT_FIELD, ChangeField.SUBMIT_RULE_OPTIONS_STRICT, cd);
    decodeSubmitRecords(
        doc, SUBMIT_RECORD_LENIENT_FIELD, ChangeField.SUBMIT_RULE_OPTIONS_LENIENT, cd);
    if (fields.contains(REF_STATE_FIELD)) {
      decodeRefStates(doc, cd);
    }
    if (fields.contains(REF_STATE_PATTERN_FIELD)) {
      decodeRefStatePatterns(doc, cd);
    }

    decodeUnresolvedCommentCount(doc, cd);
    decodeTotalCommentCount(doc, cd);
    return cd;
  }

  private void decodePatchSets(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    List<PatchSet> patchSets = decodeProtos(doc, PATCH_SET_FIELD, PatchSetProtoConverter.INSTANCE);
    if (!patchSets.isEmpty()) {
      // Will be an empty list for schemas prior to when this field was stored;
      // this cannot be valid since a change needs at least one patch set.
      cd.setPatchSets(patchSets);
    }
  }

  private void decodeApprovals(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    cd.setCurrentApprovals(
        decodeProtos(doc, APPROVAL_FIELD, PatchSetApprovalProtoConverter.INSTANCE));
  }

  private void decodeChangedLines(ListMultimap<String, IndexableField> doc, ChangeData cd) {
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

  private void decodeMergeable(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    IndexableField f = Iterables.getFirst(doc.get(MERGEABLE_FIELD), null);
    if (f != null && !skipFields.contains(MERGEABLE_FIELD)) {
      String mergeable = f.stringValue();
      if ("1".equals(mergeable)) {
        cd.setMergeable(true);
      } else if ("0".equals(mergeable)) {
        cd.setMergeable(false);
      }
    }
  }

  private void decodeReviewedBy(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    Collection<IndexableField> reviewedBy = doc.get(REVIEWEDBY_FIELD);
    if (reviewedBy.size() > 0) {
      Set<Account.Id> accounts = Sets.newHashSetWithExpectedSize(reviewedBy.size());
      for (IndexableField r : reviewedBy) {
        int id = r.numericValue().intValue();
        if (reviewedBy.size() == 1 && id == ChangeField.NOT_REVIEWED) {
          break;
        }
        accounts.add(Account.id(id));
      }
      cd.setReviewedBy(accounts);
    }
  }

  private void decodeHashtags(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    Collection<IndexableField> hashtag = doc.get(HASHTAG_FIELD);
    Set<String> hashtags = Sets.newHashSetWithExpectedSize(hashtag.size());
    for (IndexableField r : hashtag) {
      hashtags.add(r.binaryValue().utf8ToString());
    }
    cd.setHashtags(hashtags);
  }

  private void decodeStar(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    Collection<IndexableField> star = doc.get(STAR_FIELD);
    ListMultimap<Account.Id, String> stars = MultimapBuilder.hashKeys().arrayListValues().build();
    for (IndexableField r : star) {
      StarredChangesUtil.StarField starField = StarredChangesUtil.StarField.parse(r.stringValue());
      if (starField != null) {
        stars.put(starField.accountId(), starField.label());
      }
    }
    cd.setStars(stars);
  }

  private void decodeReviewers(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    cd.setReviewers(
        ChangeField.parseReviewerFieldValues(
            cd.getId(),
            FluentIterable.from(doc.get(REVIEWER_FIELD)).transform(IndexableField::stringValue)));
  }

  private void decodeReviewersByEmail(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    cd.setReviewersByEmail(
        ChangeField.parseReviewerByEmailFieldValues(
            cd.getId(),
            FluentIterable.from(doc.get(REVIEWER_BY_EMAIL_FIELD))
                .transform(IndexableField::stringValue)));
  }

  private void decodePendingReviewers(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    cd.setPendingReviewers(
        ChangeField.parseReviewerFieldValues(
            cd.getId(),
            FluentIterable.from(doc.get(PENDING_REVIEWER_FIELD))
                .transform(IndexableField::stringValue)));
  }

  private void decodePendingReviewersByEmail(
      ListMultimap<String, IndexableField> doc, ChangeData cd) {
    cd.setPendingReviewersByEmail(
        ChangeField.parseReviewerByEmailFieldValues(
            cd.getId(),
            FluentIterable.from(doc.get(PENDING_REVIEWER_BY_EMAIL_FIELD))
                .transform(IndexableField::stringValue)));
  }

  private void decodeSubmitRecords(
      ListMultimap<String, IndexableField> doc,
      String field,
      SubmitRuleOptions opts,
      ChangeData cd) {
    ChangeField.parseSubmitRecords(
        Collections2.transform(doc.get(field), f -> f.binaryValue().utf8ToString()), opts, cd);
  }

  private void decodeRefStates(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    cd.setRefStates(copyAsBytes(doc.get(REF_STATE_FIELD)));
  }

  private void decodeRefStatePatterns(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    cd.setRefStatePatterns(copyAsBytes(doc.get(REF_STATE_PATTERN_FIELD)));
  }

  private void decodeUnresolvedCommentCount(
      ListMultimap<String, IndexableField> doc, ChangeData cd) {
    decodeIntField(doc, UNRESOLVED_COMMENT_COUNT_FIELD, cd::setUnresolvedCommentCount);
  }

  private void decodeTotalCommentCount(ListMultimap<String, IndexableField> doc, ChangeData cd) {
    decodeIntField(doc, TOTAL_COMMENT_COUNT_FIELD, cd::setTotalCommentCount);
  }

  private static void decodeIntField(
      ListMultimap<String, IndexableField> doc, String fieldName, Consumer<Integer> consumer) {
    IndexableField f = Iterables.getFirst(doc.get(fieldName), null);
    if (f != null && f.numericValue() != null) {
      consumer.accept(f.numericValue().intValue());
    }
  }

  private static <T> List<T> decodeProtos(
      ListMultimap<String, IndexableField> doc, String fieldName, ProtoConverter<?, T> converter) {
    return doc.get(fieldName).stream()
        .map(IndexableField::binaryValue)
        .map(bytesRef -> parseProtoFrom(bytesRef, converter))
        .collect(toImmutableList());
  }

  private static <P extends MessageLite, T> T parseProtoFrom(
      BytesRef bytesRef, ProtoConverter<P, T> converter) {
    P message =
        Protos.parseUnchecked(
            converter.getParser(), bytesRef.bytes, bytesRef.offset, bytesRef.length);
    return converter.fromProto(message);
  }

  private static List<byte[]> copyAsBytes(Collection<IndexableField> fields) {
    return fields.stream()
        .map(
            f -> {
              BytesRef ref = f.binaryValue();
              byte[] b = new byte[ref.length];
              System.arraycopy(ref.bytes, ref.offset, b, 0, ref.length);
              return b;
            })
        .collect(toList());
  }
}
