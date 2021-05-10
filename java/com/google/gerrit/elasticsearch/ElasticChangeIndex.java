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

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.elasticsearch.bulk.BulkRequest;
import com.google.gerrit.elasticsearch.bulk.IndexRequest;
import com.google.gerrit.elasticsearch.bulk.UpdateRequest;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.converter.ChangeProtoConverter;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Set;
import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.client.Response;

/** Secondary index implementation using Elasticsearch. */
class ElasticChangeIndex extends AbstractElasticIndex<Change.Id, ChangeData>
    implements ChangeIndex {
  static class ChangeMapping {
    final MappingProperties changes;
    final MappingProperties openChanges;
    final MappingProperties closedChanges;

    ChangeMapping(Schema<ChangeData> schema, ElasticQueryAdapter adapter) {
      MappingProperties mapping = ElasticMapping.createMapping(schema, adapter);
      this.changes = mapping;
      this.openChanges = mapping;
      this.closedChanges = mapping;
    }
  }

  private static final String CHANGES = "changes";

  private final ChangeMapping mapping;
  private final ChangeData.Factory changeDataFactory;
  private final Schema<ChangeData> schema;
  private final FieldDef<ChangeData, ?> idField;
  private final ImmutableSet<String> skipFields;

  @Inject
  ElasticChangeIndex(
      ElasticConfiguration cfg,
      ChangeData.Factory changeDataFactory,
      SitePaths sitePaths,
      ElasticRestClientProvider clientBuilder,
      @GerritServerConfig Config gerritConfig,
      @Assisted Schema<ChangeData> schema) {
    super(cfg, sitePaths, schema, clientBuilder, CHANGES);
    this.changeDataFactory = changeDataFactory;
    this.schema = schema;
    this.mapping = new ChangeMapping(schema, client.adapter());
    this.idField =
        this.schema.useLegacyNumericFields() ? ChangeField.LEGACY_ID : ChangeField.LEGACY_ID_STR;
    this.skipFields =
        MergeabilityComputationBehavior.fromConfig(gerritConfig).includeInIndex()
            ? ImmutableSet.of()
            : ImmutableSet.of(ChangeField.MERGEABLE.getName());
  }

  @Override
  public void replace(ChangeData cd) {
    BulkRequest bulk =
        new IndexRequest(getId(cd), indexName).add(new UpdateRequest<>(schema, cd, skipFields));

    String uri = getURI(BULK);
    Response response = postRequest(uri, bulk, getRefreshParam());
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      throw new StorageException(
          String.format(
              "Failed to replace change %s in index %s: %s", cd.getId(), indexName, statusCode));
    }
  }

  @Override
  public DataSource<ChangeData> getSource(Predicate<ChangeData> p, QueryOptions opts)
      throws QueryParseException {
    QueryOptions filteredOpts =
        opts.filterFields(o -> IndexUtils.changeFields(o, schema.useLegacyNumericFields()));
    return new ElasticQuerySource(p, filteredOpts, getSortArray());
  }

  private JsonArray getSortArray() {
    JsonObject properties = new JsonObject();
    properties.addProperty(ORDER, DESC_SORT_ORDER);

    JsonArray sortArray = new JsonArray();
    addNamedElement(ChangeField.UPDATED.getName(), properties, sortArray);
    addNamedElement(ChangeField.MERGED_ON.getName(), getMergedOnSortOptions(), sortArray);
    addNamedElement(idField.getName(), properties, sortArray);
    return sortArray;
  }

  private JsonObject getMergedOnSortOptions() {
    JsonObject sortOptions = new JsonObject();
    sortOptions.addProperty(ORDER, DESC_SORT_ORDER);
    // Ignore the sort field if it does not exist in index. Otherwise the search would fail on open
    // changes, because the corresponding documents do not have mergedOn field.
    sortOptions.addProperty(UNMAPPED_TYPE, ElasticMapping.TIMESTAMP_FIELD_TYPE);
    return sortOptions;
  }

  @Override
  protected String getDeleteActions(Change.Id c) {
    return getDeleteRequest(c);
  }

  @Override
  protected String getMappings() {
    return getMappingsFor(mapping.changes);
  }

  @Override
  protected String getId(ChangeData cd) {
    return cd.getId().toString();
  }

  @Override
  protected ChangeData fromDocument(JsonObject json, Set<String> fields) {
    JsonElement sourceElement = json.get("_source");
    if (sourceElement == null) {
      sourceElement = json.getAsJsonObject().get("fields");
    }
    JsonObject source = sourceElement.getAsJsonObject();
    JsonElement c = source.get(ChangeField.CHANGE.getName());

    if (c == null) {
      int id = source.get(idField.getName()).getAsInt();
      // IndexUtils#changeFields ensures either CHANGE or PROJECT is always present.
      String projectName = requireNonNull(source.get(ChangeField.PROJECT.getName()).getAsString());
      return changeDataFactory.create(Project.nameKey(projectName), Change.id(id));
    }

    ChangeData cd =
        changeDataFactory.create(
            parseProtoFrom(decodeBase64(c.getAsString()), ChangeProtoConverter.INSTANCE));

    for (FieldDef<ChangeData, ?> field : getSchema().getFields().values()) {
      if (fields.contains(field.getName()) && source.get(field.getName()) != null) {
        field.setIfPossible(cd, new ElasticStoredValue(source.get(field.getName())));
      }
    }

    return cd;
  }
}
