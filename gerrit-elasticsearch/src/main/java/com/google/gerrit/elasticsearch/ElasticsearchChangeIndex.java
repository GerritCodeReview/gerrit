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

import static com.google.gerrit.server.index.IndexRewriteImpl.CLOSED_STATUSES;
import static com.google.gerrit.server.index.IndexRewriteImpl.OPEN_STATUSES;
//import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
//import static org.elasticsearch.search.sort.SortBuilders.fieldSort;





import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lucene.QueryBuilder;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.ChangeField;
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
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.SortKeyPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;
/*import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.Version;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BaseQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;*/
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;


import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.http.JestHttpClient;
import io.searchbox.indices.CreateIndex;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Secondary index implementation using Elasticsearch. */
class ElasticsearchChangeIndex implements ChangeIndex, LifecycleListener {
  //private final int DEFAULT_PORT = 9300;
  //private final String DEFAULT_CLUSTER = "gerrit";
  //private final int DEFAULT_SHARDS = 12;
  //private final int DEFAULT_REPLICAS = 1;
  private final String INDEX_NAME = "gerrit";
  private final String OPEN_CHANGES = "open_changes";
  private final String CLOSED_CHANGES = "closed_changes";

  private final Provider<ReviewDb> db;
  private final ChangeData.Factory changeDataFactory;
  private final FillArgs fillArgs;
  private final IndexCollection indexes;
  private final Schema<ChangeData> schema;
  //private final Client client;
  private final JestHttpClient client;
  private final Settings settings;
  //private final QueryBuilder queryBuilder;

  private final Logger log = LoggerFactory.getLogger(ElasticsearchChangeIndex.class);

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

    //String cluster = cfg.getString("index", null, "cluster");
    //if (Strings.isNullOrEmpty(cluster)) {
    //  cluster = DEFAULT_CLUSTER;
    //}

    String url = cfg.getString("index", null, "url");
    if (Strings.isNullOrEmpty(url)) {
      throw new IllegalStateException("index.url must be supplied");
    }

    //@SuppressWarnings("deprecation")
    //Version v = Version.LUCENE_CURRENT;
    //queryBuilder = new ElasticsearchQueryBuilder(
    //    schema, new StandardAnalyzer(v, CharArraySet.EMPTY_SET));

    //log.info(String.format("cluster [%s] hostname [%s] port [%d] shards [%d] replicas [%d]",
    //    cluster, hostname, port, shards, replicas));
    /*this.settings = ImmutableSettings.settingsBuilder()
        .put("cluster.name", cluster)
        .put("index.number_of_shards", shards)
        .put("index.number_of_replicas", replicas)
        .put("discovery.enabled", true)
        .build();
    */
    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(new HttpClientConfig
        .Builder(url)
        .multiThreaded(true)
        .discoveryEnabled(true)
        .build());
    client = (JestHttpClient)factory.getObject();
    try {
      client.execute(new CreateIndex.Builder("articles").build());
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @Override
  public void start() {
    indexes.setSearchIndex(this);
    indexes.addWriteIndex(this);
  }

  @Override
  public void stop() {
    //client.close();
  }

  @Override
  public Schema<ChangeData> getSchema() {
    return schema;
  }

  @Override
  public void close() {
    stop();
  }

  /*private XContentBuilder toDoc(ChangeData cd) throws IOException {
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
    return builder.endObject();
  }*/

  private void flush(String type) {
  /*  try {
      client.admin().indices()
          .flush(new FlushRequest(type).force(true))
          .actionGet();
    } catch (IndexMissingException emi) {
      // Ignore
    }*/
  }

  private void insert(String type, ChangeData cd) throws IOException {
    /*String id = cd.getId().toString();
    IndexRequest indexRequest =
        new IndexRequest(INDEX_NAME, type, id).source(toDoc(cd));
    client.index(indexRequest).actionGet();
    flush(type);*/
  }

  private void delete(String type, ChangeData cd) {
    /*String id = cd.getId().toString();
    client.delete(new DeleteRequest(INDEX_NAME, type, id))
        .actionGet();
    flush(type);*/
  }

  @Override
  public void replace(ChangeData cd) throws IOException {
    /*try {
      if (cd.change().getStatus().isOpen()) {
        insert(OPEN_CHANGES, cd);
        delete(CLOSED_CHANGES, cd);
      } else {
        insert(CLOSED_CHANGES, cd);
        delete(OPEN_CHANGES, cd);
      }
    } catch (OrmException e) {
      throw new IOException(e);
    }*/
  }

  @Override
  public void delete(ChangeData cd) throws IOException {
    /*try {
      if (cd.change().getStatus().isOpen()) {
        delete(OPEN_CHANGES, cd);
      } else {
        delete(CLOSED_CHANGES, cd);
      }
    } catch (OrmException e) {
      throw new IOException(e);
    }*/
  }

  @Override
  public void deleteAll() throws IOException {
    // Some of the settings are only honoured when the index is
    // created.  Deleting the index and then recreating it makes
    // sure that these settings get set properly if they have been
    // changed since the index was initially created.
    /*try {
      client.admin().indices()
          .delete(new DeleteIndexRequest(INDEX_NAME))
          .actionGet();
    } catch (IndexMissingException emi) {
      // Ignore. Index might not have been created yet.
    }

    client.admin().indices()
        .prepareCreate(INDEX_NAME)
        .setSettings(settings)
        .execute()
        .actionGet();*/
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
    //return new QuerySource(indexes, toQuery(p), start, limit,
    //    getSorts(schema, p));
    return null;
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

  /*private BaseQueryBuilder toQuery(Predicate<ChangeData> p)
      throws QueryParseException {
    return QueryBuilders.queryString(queryBuilder.toQuery(p).toString());
  }*/

  @Override
  public void markReady(boolean ready) throws IOException {
  }

  /*private class ElasticsearchQueryBuilder extends QueryBuilder {
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
      //if (p.getType() == FieldType.INTEGER) {
      //  String value = p.getValue();
      //  try {
      //    Integer.valueOf(value);
      //  } catch (IllegalArgumentException e) {
      //    throw new QueryParseException("not an integer: " + value);
      //  }
      //  return new TermQuery(new Term(p.getField().getName(), value));
      //} else {
        return super.fieldQuery(p);
      //}
    }

  }*/

  private class QuerySource implements ChangeDataSource {
    //private final SearchRequestBuilder query;

    /*public QuerySource(List<String> indexes, BaseQueryBuilder q, int start,
        int limit, List<FieldSortBuilder> sorts) throws QueryParseException {
      this.query = client.prepareSearch(INDEX_NAME)
          .setFrom(start)
          .setSize(limit)
          .setTypes(indexes.toArray(new String[indexes.size()]))
          .setQuery(q);
      for (FieldSortBuilder sort : sorts) {
        query.addSort(sort);
      }
    }*/

    @Override
    public int getCardinality() {
      return 10;
    }

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      /*try {
        SearchHits hits = query.execute().actionGet().getHits();

        List<ChangeData> results =
            Lists.newArrayListWithCapacity(hits.getHits().length);

        for (SearchHit hit : hits) {
          Integer v = Integer.parseInt(hit.getId());
          results.add(
              changeDataFactory.create(db.get(), new Change.Id(v.intValue())));
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
      } catch (ElasticsearchException e) {
        throw new OrmException(e);
      }*/
      return null;
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
