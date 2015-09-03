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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.lucene.LuceneChangeIndex.CUSTOM_CHAR_MAPPING;
import static com.google.gerrit.server.index.IndexRewriter.CLOSED_STATUSES;
import static com.google.gerrit.server.index.IndexRewriter.OPEN_STATUSES;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lucene.CustomMappingAnalyzer;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.ChangeField.ChangeProtoField;
import com.google.gerrit.server.index.ChangeField.PatchSetApprovalProtoField;
import com.google.gerrit.server.index.ChangeField.PatchSetProtoField;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.ChangeSchemas;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexRewriter;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.Schema.Values;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.QueryOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gwtorm.protobuf.ProtobufCodec;
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
import io.searchbox.indices.IndicesExists;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Secondary index implementation using Elasticsearch. */
class ElasticChangeIndex implements ChangeIndex, LifecycleListener {
  private final Logger log =
      LoggerFactory.getLogger(ElasticChangeIndex.class);

  private final String DEFAULT_INDEX_NAME = "gerrit";
  private final String OPEN_CHANGES = "open_changes";
  private final String CLOSED_CHANGES = "closed_changes";

  private final Provider<ReviewDb> db;
  private final ChangeData.Factory changeDataFactory;
  private final FillArgs fillArgs;
  private final IndexCollection indexes;
  private final Schema<ChangeData> schema;
  private final JestHttpClient client;
  private final ElasticQueryBuilder queryBuilder;
  private final boolean refresh;

  private String indexName;

  ElasticChangeIndex(
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
    checkState(!Strings.isNullOrEmpty(url), "index.url must be supplied");

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

    queryBuilder = new ElasticQueryBuilder(
        new CustomMappingAnalyzer(new StandardAnalyzer(
            CharArraySet.EMPTY_SET), CUSTOM_CHAR_MAPPING));

    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(new HttpClientConfig
        .Builder(url)
        .multiThreaded(true)
        .discoveryEnabled(!testMode)
        .discoveryFrequency(1L, TimeUnit.MINUTES)
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
          if (value == null || (value instanceof String) && ((String) value).isEmpty()) {
            continue;
          }
          builder.field(name, value);
        }
      }
    }
    return builder.endObject().string();
  }

  private static <T> List<T> decodeProtos(JsonObject doc, String fieldName,
      ProtobufCodec<T> codec) {
    JsonArray array = doc.getAsJsonArray(fieldName);
    if (array.size() == 0) {
      return Collections.emptyList();
    }
    List<T> result = new ArrayList<>(array.size());
    for (JsonElement item : array) {
      result.add(codec.decode(Base64.decodeBase64(item.toString())));
    }
    return result;
  }

  private ChangeData toChangeData(JsonElement json) {
    JsonObject source = json.getAsJsonObject().get("_source").getAsJsonObject();
    JsonElement c = source.get(ChangeField.CHANGE.getName());

    if (c == null) {
      int id = source.get(ChangeField.LEGACY_ID2.getName()).getAsInt();
      return changeDataFactory.create(db.get(), new Change.Id(id));
    }

    ChangeData cd = changeDataFactory.create(db.get(),
        ChangeProtoField.CODEC.decode(Base64.decodeBase64(c.getAsString())));

    // Patch sets.
    cd.setPatchSets(decodeProtos(
        source, ChangeField.PATCH_SET.getName(), PatchSetProtoField.CODEC));

    // Approvals.
    cd.setCurrentApprovals(decodeProtos(
        source, ChangeField.APPROVAL.getName(), PatchSetApprovalProtoField.CODEC));

    // Changed lines.
    int added = source.get(ChangeField.ADDED.getName()).getAsInt();
    int deleted = source.get(ChangeField.DELETED.getName()).getAsInt();
    if (added != 0 && deleted != 0) {
      cd.setChangedLines(added, deleted);
    }

    // Mergeable.
    String mergeable = source.get(ChangeField.MERGEABLE.getName()).getAsString();
    if ("1".equals(mergeable)) {
      cd.setMergeable(true);
    } else if ("0".equals(mergeable)) {
      cd.setMergeable(false);
    }

    // Reviewed-by.
    JsonArray reviewedBy = source.get(ChangeField.REVIEWEDBY.getName()).getAsJsonArray();
    if (reviewedBy.size() > 0) {
      Set<Account.Id> accounts =
          Sets.newHashSetWithExpectedSize(reviewedBy.size());
      for (int i = 0; i < reviewedBy.size() ; i++) {
        int a_id = reviewedBy.get(i).getAsInt();
        if (reviewedBy.size() == 1 && a_id == ChangeField.NOT_REVIEWED) {
          break;
        }
        accounts.add(new Account.Id(a_id));
      }
      cd.setReviewedBy(accounts);
    }

    return cd;
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

  private String getMappings() {
    return "{\"mappings\" : " + getMappingProperties("open_changes") + ","
        + getMappingProperties("closed_changes") + "}";
  }

  private static String setMappingField(FieldDef<?,?> f) {
    String name = f.getName();
    FieldType<?> type = f.getType();
    if (type == FieldType.EXACT) {
      return "\"" + name + "\":{\"type\":\"string\","
          + "\"fields\":{\"key\":{\"type\":\"string\",\"index\":\"not_analyzed\"}}}";
    } else if (type == FieldType.TIMESTAMP) {
            return "\"" + name + "\":{\"type\":\"date\","
        + "\"format\":\"dateOptionalTime\"}";
    } else if (type == FieldType.INTEGER || type == FieldType.INTEGER_RANGE
        || type == FieldType.LONG) {
      return "\"" + name + "\":{\"type\":\"long\"}";
    } else if (type == FieldType.PREFIX || type == FieldType.FULL_TEXT
        || type == FieldType.STORED_ONLY) {
      return "\"" + name + "\":{\"type\":\"string\"}";
    } else {
      return "\"" + name + "\":{\"type\":\"" + type + "\"}";
    }
  }

  public static String getMappingProperties(String type) {
    Schema<ChangeData> schema = ChangeSchemas.getLatest();
    String properties = "{\"" + type + "\":{\"properties\":{";
    List<String> p = Lists.newArrayListWithCapacity(schema.getFields().size());
    for (FieldDef<?, ?> f : schema.getFields().values()) {
      p.add(setMappingField(f));
    }
    properties += StringUtils.join(p, ",");
    properties += "}}}";
    return properties;
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
    // Delete the index, if it exists.
    JestResult result = client.execute(
        new IndicesExists.Builder(indexName).build());
    if (result.isSucceeded()) {
      result = client.execute(
          new DeleteIndex.Builder(indexName).build());
      if (!result.isSucceeded()) {
        throw new IOException(String.format(
            "Failed to delete index %s: %s", indexName,
            result.getErrorMessage()));
      }
    }

    // Recreate the index.
    result = client.execute(
        new CreateIndex.Builder(indexName).settings(getMappings()).build());
    if (!result.isSucceeded()) {
      String error = String.format("Failed to create index %s: %s",
          indexName, result.getErrorMessage());
      log.error(error);
      throw new IOException(error);
    }
  }

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p, QueryOptions opts)
      throws QueryParseException {
    Set<Change.Status> statuses = IndexRewriter.getPossibleStatus(p);
    List<String> indexes = Lists.newArrayListWithCapacity(2);
    if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()) {
      indexes.add(OPEN_CHANGES);
    }
    if (!Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
      indexes.add(CLOSED_CHANGES);
    }
    return new QuerySource(indexes, p, opts);
  }

  @Override
  public void markReady(boolean ready) throws IOException {
  }

  private class QuerySource implements ChangeDataSource {
    private final Search search;

    public QuerySource(List<String> types, Predicate<ChangeData> p,
        QueryOptions opts) throws QueryParseException {
      List<Sort> sorts = ImmutableList.of(
          new Sort(ChangeField.UPDATED.getName(), Sorting.DESC),
          new Sort(ChangeField.LEGACY_ID2.getName(), Sorting.DESC));
      for (Sort sort : sorts) {
        sort.setIgnoreUnmapped();
      }
      QueryBuilder qb = queryBuilder.toQueryBuilder(p);
      search = new Search.Builder(
          new SearchSourceBuilder()
            .query(qb)
            .from(opts.start())
            .size(opts.limit())
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

    @Override
    public ResultSet<ChangeData> read() throws OrmException {
      try {
        List<ChangeData> results = Collections.emptyList();
        JestResult result = client.execute(search);
        if (result.isSucceeded()) {
          JsonObject obj = result.getJsonObject().getAsJsonObject("hits");
          if (obj.get("hits") != null) {
            JsonArray json = obj.getAsJsonArray("hits");
            results = Lists.newArrayListWithCapacity(json.size());
            for (int i = 0; i < json.size(); i++) {
              results.add(toChangeData(json.get(i)));
            }
          }
        } else {
          String error = result.getErrorMessage();
          log.error(error);
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
