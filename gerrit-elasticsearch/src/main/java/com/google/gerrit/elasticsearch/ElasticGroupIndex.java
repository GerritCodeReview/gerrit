// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.elasticsearch.builders.QueryBuilder;
import com.google.gerrit.elasticsearch.builders.SearchSourceBuilder;
import com.google.gerrit.elasticsearch.bulk.BulkRequest;
import com.google.gerrit.elasticsearch.bulk.IndexRequest;
import com.google.gerrit.elasticsearch.bulk.UpdateRequest;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticGroupIndex extends AbstractElasticIndex<AccountGroup.UUID, AccountGroup>
    implements GroupIndex {
  private static final Logger log = LoggerFactory.getLogger(ElasticGroupIndex.class);

  static class GroupMapping {
    final MappingProperties groups;

    GroupMapping(Schema<AccountGroup> schema, ElasticQueryAdapter adapter) {
      this.groups = ElasticMapping.createMapping(schema, adapter);
    }
  }

  private static final String GROUPS = "groups";

  private final GroupMapping mapping;
  private final Provider<GroupCache> groupCache;
  private final Schema<AccountGroup> schema;

  @AssistedInject
  ElasticGroupIndex(
      ElasticConfiguration cfg,
      SitePaths sitePaths,
      Provider<GroupCache> groupCache,
      ElasticRestClientProvider client,
      @Assisted Schema<AccountGroup> schema) {
    super(cfg, sitePaths, schema, client, GROUPS);
    this.groupCache = groupCache;
    this.mapping = new GroupMapping(schema, client.adapter());
    this.schema = schema;
  }

  @Override
  public void replace(AccountGroup group) throws IOException {
    BulkRequest bulk =
        new IndexRequest(getId(group), indexName, type, client.adapter())
            .add(new UpdateRequest<>(schema, group));

    String uri = getURI(type, BULK);
    Response response = postRequest(uri, bulk, getRefreshParam());
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      throw new IOException(
          String.format(
              "Failed to replace group %s in index %s: %s",
              group.getGroupUUID().get(), indexName, statusCode));
    }
  }

  @Override
  public DataSource<AccountGroup> getSource(Predicate<AccountGroup> p, QueryOptions opts)
      throws QueryParseException {
    return new QuerySource(p, opts);
  }

  @Override
  protected String getDeleteActions(AccountGroup.UUID g) {
    return delete(type, g);
  }

  @Override
  protected String getMappings() {
    return getMappingsForSingleType(GROUPS, mapping.groups);
  }

  @Override
  protected String getId(AccountGroup group) {
    return group.getGroupUUID().get();
  }

  private class QuerySource implements DataSource<AccountGroup> {
    private final String search;
    private final Set<String> fields;

    QuerySource(Predicate<AccountGroup> p, QueryOptions opts) throws QueryParseException {
      QueryBuilder qb = queryBuilder.toQueryBuilder(p);
      fields = IndexUtils.groupFields(opts);
      SearchSourceBuilder searchSource =
          new SearchSourceBuilder(client.adapter())
              .query(qb)
              .from(opts.start())
              .size(opts.limit())
              .fields(Lists.newArrayList(fields));

      JsonArray sortArray = getSortArray(GroupField.UUID.getName());
      search = getSearch(searchSource, sortArray);
    }

    @Override
    public int getCardinality() {
      return 10;
    }

    @Override
    public ResultSet<AccountGroup> read() throws OrmException {
      try {
        List<AccountGroup> results = Collections.emptyList();
        String uri = getURI(type, SEARCH);
        Response response = postRequest(uri, search);
        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
          String content = getContent(response);
          JsonObject obj =
              new JsonParser().parse(content).getAsJsonObject().getAsJsonObject("hits");
          if (obj.get("hits") != null) {
            JsonArray json = obj.getAsJsonArray("hits");
            results = Lists.newArrayListWithCapacity(json.size());
            for (int i = 0; i < json.size(); i++) {
              results.add(toAccountGroup(json.get(i)));
            }
          }
        } else {
          log.error(statusLine.getReasonPhrase());
        }
        final List<AccountGroup> r = Collections.unmodifiableList(results);
        return new ResultSet<AccountGroup>() {
          @Override
          public Iterator<AccountGroup> iterator() {
            return r.iterator();
          }

          @Override
          public List<AccountGroup> toList() {
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

    private AccountGroup toAccountGroup(JsonElement json) {
      JsonElement source = json.getAsJsonObject().get("_source");
      if (source == null) {
        source = json.getAsJsonObject().get("fields");
      }

      AccountGroup.UUID uuid =
          new AccountGroup.UUID(
              source.getAsJsonObject().get(GroupField.UUID.getName()).getAsString());
      // Use the GroupCache rather than depending on any stored fields in the
      // document (of which there shouldn't be any).
      return groupCache.get().get(uuid);
    }
  }
}
