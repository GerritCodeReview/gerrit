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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.GerritServerConfig;
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
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import io.searchbox.client.JestResult;
import io.searchbox.core.Bulk;
import io.searchbox.core.Bulk.Builder;
import io.searchbox.core.Search;
import io.searchbox.core.search.sort.Sort;
import io.searchbox.core.search.sort.Sort.Sorting;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticGroupIndex extends AbstractElasticIndex<AccountGroup.UUID, AccountGroup>
    implements GroupIndex {
  public static class GroupMapping {
    MappingProperties groups;

    public GroupMapping(Schema<AccountGroup> schema) {
      this.groups = ElasticMapping.createMapping(schema);
    }
  }

  public static final String GROUPS = "groups";

  private static final Logger log = LoggerFactory.getLogger(ElasticGroupIndex.class);

  private final GroupMapping mapping;
  private final Provider<GroupCache> groupCache;

  @AssistedInject
  ElasticGroupIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Provider<GroupCache> groupCache,
      JestClientBuilder clientBuilder,
      @Assisted Schema<AccountGroup> schema) {
    // No parts of FillArgs are currently required, just use null.
    super(cfg, null, sitePaths, schema, clientBuilder, GROUPS);
    this.groupCache = groupCache;
    this.mapping = new GroupMapping(schema);
  }

  @Override
  public void replace(AccountGroup group) throws IOException {
    Bulk bulk =
        new Bulk.Builder()
            .defaultIndex(indexName)
            .defaultType(GROUPS)
            .addAction(insert(GROUPS, group))
            .refresh(true)
            .build();
    JestResult result = client.execute(bulk);
    if (!result.isSucceeded()) {
      throw new IOException(
          String.format(
              "Failed to replace group %s in index %s: %s",
              group.getGroupUUID().get(), indexName, result.getErrorMessage()));
    }
  }

  @Override
  public DataSource<AccountGroup> getSource(Predicate<AccountGroup> p, QueryOptions opts)
      throws QueryParseException {
    return new QuerySource(p, opts);
  }

  @Override
  protected Builder addActions(Builder builder, AccountGroup.UUID c) {
    return builder.addAction(delete(GROUPS, c));
  }

  @Override
  protected String getMappings() {
    ImmutableMap<String, GroupMapping> mappings = ImmutableMap.of("mappings", mapping);
    return gson.toJson(mappings);
  }

  @Override
  protected String getId(AccountGroup group) {
    return group.getGroupUUID().get();
  }

  private class QuerySource implements DataSource<AccountGroup> {
    private final Search search;
    private final Set<String> fields;

    QuerySource(Predicate<AccountGroup> p, QueryOptions opts) throws QueryParseException {
      QueryBuilder qb = queryBuilder.toQueryBuilder(p);
      fields = IndexUtils.groupFields(opts);
      SearchSourceBuilder searchSource =
          new SearchSourceBuilder()
              .query(qb)
              .from(opts.start())
              .size(opts.limit())
              .fields(Lists.newArrayList(fields));

      Sort sort = new Sort(GroupField.UUID.getName(), Sorting.ASC);
      sort.setIgnoreUnmapped();

      search =
          new Search.Builder(searchSource.toString())
              .addType(GROUPS)
              .addIndex(indexName)
              .addSort(ImmutableList.of(sort))
              .build();
    }

    @Override
    public int getCardinality() {
      return 10;
    }

    @Override
    public ResultSet<AccountGroup> read() throws OrmException {
      try {
        List<AccountGroup> results = Collections.emptyList();
        JestResult result = client.execute(search);
        if (result.isSucceeded()) {
          JsonObject obj = result.getJsonObject().getAsJsonObject("hits");
          if (obj.get("hits") != null) {
            JsonArray json = obj.getAsJsonArray("hits");
            results = Lists.newArrayListWithCapacity(json.size());
            for (int i = 0; i < json.size(); i++) {
              results.add(toAccountGroup(json.get(i)));
            }
          }
        } else {
          log.error(result.getErrorMessage());
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

    @Override
    public String toString() {
      return search.toString();
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
