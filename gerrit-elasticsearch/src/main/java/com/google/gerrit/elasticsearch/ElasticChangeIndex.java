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

import static com.google.gerrit.server.index.change.ChangeIndexRewriter.CLOSED_STATUSES;
import static com.google.gerrit.server.index.change.ChangeIndexRewriter.OPEN_STATUSES;
import static com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.binary.Base64.decodeBase64;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.index.IndexUtils;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.FieldType;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeField.ChangeProtoField;
import com.google.gerrit.server.index.change.ChangeField.PatchSetApprovalProtoField;
import com.google.gerrit.server.index.change.ChangeField.PatchSetProtoField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gwtorm.protobuf.ProtobufCodec;
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
import org.apache.commons.codec.binary.Base64;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Secondary index implementation using Elasticsearch. */
class ElasticChangeIndex extends AbstractElasticIndex<Change.Id, ChangeData>
    implements ChangeIndex {
  private static final Logger log = LoggerFactory.getLogger(ElasticChangeIndex.class);

  static class ChangeMapping {
    MappingProperties openChanges;
    MappingProperties closedChanges;

    ChangeMapping(Schema<ChangeData> schema) {
      ElasticMapping.Builder mappingBuilder = new ElasticMapping.Builder();
      for (FieldDef<?, ?> field : schema.getFields().values()) {
        String name = field.getName();
        FieldType<?> fieldType = field.getType();
        if (fieldType == FieldType.EXACT) {
          mappingBuilder.addExactField(name);
        } else if (fieldType == FieldType.TIMESTAMP) {
          mappingBuilder.addTimestamp(name);
        } else if (fieldType == FieldType.INTEGER
            || fieldType == FieldType.INTEGER_RANGE
            || fieldType == FieldType.LONG) {
          mappingBuilder.addNumber(name);
        } else if (fieldType == FieldType.PREFIX
            || fieldType == FieldType.FULL_TEXT
            || fieldType == FieldType.STORED_ONLY) {
          mappingBuilder.addString(name);
        } else {
          throw new IllegalArgumentException("Unsupported filed type " + fieldType.getName());
        }
      }
      MappingProperties mapping = mappingBuilder.build();
      openChanges = mapping;
      closedChanges = mapping;
    }
  }

  static final String OPEN_CHANGES = "open_changes";
  static final String CLOSED_CHANGES = "closed_changes";

  private final Gson gson;
  private final ChangeMapping mapping;
  private final Provider<ReviewDb> db;
  private final ElasticQueryBuilder queryBuilder;
  private final ChangeData.Factory changeDataFactory;

  @AssistedInject
  ElasticChangeIndex(
      @GerritServerConfig Config cfg,
      Provider<ReviewDb> db,
      ChangeData.Factory changeDataFactory,
      FillArgs fillArgs,
      SitePaths sitePaths,
      @Assisted Schema<ChangeData> schema) {
    super(cfg, fillArgs, sitePaths, schema);
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    mapping = new ChangeMapping(schema);

    this.queryBuilder = new ElasticQueryBuilder();
    this.gson = new GsonBuilder().setFieldNamingPolicy(LOWER_CASE_WITH_UNDERSCORES).create();
  }

  private static <T> List<T> decodeProtos(
      JsonObject doc, String fieldName, ProtobufCodec<T> codec) {
    return FluentIterable.from(doc.getAsJsonArray(fieldName))
        .transform(i -> codec.decode(decodeBase64(i.toString())))
        .toList();
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

    Bulk bulk =
        new Bulk.Builder()
            .defaultIndex(indexName)
            .defaultType("changes")
            .addAction(insert(insertIndex, cd))
            .addAction(delete(deleteIndex, cd.getId()))
            .refresh(refresh)
            .build();
    JestResult result = client.execute(bulk);
    if (!result.isSucceeded()) {
      throw new IOException(
          String.format(
              "Failed to replace change %s in index %s: %s",
              cd.getId(), indexName, result.getErrorMessage()));
    }
  }

  @Override
  public ChangeDataSource getSource(Predicate<ChangeData> p, QueryOptions opts)
      throws QueryParseException {
    Set<Change.Status> statuses = ChangeIndexRewriter.getPossibleStatus(p);
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
  protected Builder addActions(Builder builder, Id c) {
    return builder.addAction(delete(OPEN_CHANGES, c)).addAction(delete(OPEN_CHANGES, c));
  }

  @Override
  protected String getMappings() {
    return gson.toJson(ImmutableMap.of("mappings", mapping));
  }

  @Override
  protected String getId(ChangeData cd) {
    return cd.getId().toString();
  }

  private class QuerySource implements ChangeDataSource {
    private final Search search;
    private final Set<String> fields;

    QuerySource(List<String> types, Predicate<ChangeData> p, QueryOptions opts)
        throws QueryParseException {
      List<Sort> sorts =
          ImmutableList.of(
              new Sort(ChangeField.UPDATED.getName(), Sorting.DESC),
              new Sort(ChangeField.LEGACY_ID.getName(), Sorting.DESC));
      for (Sort sort : sorts) {
        sort.setIgnoreUnmapped();
      }
      QueryBuilder qb = queryBuilder.toQueryBuilder(p);
      fields = IndexUtils.fields(opts);
      SearchSourceBuilder searchSource =
          new SearchSourceBuilder()
              .query(qb)
              .from(opts.start())
              .size(opts.limit())
              .fields(Lists.newArrayList(fields));

      search =
          new Search.Builder(searchSource.toString())
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
          log.error(result.getErrorMessage());
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

    private ChangeData toChangeData(JsonElement json) {
      JsonElement sourceElement = json.getAsJsonObject().get("_source");
      if (sourceElement == null) {
        sourceElement = json.getAsJsonObject().get("fields");
      }
      JsonObject source = sourceElement.getAsJsonObject();
      JsonElement c = source.get(ChangeField.CHANGE.getName());

      if (c == null) {
        int id = source.get(ChangeField.LEGACY_ID.getName()).getAsInt();
        String projectName = source.get(ChangeField.PROJECT.getName()).getAsString();
        if (projectName == null) {
          return changeDataFactory.createOnlyWhenNoteDbDisabled(db.get(), new Change.Id(id));
        }
        return changeDataFactory.create(
            db.get(), new Project.NameKey(projectName), new Change.Id(id));
      }

      ChangeData cd =
          changeDataFactory.create(
              db.get(), ChangeProtoField.CODEC.decode(Base64.decodeBase64(c.getAsString())));

      // Patch sets.
      cd.setPatchSets(
          decodeProtos(source, ChangeField.PATCH_SET.getName(), PatchSetProtoField.CODEC));

      // Approvals.
      if (source.get(ChangeField.APPROVAL.getName()) != null) {
        cd.setCurrentApprovals(
            decodeProtos(source, ChangeField.APPROVAL.getName(), PatchSetApprovalProtoField.CODEC));
      } else if (fields.contains(ChangeField.APPROVAL.getName())) {
        cd.setCurrentApprovals(Collections.emptyList());
      }

      JsonElement addedElement = source.get(ChangeField.ADDED.getName());
      JsonElement deletedElement = source.get(ChangeField.DELETED.getName());
      if (addedElement != null && deletedElement != null) {
        // Changed lines.
        int added = addedElement.getAsInt();
        int deleted = deletedElement.getAsInt();
        if (added != 0 && deleted != 0) {
          cd.setChangedLines(added, deleted);
        }
      }

      // Mergeable.
      JsonElement mergeableElement = source.get(ChangeField.MERGEABLE.getName());
      if (mergeableElement != null) {
        String mergeable = mergeableElement.getAsString();
        if ("1".equals(mergeable)) {
          cd.setMergeable(true);
        } else if ("0".equals(mergeable)) {
          cd.setMergeable(false);
        }
      }

      // Reviewed-by.
      if (source.get(ChangeField.REVIEWEDBY.getName()) != null) {
        JsonArray reviewedBy = source.get(ChangeField.REVIEWEDBY.getName()).getAsJsonArray();
        if (reviewedBy.size() > 0) {
          Set<Account.Id> accounts = Sets.newHashSetWithExpectedSize(reviewedBy.size());
          for (int i = 0; i < reviewedBy.size(); i++) {
            int aId = reviewedBy.get(i).getAsInt();
            if (reviewedBy.size() == 1 && aId == ChangeField.NOT_REVIEWED) {
              break;
            }
            accounts.add(new Account.Id(aId));
          }
          cd.setReviewedBy(accounts);
        }
      } else if (fields.contains(ChangeField.REVIEWEDBY.getName())) {
        cd.setReviewedBy(Collections.emptySet());
      }

      if (source.get(ChangeField.REVIEWER.getName()) != null) {
        cd.setReviewers(
            ChangeField.parseReviewerFieldValues(
                FluentIterable.from(source.get(ChangeField.REVIEWER.getName()).getAsJsonArray())
                    .transform(JsonElement::getAsString)));
      } else if (fields.contains(ChangeField.REVIEWER.getName())) {
        cd.setReviewers(ReviewerSet.empty());
      }

      decodeSubmitRecords(
          source,
          ChangeField.STORED_SUBMIT_RECORD_STRICT.getName(),
          ChangeField.SUBMIT_RULE_OPTIONS_STRICT,
          cd);
      decodeSubmitRecords(
          source,
          ChangeField.STORED_SUBMIT_RECORD_LENIENT.getName(),
          ChangeField.SUBMIT_RULE_OPTIONS_LENIENT,
          cd);

      return cd;
    }

    private void decodeSubmitRecords(
        JsonObject doc, String fieldName, SubmitRuleOptions opts, ChangeData out) {
      JsonArray records = doc.getAsJsonArray(fieldName);
      if (records == null) {
        return;
      }
      ChangeField.parseSubmitRecords(
          FluentIterable.from(records)
              .transform(i -> new String(decodeBase64(i.toString()), UTF_8))
              .toList(),
          opts,
          out);
    }
  }
}
