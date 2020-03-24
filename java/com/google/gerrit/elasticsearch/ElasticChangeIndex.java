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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.elasticsearch.bulk.BulkRequest;
import com.google.gerrit.elasticsearch.bulk.DeleteRequest;
import com.google.gerrit.elasticsearch.bulk.IndexRequest;
import com.google.gerrit.elasticsearch.bulk.UpdateRequest;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.converter.ChangeProtoConverter;
import com.google.gerrit.entities.converter.PatchSetApprovalProtoConverter;
import com.google.gerrit.entities.converter.PatchSetProtoConverter;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.change.MergeabilityComputationBehavior;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.index.change.ChangeIndex;
import com.google.gerrit.server.index.change.ChangeIndexRewriter;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
  private static final String OPEN_CHANGES = "open_" + CHANGES;
  private static final String CLOSED_CHANGES = "closed_" + CHANGES;

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
    String deleteIndex;
    String insertIndex;

    if (cd.change().isNew()) {
      insertIndex = OPEN_CHANGES;
      deleteIndex = CLOSED_CHANGES;
    } else {
      insertIndex = CLOSED_CHANGES;
      deleteIndex = OPEN_CHANGES;
    }

    ElasticQueryAdapter adapter = client.adapter();
    BulkRequest bulk =
        new IndexRequest(getId(cd), indexName, adapter.getType(insertIndex), adapter)
            .add(new UpdateRequest<>(schema, cd, skipFields));
    if (adapter.deleteToReplace()) {
      bulk.add(new DeleteRequest(cd.getId().toString(), indexName, deleteIndex, adapter));
    }

    String uri = getURI(type, BULK);
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
    Set<Change.Status> statuses = ChangeIndexRewriter.getPossibleStatus(p);
    List<String> indexes = Lists.newArrayListWithCapacity(2);
    if (!client.adapter().omitType()) {
      if (client.adapter().useV6Type()) {
        if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()
            || !Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
          indexes.add(ElasticQueryAdapter.V6_TYPE);
        }
      } else {
        if (!Sets.intersection(statuses, OPEN_STATUSES).isEmpty()) {
          indexes.add(OPEN_CHANGES);
        }
        if (!Sets.intersection(statuses, CLOSED_STATUSES).isEmpty()) {
          indexes.add(CLOSED_CHANGES);
        }
      }
    }

    QueryOptions filteredOpts =
        opts.filterFields(o -> IndexUtils.changeFields(o, schema.useLegacyNumericFields()));
    return new ElasticQuerySource(p, filteredOpts, getURI(indexes), getSortArray());
  }

  private JsonArray getSortArray() {
    JsonObject properties = new JsonObject();
    properties.addProperty(ORDER, "desc");

    JsonArray sortArray = new JsonArray();
    addNamedElement(ChangeField.UPDATED.getName(), properties, sortArray);
    addNamedElement(idField.getName(), properties, sortArray);
    return sortArray;
  }

  private String getURI(List<String> types) {
    return String.join(",", types);
  }

  @Override
  protected String getDeleteActions(Change.Id c) {
    if (!client.adapter().useV5Type()) {
      return delete(client.adapter().getType(), c);
    }
    return delete(OPEN_CHANGES, c) + delete(CLOSED_CHANGES, c);
  }

  @Override
  protected String getMappings() {
    if (!client.adapter().useV5Type()) {
      return getMappingsFor(client.adapter().getType(), mapping.changes);
    }
    return gson.toJson(ImmutableMap.of(MAPPINGS, mapping));
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

    // Any decoding that is done here must also be done in {@link LuceneChangeIndex}.

    // Patch sets.
    cd.setPatchSets(
        decodeProtos(source, ChangeField.PATCH_SET.getName(), PatchSetProtoConverter.INSTANCE));

    // Approvals.
    if (source.get(ChangeField.APPROVAL.getName()) != null) {
      cd.setCurrentApprovals(
          decodeProtos(
              source, ChangeField.APPROVAL.getName(), PatchSetApprovalProtoConverter.INSTANCE));
    } else if (fields.contains(ChangeField.APPROVAL.getName())) {
      cd.setCurrentApprovals(Collections.emptyList());
    }

    // Added & Deleted.
    JsonElement addedElement = source.get(ChangeField.ADDED.getName());
    JsonElement deletedElement = source.get(ChangeField.DELETED.getName());
    if (addedElement != null && deletedElement != null) {
      // Changed lines.
      int added = addedElement.getAsInt();
      int deleted = deletedElement.getAsInt();
      cd.setChangedLines(added, deleted);
    }

    // Star.
    JsonElement starredElement = source.get(ChangeField.STAR.getName());
    if (starredElement != null) {
      ListMultimap<Account.Id, String> stars = MultimapBuilder.hashKeys().arrayListValues().build();
      JsonArray starBy = starredElement.getAsJsonArray();
      if (starBy.size() > 0) {
        for (int i = 0; i < starBy.size(); i++) {
          String[] indexableFields = starBy.get(i).getAsString().split(":");
          Optional<Account.Id> id = Account.Id.tryParse(indexableFields[0]);
          if (id.isPresent()) {
            stars.put(id.get(), indexableFields[1]);
          }
        }
      }
      cd.setStars(stars);
    }

    // Mergeable.
    JsonElement mergeableElement = source.get(ChangeField.MERGEABLE.getName());
    if (mergeableElement != null && !skipFields.contains(ChangeField.MERGEABLE.getName())) {
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
          accounts.add(Account.id(aId));
        }
        cd.setReviewedBy(accounts);
      }
    } else if (fields.contains(ChangeField.REVIEWEDBY.getName())) {
      cd.setReviewedBy(Collections.emptySet());
    }

    // Hashtag.
    if (source.get(ChangeField.HASHTAG.getName()) != null) {
      JsonArray hashtagArray = source.get(ChangeField.HASHTAG.getName()).getAsJsonArray();
      if (hashtagArray.size() > 0) {
        Set<String> hashtags = Sets.newHashSetWithExpectedSize(hashtagArray.size());
        for (int i = 0; i < hashtagArray.size(); i++) {
          hashtags.add(hashtagArray.get(i).getAsString());
        }
        cd.setHashtags(hashtags);
      }
    } else if (fields.contains(ChangeField.HASHTAG.getName())) {
      cd.setHashtags(Collections.emptySet());
    }

    // Star.
    if (source.get(ChangeField.STAR.getName()) != null) {
      JsonArray starArray = source.get(ChangeField.STAR.getName()).getAsJsonArray();
      if (starArray.size() > 0) {
        ListMultimap<Account.Id, String> stars =
            MultimapBuilder.hashKeys().arrayListValues().build();
        for (int i = 0; i < starArray.size(); i++) {
          StarredChangesUtil.StarField starField =
              StarredChangesUtil.StarField.parse(starArray.get(i).getAsString());
          stars.put(starField.accountId(), starField.label());
        }
        cd.setStars(stars);
      }
    } else if (fields.contains(ChangeField.STAR.getName())) {
      cd.setStars(ImmutableListMultimap.of());
    }

    // Reviewer.
    if (source.get(ChangeField.REVIEWER.getName()) != null) {
      cd.setReviewers(
          ChangeField.parseReviewerFieldValues(
              cd.getId(),
              FluentIterable.from(source.get(ChangeField.REVIEWER.getName()).getAsJsonArray())
                  .transform(JsonElement::getAsString)));
    } else if (fields.contains(ChangeField.REVIEWER.getName())) {
      cd.setReviewers(ReviewerSet.empty());
    }

    // Reviewer-by-email.
    if (source.get(ChangeField.REVIEWER_BY_EMAIL.getName()) != null) {
      cd.setReviewersByEmail(
          ChangeField.parseReviewerByEmailFieldValues(
              cd.getId(),
              FluentIterable.from(
                      source.get(ChangeField.REVIEWER_BY_EMAIL.getName()).getAsJsonArray())
                  .transform(JsonElement::getAsString)));
    } else if (fields.contains(ChangeField.REVIEWER_BY_EMAIL.getName())) {
      cd.setReviewersByEmail(ReviewerByEmailSet.empty());
    }

    // Pending-reviewer.
    if (source.get(ChangeField.PENDING_REVIEWER.getName()) != null) {
      cd.setPendingReviewers(
          ChangeField.parseReviewerFieldValues(
              cd.getId(),
              FluentIterable.from(
                      source.get(ChangeField.PENDING_REVIEWER.getName()).getAsJsonArray())
                  .transform(JsonElement::getAsString)));
    } else if (fields.contains(ChangeField.PENDING_REVIEWER.getName())) {
      cd.setPendingReviewers(ReviewerSet.empty());
    }

    // Pending-reviewer-by-email.
    if (source.get(ChangeField.PENDING_REVIEWER_BY_EMAIL.getName()) != null) {
      cd.setPendingReviewersByEmail(
          ChangeField.parseReviewerByEmailFieldValues(
              cd.getId(),
              FluentIterable.from(
                      source.get(ChangeField.PENDING_REVIEWER_BY_EMAIL.getName()).getAsJsonArray())
                  .transform(JsonElement::getAsString)));
    } else if (fields.contains(ChangeField.PENDING_REVIEWER_BY_EMAIL.getName())) {
      cd.setPendingReviewersByEmail(ReviewerByEmailSet.empty());
    }

    // Stored-submit-record-strict.
    decodeSubmitRecords(
        source,
        ChangeField.STORED_SUBMIT_RECORD_STRICT.getName(),
        ChangeField.SUBMIT_RULE_OPTIONS_STRICT,
        cd);

    // Stored-submit-record-leniant.
    decodeSubmitRecords(
        source,
        ChangeField.STORED_SUBMIT_RECORD_LENIENT.getName(),
        ChangeField.SUBMIT_RULE_OPTIONS_LENIENT,
        cd);

    // Ref-state.
    if (fields.contains(ChangeField.REF_STATE.getName())) {
      cd.setRefStates(getByteArray(source, ChangeField.REF_STATE.getName()));
    }

    // Ref-state-pattern.
    if (fields.contains(ChangeField.REF_STATE_PATTERN.getName())) {
      cd.setRefStatePatterns(getByteArray(source, ChangeField.REF_STATE_PATTERN.getName()));
    }

    // Unresolved-comment-count.
    decodeUnresolvedCommentCount(source, ChangeField.UNRESOLVED_COMMENT_COUNT.getName(), cd);

    // Attention set.
    if (fields.contains(ChangeField.ATTENTION_SET_FULL.getName())) {
      ChangeField.parseAttentionSet(
          FluentIterable.from(source.getAsJsonArray(ChangeField.ATTENTION_SET_FULL.getName()))
              .transform(JsonElement::getAsString),
          cd);
    }

    return cd;
  }

  private Iterable<byte[]> getByteArray(JsonObject source, String name) {
    JsonElement element = source.get(name);
    return element != null
        ? Iterables.transform(element.getAsJsonArray(), e -> decodeBase64(e.getAsString()))
        : Collections.emptyList();
  }

  private void decodeSubmitRecords(
      JsonObject doc, String fieldName, SubmitRuleOptions opts, ChangeData out) {
    JsonArray records = doc.getAsJsonArray(fieldName);
    if (records == null) {
      return;
    }
    ChangeField.parseSubmitRecords(
        FluentIterable.from(records)
            .transform(i -> new String(decodeBase64(i.getAsString()), UTF_8))
            .toList(),
        opts,
        out);
  }

  private void decodeUnresolvedCommentCount(JsonObject doc, String fieldName, ChangeData out) {
    JsonElement count = doc.get(fieldName);
    if (count == null) {
      return;
    }
    out.setUnresolvedCommentCount(count.getAsInt());
  }
}
