// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.elasticsearch;

import static com.google.gerrit.lucene.LuceneChangeIndex.CUSTOM_CHAR_MAPPING;
import static com.google.gerrit.server.index.IndexRewriteImpl.CLOSED_STATUSES;
import static com.google.gerrit.server.index.IndexRewriteImpl.OPEN_STATUSES;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lucene.CustomMappingAnalyzer;
import com.google.gerrit.lucene.QueryBuilder;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexPredicate;
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

import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.http.JestHttpClient;
import io.searchbox.core.Bulk;
import io.searchbox.core.Delete;
import io.searchbox.core.Index;
import io.searchbox.core.Search;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.DeleteIndex;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Secondary index implementation using Elasticsearch. */
class ElasticsearchChangeIndex implements ChangeIndex, LifecycleListener {
  private final Logger log =
      LoggerFactory.getLogger(ElasticsearchChangeIndex.class);

  private final String DEFAULT_INDEX_NAME = "gerrit";
  private final String OPEN_CHANGES = "open_changes";
  private final String CLOSED_CHANGES = "closed_changes";

  private final Provider<ReviewDb> db;
  private final ChangeData.Factory changeDataFactory;
  private final FillArgs fillArgs;
  private final IndexCollection indexes;
  private final Schema<ChangeData> schema;
  private final JestHttpClient client;
  private final QueryBuilder queryBuilder;

  private String indexName;

  ElasticsearchChangeIndex(
      @GerritServerConfig Config cfg,
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      FillArgs fillArgs,
      IndexCollection indexes,
      Schema<ChangeData> schema) {

    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.fillArgs = fillArgs;
    this.indexes = indexes;
    this.schema = schema;

    String url = cfg.getString("index", null, "url");
    if (Strings.isNullOrEmpty(url)) {
      throw new IllegalStateException("index.url must be supplied");
    }

    indexName = cfg.getString("index", null, "name");
    if (Strings.isNullOrEmpty(indexName)) {
      indexName = DEFAULT_INDEX_NAME;
    }

    queryBuilder = new ElasticsearchQueryBuilder(
        schema, new CustomMappingAnalyzer(new StandardAnalyzer(
            CharArraySet.EMPTY_SET), CUSTOM_CHAR_MAPPING));

    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(new HttpClientConfig
        .Builder(url)
        .multiThreaded(true)
        .discoveryEnabled(true)
        .build());
    client = (JestHttpClient)factory.getObject();
  }

  @Override
  public void start() {
    indexes.setSearchIndex(this);
    indexes.addWriteIndex(this);
  }

  @Override
  public void stop() {
    //TODO
  }

  @Override
  public Schema<ChangeData> getSchema() {
    return schema;
  }

  @Override
  public void close() {
    stop();
  }

  private String toDoc(ChangeData cd) throws IOException {
    XContentBuilder builder = jsonBuilder().startObject();
    for (Values<ChangeData> values : schema.buildFields(cd, fillArgs)) {
      String name = values.getField().getName();
      if (values.getField().isRepeatable()) {
        builder.array(name, values.getValues());
      } else {
        for (Object value : values.getValues()) {
          builder.field(name, value);
        }
      }
    }
    return builder.endObject().string();
  }

  private Index insert(String type, ChangeData cd) throws IOException {
    String id = cd.getId().toString();
    String doc = toDoc(cd);
    return new Index.Builder(doc)
      .index(indexName)
      .type(type)
      .id(id)
      .build();
  }

  private Delete delete(String type, ChangeData cd) {
    String id = cd.getId().toString();
    return new Delete.Builder(id)
      .index(indexName)
      .type(type)
      .build();
  }

  @Override
  public void replace(ChangeData cd) throws IOException {
    String deleteIndex;
    String insertIndex;

    try {
      if (cd.change().getStatus().isOpen()) {
        insertIndex = OPEN_CHANGES;
        deleteIndex = CLOSED_CHANGES;
      } else {
        insertIndex = CLOSED_CHANGES;
        deleteIndex = OPEN_CHANGES;
      }
    } catch (OrmException e) {
      throw new IOException(e);
    }

    Index insert = insert(insertIndex, cd);
    Delete delete = delete(deleteIndex, cd);

    Bulk bulk = new Bulk.Builder()
      .addAction(insert)
      .addAction(delete)
      .build();
    JestResult result = client.execute(bulk);
    if (!result.isSucceeded()) {
      throw new IOException(
          String.format("Failed to replace change %s in index %s: %s",
              cd.getId(), indexName, result.getErrorMessage()));
    }
  }

  @Override
  public void delete(ChangeData cd) throws IOException {
    Delete delete;
    try {
      if (cd.change().getStatus().isOpen()) {
        delete = delete(OPEN_CHANGES, cd);
      } else {
        delete = delete(CLOSED_CHANGES, cd);
      }
    } catch (OrmException e) {
      throw new IOException(e);
    }
    JestResult result = client.execute(delete);
    if (!result.isSucceeded()) {
      throw new IOException(
          String.format("Failed to delete change %s in index %s: %s",
              cd.getId(), indexName, result.getErrorMessage()));
    }
  }

  @Override
  public void deleteAll() throws IOException {
    // Delete the index.
    JestResult result = client.execute(
        new DeleteIndex.Builder(indexName).build());
    if (!result.isSucceeded()) {
      // The index may not exist yet, so we don't throw any
      // exception, just log it.
      log.warn(String.format("Failed to delete index %s: %s",
          indexName, result.getErrorMessage()));
    }

    // Recreate the index.
    result = client.execute(
        new CreateIndex.Builder(indexName).build());
    if (!result.isSucceeded()) {
      String error = String.format("Failed to create index %s: %s",
          indexName, result.getErrorMessage());
      log.error(error);
      throw new IOException(error);
    }
  }

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p, int start,
      int limit) throws QueryParseException {
    Set<Change.Status> statuses = IndexRewriteImpl.getPossibleStatus(p);
    List<String> indexes = Lists.newArrayListWithCapacity(2);
    if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()) {
      indexes.add(OPEN_CHANGES);
    }
    if (!Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
      indexes.add(CLOSED_CHANGES);
    }
    return new QuerySource(indexes, toQuery(p), start, limit);
  }

  /*@SuppressWarnings("deprecation")
  private static List<FieldSortBuilder> getSorts(Schema<ChangeData> schema,
      Predicate<ChangeData> p) {
    if (SortKeyPredicate.hasSortKeyField(schema)) {
      SortOrder order = ChangeQueryBuilder.hasNonTrivialSortKeyAfter(schema, p)
          ? SortOrder.ASC : SortOrder.DESC;
      return ImmutableList.of(
          fieldSort(ChangeField.SORTKEY.getName()).order(order));
    } else {
      return ImmutableList.of(
          fieldSort(ChangeField.UPDATED.getName()).order(SortOrder.DESC),
          fieldSort(ChangeField.LEGACY_ID.getName()).order(SortOrder.DESC));
    }
    return null;
  }*/

  private String toQuery(Predicate<ChangeData> p)
      throws QueryParseException {
    String s = queryBuilder.toQuery(p).toString();
    return s;//return new SearchSourceBuilder().query(s);
  }

  @Override
  public void markReady(boolean ready) throws IOException {
  }

  private class ElasticsearchQueryBuilder extends QueryBuilder {
    public ElasticsearchQueryBuilder(Schema<ChangeData> schema, Analyzer analyzer) {
      super(schema, analyzer);
    }

    @Override
    protected Query fieldQuery(IndexPredicate<ChangeData> p)
        throws QueryParseException {
      // QueryBuilder encodes integer fields as prefix coded bits,
      // which elasticsearch's queryString can't handle.
      // Create integer terms with string representations instead.
      // For other terms, fall back to QueryBuilder's implementation.
      if (p.getType() == FieldType.INTEGER) {
        String value = p.getValue();
        if (Ints.tryParse(value) == null) {
          throw new QueryParseException("not an integer: " + value);
        }
        return new TermQuery(new Term(p.getField().getName(), value));
      } else {
        return super.fieldQuery(p);
      }
    }
  }

  private class QuerySource implements ChangeDataSource {
    private final Search search;

    public QuerySource(List<String> indexes, String q, int start,
        int limit) throws QueryParseException {

      // TODO: find a better way to build the query
      String query = "{\n" +
          "    \"query\": {\n" +
          "        \"filtered\" : {\n" +
          "            \"query\" : {\n" +
          "                \"query_string\" : {\n" +
          "                    \"query\" : \"" + q + "\"\n" +
          "                }\n" +
          "            }\n"+
          "        }\n" +
          "    }\n" +
          "}";
      Search.Builder builder = new Search.Builder(query);
      for (String type : indexes) {
        builder.addType(type);
      }
      // TODO: add start and limit
      // TODO: add sorts
      search = builder.addIndex(indexName).build();
    }

    @Override
    public int getCardinality() {
      return 10;
    }

    private class Result {
      public String _id;
    }

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      try {
        List<ChangeData> results;
        JestResult result = client.execute(search);
        if (result.isSucceeded()) {
          // TODO: find a better way to get the results out
          List<Result> objects = result.getSourceAsObjectList(Result.class);
          results = Lists.newArrayListWithCapacity(objects.size());
          for (Result r : objects) {
            Integer v = Integer.parseInt(r._id);
            results.add(
                changeDataFactory.create(db.get(), new Change.Id(v.intValue())));
          }
        } else {
          String error = result.getErrorMessage();
          log.error(error);
          results = Collections.emptyList();
        }
        final List<ChangeData> r = Collections.unmodifiableList(results);
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
      }
    }

    @Override
    public boolean hasChange() {
      return false;
    }

    @Override
    public String toString() {
      return "";//query.toString();
    }
  }
}
