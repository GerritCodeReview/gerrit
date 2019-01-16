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

import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.elasticsearch.bulk.BulkRequest;
import com.google.gerrit.elasticsearch.bulk.IndexRequest;
import com.google.gerrit.elasticsearch.bulk.UpdateRequest;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.group.GroupField;
import com.google.gerrit.server.index.group.GroupIndex;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.util.Set;
import org.apache.http.HttpStatus;
import org.elasticsearch.client.Response;

public class ElasticGroupIndex extends AbstractElasticIndex<AccountGroup.UUID, InternalGroup>
    implements GroupIndex {
  static class GroupMapping {
    final MappingProperties groups;

    GroupMapping(Schema<InternalGroup> schema, ElasticQueryAdapter adapter) {
      this.groups = ElasticMapping.createMapping(schema, adapter);
    }
  }

  private static final String GROUPS = "groups";

  private final GroupMapping mapping;
  private final Provider<GroupCache> groupCache;
  private final Schema<InternalGroup> schema;

  @Inject
  ElasticGroupIndex(
      ElasticConfiguration cfg,
      SitePaths sitePaths,
      Provider<GroupCache> groupCache,
      ElasticRestClientProvider client,
      @Assisted Schema<InternalGroup> schema) {
    super(cfg, sitePaths, schema, client, GROUPS);
    this.groupCache = groupCache;
    this.mapping = new GroupMapping(schema, client.adapter());
    this.schema = schema;
  }

  @Override
  public void replace(InternalGroup group) {
    BulkRequest bulk =
        new IndexRequest(getId(group), indexName, type, client.adapter())
            .add(new UpdateRequest<>(schema, group));

    String uri = getURI(type, BULK);
    Response response = postRequest(uri, bulk, getRefreshParam());
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      throw new StorageException(
          String.format(
              "Failed to replace group %s in index %s: %s",
              group.getGroupUUID().get(), indexName, statusCode));
    }
  }

  @Override
  public DataSource<InternalGroup> getSource(Predicate<InternalGroup> p, QueryOptions opts)
      throws QueryParseException {
    JsonArray sortArray = getSortArray(GroupField.UUID.getName());
    return new ElasticQuerySource(p, opts.filterFields(IndexUtils::groupFields), type, sortArray);
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
  protected String getId(InternalGroup group) {
    return group.getGroupUUID().get();
  }

  @Override
  protected InternalGroup fromDocument(JsonObject json, Set<String> fields) {
    JsonElement source = json.get("_source");
    if (source == null) {
      source = json.getAsJsonObject().get("fields");
    }

    AccountGroup.UUID uuid =
        new AccountGroup.UUID(
            source.getAsJsonObject().get(GroupField.UUID.getName()).getAsString());
    // Use the GroupCache rather than depending on any stored fields in the
    // document (of which there shouldn't be any).
    return groupCache.get().get(uuid).orElse(null);
  }
}
