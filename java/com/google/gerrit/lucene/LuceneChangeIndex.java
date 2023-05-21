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
import static com.google.gerrit.server.index.change.ChangeField.NUMERIC_ID_STR_SPEC;
import static com.google.gerrit.server.index.change.ChangeField.PROJECT_SPEC;
import static com.google.gerrit.server.index.change.ChangeIndexRewriter.CLOSED_STATUSES;
import static com.google.gerrit.server.index.change.ChangeIndexRewriter.OPEN_STATUSES;
import static java.util.Objects.requireNonNull;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.converter.ChangeProtoConverter;
import com.google.gerrit.entities.converter.ProtoConverter;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.PaginationType;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.SchemaFieldDefs.SchemaField;
import com.google.gerrit.index.query.FieldBundle;
import com.google.gerrit.index.query.HasCardinality;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.ResultSet;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexExecutor;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.index.options.AutoFlush;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
import org.apache.lucene.store.ByteBuffersDirectory;
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

  static final String UPDATED_SORT_FIELD = sortFieldName(ChangeField.UPDATED_SPEC);
  static final String MERGED_ON_SORT_FIELD = sortFieldName(ChangeField.MERGED_ON_SPEC);
  static final String ID_STR_SORT_FIELD = sortFieldName(ChangeField.NUMERIC_ID_STR_SPEC);

  private static final String CHANGES = "changes";
  private static final String CHANGES_OPEN = "open";
  private static final String CHANGES_CLOSED = "closed";
  private static final String CHANGE_FIELD = ChangeField.CHANGE_SPEC.getName();

  static Term idTerm(ChangeData cd) {
    return idTerm(cd.getVirtualId());
  }

  static Term idTerm(Change.Id id) {
    return QueryBuilder.stringTerm(NUMERIC_ID_STR_SPEC.getName(), Integer.toString(id.get()));
  }

  private final ListeningExecutorService executor;
  private final ChangeData.Factory changeDataFactory;
  private final Schema<ChangeData> schema;
  private final QueryBuilder<ChangeData> queryBuilder;
  private final ChangeSubIndex openIndex;
  private final ChangeSubIndex closedIndex;
  private final ImmutableSet<String> skipFields;

  @Inject
  LuceneChangeIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      @IndexExecutor(INTERACTIVE) ListeningExecutorService executor,
      ChangeData.Factory changeDataFactory,
      @Assisted Schema<ChangeData> schema,
      AutoFlush autoFlush)
      throws IOException {
    this.executor = executor;
    this.changeDataFactory = changeDataFactory;
    this.schema = schema;
    this.skipFields =
        MergeabilityComputationBehavior.fromConfig(cfg).includeInIndex()
            ? ImmutableSet.of()
            : ImmutableSet.of(ChangeField.MERGEABLE_SPEC.getName());

    GerritIndexWriterConfig openConfig = new GerritIndexWriterConfig(cfg, "changes_open");
    GerritIndexWriterConfig closedConfig = new GerritIndexWriterConfig(cfg, "changes_closed");

    queryBuilder = new QueryBuilder<>(schema, openConfig.getAnalyzer());

    SearcherFactory searcherFactory = new SearcherFactory();
    if (LuceneIndexModule.isInMemoryTest(cfg)) {
      openIndex =
          new ChangeSubIndex(
              schema,
              sitePaths,
              new ByteBuffersDirectory(),
              "ramOpen",
              skipFields,
              openConfig,
              searcherFactory,
              autoFlush);
      closedIndex =
          new ChangeSubIndex(
              schema,
              sitePaths,
              new ByteBuffersDirectory(),
              "ramClosed",
              skipFields,
              closedConfig,
              searcherFactory,
              autoFlush);
    } else {
      Path dir = LuceneVersionManager.getDir(sitePaths, CHANGES, schema);
      openIndex =
          new ChangeSubIndex(
              schema,
              sitePaths,
              dir.resolve(CHANGES_OPEN),
              skipFields,
              openConfig,
              searcherFactory,
              autoFlush);
      closedIndex =
          new ChangeSubIndex(
              schema,
              sitePaths,
              dir.resolve(CHANGES_CLOSED),
              skipFields,
              closedConfig,
              searcherFactory,
              autoFlush);
    }
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
    Term id = LuceneChangeIndex.idTerm(cd);
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
  public void insert(ChangeData cd) {
    // toDocument is essentially static and doesn't depend on the specific
    // sub-index, so just pick one.
    Document doc = openIndex.toDocument(cd);
    try {
      if (cd.change().isNew()) {
        openIndex.insert(doc).get();
      } else {
        closedIndex.insert(doc).get();
      }
    } catch (ExecutionException | InterruptedException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void deleteByValue(ChangeData value) {
    delete(ChangeIndex.ENTITY_TO_KEY.apply(value));
  }

  @Override
  public void delete(Change.Id changeId) {
    Term idTerm = LuceneChangeIndex.idTerm(changeId);
    try {
      Futures.allAsList(openIndex.delete(idTerm), closedIndex.delete(idTerm)).get();
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
        new SortField(MERGED_ON_SORT_FIELD, SortField.Type.LONG, true),
        new SortField(ID_STR_SORT_FIELD, SortField.Type.LONG, true));
  }

  private class QuerySource implements ChangeDataSource {
    private final List<ChangeSubIndex> indexes;
    private final Predicate<ChangeData> predicate;
    private final Query query;
    private final QueryOptions opts;
    private final Sort sort;
    private final Function<Document, FieldBundle> rawDocumentMapper;
    private final boolean isSearchAfterPagination;

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
      this.isSearchAfterPagination =
          opts.config().paginationType().equals(PaginationType.SEARCH_AFTER);
    }

    @Override
    public int getCardinality() {
      if (predicate instanceof HasCardinality) {
        return ((HasCardinality) predicate).getCardinality();
      }
      return 10;
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

      final Set<String> fields = IndexUtils.changeFields(opts);
      return new ChangeDataResults(
          executor.submit(
              new Callable<Results>() {
                @Override
                public Results call() throws IOException {
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
      Map<ChangeSubIndex, ScoreDoc> searchAfterBySubIndex;

      try {
        Results r = doRead(IndexUtils.changeFields(opts));
        documents = r.docs;
        searchAfterBySubIndex = r.searchAfterBySubIndex;
      } catch (IOException e) {
        throw new StorageException(e);
      }
      ImmutableList<FieldBundle> fieldBundles =
          documents.stream().map(rawDocumentMapper).collect(toImmutableList());
      return new ResultSet<>() {
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

        @Override
        public Object searchAfter() {
          return searchAfterBySubIndex;
        }
      };
    }

    private Results doRead(Set<String> fields) throws IOException {
      IndexSearcher[] searchers = new IndexSearcher[indexes.size()];
      Map<ChangeSubIndex, ScoreDoc> searchAfterBySubIndex = new HashMap<>();
      try {
        int realPageSize = opts.start() + opts.pageSize();
        if (Integer.MAX_VALUE - opts.pageSize() < opts.start()) {
          realPageSize = Integer.MAX_VALUE;
        }
        List<TopFieldDocs> hits = new ArrayList<>();
        int searchAfterHitsCount = 0;
        for (int i = 0; i < indexes.size(); i++) {
          ChangeSubIndex subIndex = indexes.get(i);
          searchers[i] = subIndex.acquire();
          if (isSearchAfterPagination) {
            ScoreDoc searchAfter = getSearchAfter(subIndex);
            int maxRemainingHits = realPageSize - searchAfterHitsCount;
            if (maxRemainingHits > 0) {
              TopFieldDocs subIndexHits =
                  searchers[i].searchAfter(
                      searchAfter, query, maxRemainingHits, sort, /* doDocScores= */ false);
              searchAfterHitsCount += subIndexHits.scoreDocs.length;
              hits.add(subIndexHits);
              searchAfterBySubIndex.put(
                  subIndex, Iterables.getLast(Arrays.asList(subIndexHits.scoreDocs), searchAfter));
            }
          } else {
            hits.add(searchers[i].search(query, realPageSize, sort));
          }
        }
        TopDocs docs =
            TopDocs.merge(sort, realPageSize, hits.stream().toArray(TopFieldDocs[]::new));

        List<Document> result = new ArrayList<>(docs.scoreDocs.length);
        for (int i = opts.start(); i < docs.scoreDocs.length; i++) {
          ScoreDoc sd = docs.scoreDocs[i];
          result.add(searchers[sd.shardIndex].doc(sd.doc, fields));
        }
        return new Results(result, searchAfterBySubIndex);
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

    /**
     * Returns null for the first page or when pagination type is not {@link
     * PaginationType#SEARCH_AFTER search-after}, otherwise returns the last doc from previous
     * search on the given change sub-index.
     *
     * @param subIndex change sub-index
     * @return the score doc that can be used to page result sets
     */
    @Nullable
    private ScoreDoc getSearchAfter(ChangeSubIndex subIndex) {
      if (isSearchAfterPagination
          && opts.searchAfter() != null
          && opts.searchAfter() instanceof Map
          && ((Map<?, ?>) opts.searchAfter()).get(subIndex) instanceof ScoreDoc) {
        return (ScoreDoc) ((Map<?, ?>) opts.searchAfter()).get(subIndex);
      }
      return null;
    }
  }

  private static class Results {
    List<Document> docs;
    Map<ChangeSubIndex, ScoreDoc> searchAfterBySubIndex;

    public Results(List<Document> docs, Map<ChangeSubIndex, ScoreDoc> searchAfterBySubIndex) {
      this.docs = docs;
      this.searchAfterBySubIndex = searchAfterBySubIndex;
    }
  }

  private class ChangeDataResults implements ResultSet<ChangeData> {
    private final Future<Results> future;
    private final Set<String> fields;
    private Map<ChangeSubIndex, ScoreDoc> searchAfterBySubIndex;

    ChangeDataResults(Future<Results> future, Set<String> fields) {
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
        Results r = future.get();
        List<Document> docs = r.docs;
        searchAfterBySubIndex = r.searchAfterBySubIndex;
        ImmutableList.Builder<ChangeData> result =
            ImmutableList.builderWithExpectedSize(docs.size());
        for (Document doc : docs) {
          result.add(toChangeData(fields(doc, fields), fields, NUMERIC_ID_STR_SPEC.getName()));
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

    @Override
    public Object searchAfter() {
      return searchAfterBySubIndex;
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

      Change.Id id = Change.id(Integer.valueOf(f.stringValue()));
      // IndexUtils#changeFields ensures either CHANGE or PROJECT is always present.
      IndexableField project = doc.get(PROJECT_SPEC.getName()).iterator().next();
      cd = changeDataFactory.create(Project.nameKey(project.stringValue()), id);
    }

    for (SchemaField<ChangeData, ?> field : getSchema().getSchemaFields().values()) {
      if (fields.contains(field.getName()) && doc.get(field.getName()) != null) {
        field.setIfPossible(cd, new LuceneStoredValue(doc.get(field.getName())));
      }
    }
    return cd;
  }

  private static <P extends MessageLite, T> T parseProtoFrom(
      BytesRef bytesRef, ProtoConverter<P, T> converter) {
    P message =
        Protos.parseUnchecked(
            converter.getParser(), bytesRef.bytes, bytesRef.offset, bytesRef.length);
    return converter.fromProto(message);
  }
}
