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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lucene.CustomMappingAnalyzer;
import com.google.gerrit.lucene.QueryBuilder;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.IndexCollection;
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
import io.searchbox.core.search.sort.Sort;
import io.searchbox.core.search.sort.Sort.Sorting;
import io.searchbox.indices.CreateIndex;
import io.searchbox.indices.DeleteIndex;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
  private final boolean refresh;

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

    // By default Elasticsearch has a 1s delay before changes are available in
    // the index.  Setting refresh(true) on calls to the index makes the index
    // refresh immediately.
    //
    // Discovery should be disabled during test mode to prevent spurious
    // connection failures caused by the client starting up and being ready
    // before the test node.
    //
    // This setting should only be set to true during testing, and is not
    // documented.
    boolean testMode = cfg.getBoolean("index", "elasticsearch", "test", false);
    refresh = testMode;

    queryBuilder = new ElasticsearchQueryBuilder(
        new CustomMappingAnalyzer(new StandardAnalyzer(
            CharArraySet.EMPTY_SET), CUSTOM_CHAR_MAPPING));

    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(new HttpClientConfig
        .Builder(url)
        .multiThreaded(true)
        .discoveryEnabled(!testMode)
        .discoveryFrequency(1l, TimeUnit.MINUTES)
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
    client.shutdownClient();
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

  private Delete delete(String type, Change.Id c) {
    String id = c.toString();
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

    Bulk bulk = new Bulk.Builder()
      .addAction(insert(insertIndex, cd))
      .addAction(delete(deleteIndex, cd.getId()))
      .refresh(refresh)
      .build();
    JestResult result = client.execute(bulk);
    if (!result.isSucceeded()) {
      throw new IOException(String.format(
          "Failed to replace change %s in index %s: %s", cd.getId(), indexName,
          result.getErrorMessage()));
    }
  }

  @Override
  public void delete(Change.Id c) throws IOException {
    Bulk bulk = new Bulk.Builder()
      .addAction(delete(OPEN_CHANGES, c))
      .addAction(delete(CLOSED_CHANGES, c))
      .refresh(refresh)
      .build();
    JestResult result = client.execute(bulk);
    if (!result.isSucceeded()) {
      throw new IOException(String.format(
          "Failed to delete change %s in index %s: %s", c, indexName,
          result.getErrorMessage()));
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
      log.warn(String.format("Failed to delete index %s: %s", indexName,
          result.getErrorMessage()));
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
    return new QuerySource(indexes, p, start, limit);
  }

  @Override
  public void markReady(boolean ready) throws IOException {
  }

  private class QuerySource implements ChangeDataSource {
    private final Search search;

    private QueryStringQueryBuilder buildQuery(Predicate<ChangeData> p)
        throws QueryParseException {
      String q = queryBuilder.toQuery(p).toString();
      return QueryBuilders.queryString(q);
    }

    public QuerySource(List<String> types, Predicate<ChangeData> p,
        int start, int limit) throws QueryParseException {
      List<Sort> sorts = ImmutableList.of(
          new Sort(ChangeField.UPDATED.getName(), Sorting.DESC),
          new Sort(ChangeField.LEGACY_ID.getName(), Sorting.DESC));
      search = new Search.Builder(
          new SearchSourceBuilder()
            .query(buildQuery(p))
            .from(start)
            .size(limit)
            .toString())
        .addType(types)
        .addSort(sorts)
        .addIndex(indexName)
        .build();
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
      return search.toString();
    }
  }
}
