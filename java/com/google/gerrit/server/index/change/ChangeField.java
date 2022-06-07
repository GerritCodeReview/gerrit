// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.index.change;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.index.Field.INTEGER_TYPE;
import static com.google.gerrit.index.Field.exact;
import static com.google.gerrit.index.Field.fullText;
import static com.google.gerrit.index.Field.intRange;
import static com.google.gerrit.index.Field.integer;
import static com.google.gerrit.index.Field.prefix;
import static com.google.gerrit.index.Field.storedOnly;
import static com.google.gerrit.index.Field.timestamp;
import static com.google.gerrit.server.change.ChangeJson.SUBMIT_RULE_OPTIONS_STRICT;
import static com.google.gerrit.server.util.AttentionSetUtil.additionsOnly;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.common.primitives.Longs;
import com.google.common.reflect.TypeToken;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LegacySubmitRequirement;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.entities.converter.ChangeProtoConverter;
import com.google.gerrit.entities.converter.PatchSetApprovalProtoConverter;
import com.google.gerrit.entities.converter.PatchSetProtoConverter;
import com.google.gerrit.entities.converter.ProtoConverter;
import com.google.gerrit.index.Field;
import com.google.gerrit.index.Field.FieldSpec;
import com.google.gerrit.index.Field;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.SchemaUtil;
import com.google.gerrit.index.SearchOptions;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.index.change.StalenessChecker.RefStatePattern;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.notedb.SubmitRequirementProtoConverter;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gerrit.server.query.change.MagicLabelValue;
import com.google.gson.Gson;
import com.google.protobuf.MessageLite;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Fields indexed on change documents.
 *
 * <p>Each field corresponds to both a field name supported by {@link ChangeQueryBuilder} for
 * querying that field, and a method on {@link ChangeData} used for populating the corresponding
 * document fields in the secondary index.
 *
 * <p>Field names are all lowercase alphanumeric plus underscore; index implementations may create
 * unambiguous derived field names containing other characters.
 */
public class ChangeField {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final int NO_ASSIGNEE = -1;

  private static final Gson GSON = OutputFormat.JSON_COMPACT.newGson();

  // TODO: Rename LEGACY_ID to NUMERIC_ID
  /** Legacy change ID. */

  public static final Field<ChangeData, String> LEGACY_ID_STR_FIELD = Field.<ChangeData, String>builder("IdStr", Field.STRING_TYPE).stored().build(cd -> String.valueOf(cd.getId().get()));

  public static final FieldSpec LEGACY_ID_STR_SPEC = LEGACY_ID_STR_FIELD.exact("legacy_id_str");

  /** Newer style Change-Id key. */
  public static final Field<ChangeData, String> CHANGE_ID_FIELD = Field.<ChangeData, String>builder("ChangeId", Field.STRING_TYPE).build(changeGetter(c -> c.getKey().get()));

  public static final FieldSpec CHANGE_ID_SPEC = CHANGE_ID_FIELD.prefix(ChangeQueryBuilder.FIELD_CHANGE_ID);

  /** Change status string, in the same format as {@code status:}. */

  public static final Field<ChangeData, String> STATUS_FIELD = Field.<ChangeData, String>builder("Status", Field.STRING_TYPE).build(changeGetter(c -> ChangeStatusPredicate.canonicalize(c.getStatus())));

  public static final FieldSpec STATUS_SPEC = STATUS_FIELD.exact(ChangeQueryBuilder.FIELD_STATUS);

  /** Project containing the change. */
  public static final Field<ChangeData, String> PROJECT_FIELD = Field.<ChangeData, String>builder("Project", Field.STRING_TYPE)
          .stored()
          .build(changeGetter(c -> c.getProject().get()));
  public static final FieldSpec PROJECT_EXACT_SPEC =       PROJECT_FIELD.exact(ChangeQueryBuilder.FIELD_PROJECT);
  public static final FieldSpec PROJECT_PREFIX_SPEC =      PROJECT_FIELD.prefix(ChangeQueryBuilder.FIELD_PROJECTS);

  /** Reference (aka branch) the change will submit onto. */
  public static final Field<ChangeData, String > REF_FIELD =  Field.<ChangeData, String>builder("Ref", Field.STRING_TYPE).build(changeGetter(c -> c.getDest().branch()));

  public static final FieldSpec REF_SPEC = REF_FIELD.exact(ChangeQueryBuilder.FIELD_REF);

  /** Topic, a short annotation on the branch. */
  public static final Field<ChangeData, String > TOPIC_FIELD =  Field.<ChangeData, String>builder("Topic", Field.STRING_TYPE).build(ChangeField::getTopic);
  public static final FieldSpec EXACT_TOPIC_SPEC =
      TOPIC_FIELD.exact("topic4");
  
  public static final FieldSpec FUZZY_TOPIC_SPEC =
      TOPIC_FIELD.fullText("topic5");
  public static final FieldSpec PREFIX_TOPIC_SPEC =
      TOPIC_FIELD.prefix("topic6");

  /** Submission id assigned by MergeOp. */
  public static final Field<ChangeData, String> SUBMISSIONID_FIELD = Field.<ChangeData, String>builder("SubmissionId", Field.STRING_TYPE).build(changeGetter(Change::getSubmissionId));

  public static final FieldSpec SUBMISSIONID_SPEC = SUBMISSIONID_FIELD.exact(ChangeQueryBuilder.FIELD_SUBMISSIONID);

  /** Last update time since January 1, 1970. */
  // TODO(issue-15518): Migrate type for timestamp index fields from Timestamp to Instant
  public static final Field<ChangeData, Timestamp> UPDATED_FIELD = Field.<ChangeData, Timestamp>builder("LastUpdated", Field.TIMESTAMP_TYPE).stored().build(changeGetter(change -> Timestamp.from(change.getLastUpdatedOn())));

  public static final FieldSpec UPDATED_SPEC = UPDATED_FIELD.timestamp("updated2");

  /** When this change was merged, time since January 1, 1970. */
  // TODO(issue-15518): Migrate type for timestamp index fields from Timestamp to Instant
  public static final Field<ChangeData, Timestamp> MERGED_ON_FIELD = Field.<ChangeData, Timestamp>builder("MergedOn", Field.TIMESTAMP_TYPE).stored().build(
      cd -> cd.getMergedOn().map(Timestamp::from).orElse(null),
              (cd, field) -> cd.setMergedOn(field != null ? field.toInstant() : null));
  public static final FieldSpec MERGED_ON_SPEC = MERGED_ON_FIELD .timestamp(ChangeQueryBuilder.FIELD_MERGED_ON);

  /** List of full file paths modified in the current patch set. */
  public static final Field<ChangeData, Iterable<String>> PATH_FIELD = Field.<ChangeData, Iterable<String>>builder("MergedOn", Field.ITERABLE_STRING_TYPE).build(cd -> firstNonNull(cd.currentFilePaths(), ImmutableList.of()));

  // Named for backwards compatibility.
  public static final FieldSpec PATH_FIELD_SPEC = PATH_FIELD.timestamp(ChangeQueryBuilder.FIELD_FILE);

  public static Set<String> getFileParts(ChangeData cd) {
    List<String> paths = cd.currentFilePaths();

    Splitter s = Splitter.on('/').omitEmptyStrings();
    Set<String> r = new HashSet<>();
    for (String path : paths) {
      for (String part : s.split(path)) {
        r.add(part);
      }
    }
    return r;
  }

  /** Hashtags tied to a change */
  public static final Field<ChangeData, Iterable<String>> HASHTAG_FIELD = Field.<ChangeData, Iterable<String>>builder("Hashtag", Field.ITERABLE_STRING_TYPE)
          .build(cd -> cd.hashtags().stream().map(String::toLowerCase).collect(toSet()));
  public static final FieldSpec EXACT_HASHTAG_SPEC = HASHTAG_FIELD.exact(ChangeQueryBuilder.FIELD_HASHTAG);

  /** Hashtags as fulltext field for in-string search. */
  public static final FieldSpec FUZZY_HASHTAG_SPEC = HASHTAG_FIELD.fullText("hashtag2");
  /** Hashtags as prefix field for in-string search. */
  public static final FieldSpec PREFIX_HASHTAG_SPEC = HASHTAG_FIELD.prefix("hashtag3");


  /** Hashtags with original case. */

  // This should be stored as String
  public static final Field<ChangeData, Iterable<byte[]>> HASHTAG_CASE_AWARE_FIELD = Field.<ChangeData,  Iterable<byte[]>>builder("HashtagCaseAware", Field.ITERABLE_BYTE_ARRAY_TYPE)
      .build(              cd -> cd.hashtags().stream().map(t -> t.getBytes(UTF_8)).collect(toSet()),
          (cd, field) ->
              cd.setHashtags(
                  StreamSupport.stream(field.spliterator(), false)
                      .map(f -> new String(f, UTF_8))
                      .collect(toImmutableSet())));
  public static final FieldSpec HASHTAG_CASE_AWARE_SPEC = HASHTAG_FIELD.storedOnly("_hashtag");


  public static Set<String> getExtensions(ChangeData cd) {
    return extensions(cd).collect(toSet());
  }

  /**
   * File extensions of each file modified in the current patch set as a sorted list. The purpose of
   * this field is to allow matching changes that only touch files with certain file extensions.
   */

  public static String getAllExtensionsAsList(ChangeData cd) {
    return extensions(cd).distinct().sorted().collect(joining(","));
  }

  /**
   * Returns a stream with all file extensions that are used by files in the given change. A file
   * extension is defined as the portion of the filename following the final `.`. Files with no `.`
   * in their name have no extension. For them an empty string is returned as part of the stream.
   *
   * <p>If the change contains multiple files with the same extension the extension is returned
   * multiple times in the stream (once per file).
   */
  private static Stream<String> extensions(ChangeData cd) {
    return cd.currentFilePaths().stream()
        // Use case-insensitive file extensions even though other file fields are case-sensitive.
        // If we want to find "all Java files", we want to match both .java and .JAVA, even if we
        // normally care about case sensitivity. (Whether we should change the existing file/path
        // predicates to be case insensitive is a separate question.)
        .map(f -> Files.getFileExtension(f).toLowerCase(Locale.US));
  }

  /** Footers from the commit message of the current patch set. */

  public static Set<String> getFooters(ChangeData cd) {
    return cd.commitFooters().stream()
        .map(f -> f.toString().toLowerCase(Locale.US))
        .collect(toSet());
  }


  public static Set<String> getDirectories(ChangeData cd) {
    List<String> paths = cd.currentFilePaths();

    Splitter s = Splitter.on('/').omitEmptyStrings();
    Set<String> r = new HashSet<>();
    for (String path : paths) {
      StringBuilder directory = new StringBuilder();
      r.add(directory.toString());
      String nextPart = null;
      for (String part : s.split(path.toLowerCase(Locale.US))) {
        if (nextPart != null) {
          r.add(nextPart);

          if (directory.length() > 0) {
            directory.append("/");
          }
          directory.append(nextPart);

          String intermediateDir = directory.toString();
          int i = intermediateDir.indexOf('/');
          while (i >= 0) {
            r.add(intermediateDir);
            intermediateDir = intermediateDir.substring(i + 1);
            i = intermediateDir.indexOf('/');
          }
        }
        nextPart = part;
      }
    }
    return r;
  }

  /** Owner/creator of the change. */
  public static final Field<ChangeData, Integer> OWNER_FILED = Field.<ChangeData, Integer>builder("Owner", INTEGER_TYPE).build(changeGetter(c -> c.getOwner().get()));
  public static final FieldSpec OWNER_SPEC =
      OWNER_FILED.integer(ChangeQueryBuilder.FIELD_OWNER);

  /** Uploader of the latest patch set. */
  public static final Field<ChangeData, Integer> UPLOADER_FIELD = Field.<ChangeData, Integer>builder("Owner", INTEGER_TYPE).build(cd -> cd.currentPatchSet().uploader().get());
  public static final FieldSpec UPLOADER_SPEC =
      UPLOADER_FIELD.integer(ChangeQueryBuilder.FIELD_UPLOADER);;


  /** This class decouples the internal and API types from storage. */
  private static class StoredAttentionSetEntry {
    final long timestampMillis;
    final int userId;
    final String reason;
    final AttentionSetUpdate.Operation operation;

    StoredAttentionSetEntry(AttentionSetUpdate attentionSetUpdate) {
      timestampMillis = attentionSetUpdate.timestamp().toEpochMilli();
      userId = attentionSetUpdate.account().get();
      reason = attentionSetUpdate.reason();
      operation = attentionSetUpdate.operation();
    }

    AttentionSetUpdate toAttentionSetUpdate() {
      return AttentionSetUpdate.createFromRead(
          Instant.ofEpochMilli(timestampMillis), Account.id(userId), operation, reason);
    }
  }

  @VisibleForTesting
  static List<String> getReviewerFieldValues(ReviewerSet reviewers) {
    List<String> r = new ArrayList<>(reviewers.asTable().size() * 2);
    for (Table.Cell<ReviewerStateInternal, Account.Id, Instant> c : reviewers.asTable().cellSet()) {
      String v = getReviewerFieldValue(c.getRowKey(), c.getColumnKey());
      r.add(v);
      r.add(v + ',' + c.getValue().toEpochMilli());
    }
    return r;
  }

  public static String getReviewerFieldValue(ReviewerStateInternal state, Account.Id id) {
    return state.toString() + ',' + id;
  }

  @VisibleForTesting
  static List<String> getReviewerByEmailFieldValues(ReviewerByEmailSet reviewersByEmail) {
    List<String> r = new ArrayList<>(reviewersByEmail.asTable().size() * 2);
    for (Table.Cell<ReviewerStateInternal, Address, Instant> c :
        reviewersByEmail.asTable().cellSet()) {
      String v = getReviewerByEmailFieldValue(c.getRowKey(), c.getColumnKey());
      r.add(v);
      if (c.getColumnKey().name() != null) {
        // Add another entry without the name to provide search functionality on the email
        Address emailOnly = Address.create(c.getColumnKey().email());
        r.add(getReviewerByEmailFieldValue(c.getRowKey(), emailOnly));
      }
      r.add(v + ',' + c.getValue().toEpochMilli());
    }
    return r;
  }

  public static String getReviewerByEmailFieldValue(ReviewerStateInternal state, Address adr) {
    return state.toString() + ',' + adr;
  }

  public static ReviewerSet parseReviewerFieldValues(Change.Id changeId, Iterable<String> values) {
    ImmutableTable.Builder<ReviewerStateInternal, Account.Id, Instant> b = ImmutableTable.builder();
    for (String v : values) {

      int i = v.indexOf(',');
      if (i < 0) {
        logger.atWarning().log(
            "Invalid value for reviewer field from change %s: %s", changeId.get(), v);
        continue;
      }

      int i2 = v.lastIndexOf(',');
      if (i2 == i) {
        // Don't log a warning here.
        // For each reviewer we store 2 values in the reviewer field, one value with the format
        // "<reviewer-type>,<account-id>" and one value with the format
        // "<reviewer-type>,<account-id>,<timestamp>" (see #getReviewerFieldValues(ReviewerSet)).
        // For parsing we are only interested in the "<reviewer-type>,<account-id>,<timestamp>"
        // value and the "<reviewer-type>,<account-id>" value is ignored here.
        continue;
      }

      Optional<ReviewerStateInternal> reviewerState = getReviewerState(v.substring(0, i));
      if (!reviewerState.isPresent()) {
        logger.atWarning().log(
            "Failed to parse reviewer state of reviewer field from change %s: %s",
            changeId.get(), v);
        continue;
      }

      Optional<Account.Id> accountId = Account.Id.tryParse(v.substring(i + 1, i2));
      if (!accountId.isPresent()) {
        logger.atWarning().log(
            "Failed to parse account ID of reviewer field from change %s: %s", changeId.get(), v);
        continue;
      }

      Long l = Longs.tryParse(v.substring(i2 + 1));
      if (l == null) {
        logger.atWarning().log(
            "Failed to parse timestamp of reviewer field from change %s: %s", changeId.get(), v);
        continue;
      }
      Instant timestamp = Instant.ofEpochMilli(l);

      b.put(reviewerState.get(), accountId.get(), timestamp);
    }
    return ReviewerSet.fromTable(b.build());
  }

  public static ReviewerByEmailSet parseReviewerByEmailFieldValues(
      Change.Id changeId, Iterable<String> values) {
    ImmutableTable.Builder<ReviewerStateInternal, Address, Instant> b = ImmutableTable.builder();
    for (String v : values) {
      int i = v.indexOf(',');
      if (i < 0) {
        logger.atWarning().log(
            "Invalid value for reviewer by email field from change %s: %s", changeId.get(), v);
        continue;
      }

      int i2 = v.lastIndexOf(',');
      if (i2 == i) {
        // Don't log a warning here.
        // For each reviewer we store 2 values in the reviewer field, one value with the format
        // "<reviewer-type>,<email>" and one value with the format
        // "<reviewer-type>,<email>,<timestamp>" (see
        // #getReviewerByEmailFieldValues(ReviewerByEmailSet)).
        // For parsing we are only interested in the "<reviewer-type>,<email>,<timestamp>" value
        // and the "<reviewer-type>,<email>" value is ignored here.
        continue;
      }

      Optional<ReviewerStateInternal> reviewerState = getReviewerState(v.substring(0, i));
      if (!reviewerState.isPresent()) {
        logger.atWarning().log(
            "Failed to parse reviewer state of reviewer by email field from change %s: %s",
            changeId.get(), v);
        continue;
      }

      Address address = Address.tryParse(v.substring(i + 1, i2));
      if (address == null) {
        logger.atWarning().log(
            "Failed to parse address of reviewer by email field from change %s: %s",
            changeId.get(), v);
        continue;
      }

      Long l = Longs.tryParse(v.substring(i2 + 1));
      if (l == null) {
        logger.atWarning().log(
            "Failed to parse timestamp of reviewer by email field from change %s: %s",
            changeId.get(), v);
        continue;
      }
      Instant timestamp = Instant.ofEpochMilli(l);

      b.put(reviewerState.get(), address, timestamp);
    }
    return ReviewerByEmailSet.fromTable(b.build());
  }

  private static Optional<ReviewerStateInternal> getReviewerState(String value) {
    try {
      return Optional.of(ReviewerStateInternal.valueOf(value));
    } catch (IllegalArgumentException | NullPointerException e) {
      return Optional.empty();
    }
  }

  private static ImmutableSet<Integer> getAttentionSetUserIds(ChangeData changeData) {
    return additionsOnly(changeData.attentionSet()).stream()
        .map(update -> update.account().get())
        .collect(toImmutableSet());
  }

  private static ImmutableSet<byte[]> storedAttentionSet(ChangeData changeData) {
    return changeData.attentionSet().stream()
        .map(StoredAttentionSetEntry::new)
        .map(storedAttentionSetEntry -> GSON.toJson(storedAttentionSetEntry).getBytes(UTF_8))
        .collect(toImmutableSet());
  }

  /**
   * Deserializes the specified attention set entries from JSON and stores them in the specified
   * change.
   */
  public static void parseAttentionSet(
      Collection<String> storedAttentionSetEntriesJson, ChangeData changeData) {
    ImmutableSet<AttentionSetUpdate> attentionSet =
        storedAttentionSetEntriesJson.stream()
            .map(
                entry -> GSON.fromJson(entry, StoredAttentionSetEntry.class).toAttentionSetUpdate())
            .collect(toImmutableSet());
    changeData.setAttentionSet(attentionSet);
  }

  private static Iterable<String> getLabels(ChangeData cd) {
    Set<String> allApprovals = new HashSet<>();
    Set<String> distinctApprovals = new HashSet<>();
    Table<String, Short, Integer> voteCounts = HashBasedTable.create();
    for (PatchSetApproval a : cd.currentApprovals()) {
      if (a.value() != 0 && !a.isLegacySubmit()) {
        increment(voteCounts, a.label(), a.value());
        Optional<LabelType> labelType = cd.getLabelTypes().byLabel(a.labelId());

        allApprovals.add(formatLabel(a.label(), a.value(), a.accountId()));
        allApprovals.addAll(getMagicLabelFormats(a.label(), a.value(), labelType, a.accountId()));
        allApprovals.addAll(getLabelOwnerFormats(a, cd, labelType));
        allApprovals.addAll(getLabelNonUploaderFormats(a, cd, labelType));
        distinctApprovals.add(formatLabel(a.label(), a.value()));
        distinctApprovals.addAll(
            getMagicLabelFormats(a.label(), a.value(), labelType, /* accountId= */ null));
      }
    }
    allApprovals.addAll(distinctApprovals);
    allApprovals.addAll(getCountLabelFormats(voteCounts, cd));
    return allApprovals;
  }

  private static void increment(Table<String, Short, Integer> table, String k1, short k2) {
    if (!table.contains(k1, k2)) {
      table.put(k1, k2, 1);
    } else {
      int val = table.get(k1, k2);
      table.put(k1, k2, val + 1);
    }
  }

  private static List<String> getCountLabelFormats(
      Table<String, Short, Integer> voteCounts, ChangeData cd) {
    List<String> allFormats = new ArrayList<>();
    for (String label : voteCounts.rowMap().keySet()) {
      Optional<LabelType> labelType = cd.getLabelTypes().byLabel(label);
      Map<Short, Integer> row = voteCounts.row(label);
      for (short vote : row.keySet()) {
        int count = row.get(vote);
        allFormats.addAll(getCountLabelFormats(labelType, label, vote, count));
      }
    }
    return allFormats;
  }

  private static List<String> getCountLabelFormats(
      Optional<LabelType> labelType, String label, short vote, int count) {
    List<String> formats =
        getMagicLabelFormats(label, vote, labelType, /* accountId= */ null, /* count= */ count);
    formats.add(formatLabel(label, vote, count));
    return formats;
  }

  /** Get magic label formats corresponding to the {MIN, MAX, ANY} label votes. */
  private static List<String> getMagicLabelFormats(
      String label, short labelVal, Optional<LabelType> labelType, @Nullable Account.Id accountId) {
    return getMagicLabelFormats(label, labelVal, labelType, accountId, /* count= */ null);
  }

  /** Get magic label formats corresponding to the {MIN, MAX, ANY} label votes. */
  private static List<String> getMagicLabelFormats(
      String label,
      short labelVal,
      Optional<LabelType> labelType,
      @Nullable Account.Id accountId,
      @Nullable Integer count) {
    List<String> labels = new ArrayList<>();
    if (labelType.isPresent()) {
      if (labelVal == labelType.get().getMaxPositive()) {
        labels.add(formatLabel(label, MagicLabelValue.MAX.name(), accountId, count));
      }
      if (labelVal == labelType.get().getMaxNegative()) {
        labels.add(formatLabel(label, MagicLabelValue.MIN.name(), accountId, count));
      }
    }
    labels.add(formatLabel(label, MagicLabelValue.ANY.name(), accountId, count));
    return labels;
  }

  private static List<String> getLabelOwnerFormats(
      PatchSetApproval a, ChangeData cd, Optional<LabelType> labelType) {
    List<String> allFormats = new ArrayList<>();
    if (cd.change().getOwner().equals(a.accountId())) {
      allFormats.add(formatLabel(a.label(), a.value(), ChangeQueryBuilder.OWNER_ACCOUNT_ID));
      allFormats.addAll(
          getMagicLabelFormats(
              a.label(), a.value(), labelType, ChangeQueryBuilder.OWNER_ACCOUNT_ID));
    }
    return allFormats;
  }

  private static List<String> getLabelNonUploaderFormats(
      PatchSetApproval a, ChangeData cd, Optional<LabelType> labelType) {
    List<String> allFormats = new ArrayList<>();
    if (!cd.currentPatchSet().uploader().equals(a.accountId())) {
      allFormats.add(formatLabel(a.label(), a.value(), ChangeQueryBuilder.NON_UPLOADER_ACCOUNT_ID));
      allFormats.addAll(
          getMagicLabelFormats(
              a.label(), a.value(), labelType, ChangeQueryBuilder.NON_UPLOADER_ACCOUNT_ID));
    }
    return allFormats;
  }

  public static Set<String> getAuthorParts(ChangeData cd) {
    return SchemaUtil.getPersonParts(cd.getAuthor());
  }

  public static Set<String> getAuthorNameAndEmail(ChangeData cd) {
    return getNameAndEmail(cd.getAuthor());
  }

  public static Set<String> getCommitterParts(ChangeData cd) {
    return SchemaUtil.getPersonParts(cd.getCommitter());
  }

  public static Set<String> getCommitterNameAndEmail(ChangeData cd) {
    return getNameAndEmail(cd.getCommitter());
  }

  private static Set<String> getNameAndEmail(PersonIdent person) {
    if (person == null) {
      return ImmutableSet.of();
    }

    String name = person.getName().toLowerCase(Locale.US);
    String email = person.getEmailAddress().toLowerCase(Locale.US);

    StringBuilder nameEmailBuilder = new StringBuilder();
    PersonIdent.appendSanitized(nameEmailBuilder, name);
    nameEmailBuilder.append(" <");
    PersonIdent.appendSanitized(nameEmailBuilder, email);
    nameEmailBuilder.append('>');

    return ImmutableSet.of(name, email, nameEmailBuilder.toString());
  }

  /**
   * The exact email address, or any part of the author name or email address, in the current patch
   * set.
   */
  public static final Field<ChangeData, Iterable<String>> AUTHOR =
      fullText(ChangeQueryBuilder.FIELD_AUTHOR).buildRepeatable(ChangeField::getAuthorParts);

  /** The exact name, email address and NameEmail of the author. */
  public static final Field<ChangeData, Iterable<String>> EXACT_AUTHOR =
      exact(ChangeQueryBuilder.FIELD_EXACTAUTHOR)
          .buildRepeatable(ChangeField::getAuthorNameAndEmail);

  /**
   * The exact email address, or any part of the committer name or email address, in the current
   * patch set.
   */
  public static final Field<ChangeData, Iterable<String>> COMMITTER =
      fullText(ChangeQueryBuilder.FIELD_COMMITTER).buildRepeatable(ChangeField::getCommitterParts);

  /** The exact name, email address, and NameEmail of the committer. */
  public static final Field<ChangeData, Iterable<String>> EXACT_COMMITTER =
      exact(ChangeQueryBuilder.FIELD_EXACTCOMMITTER)
          .buildRepeatable(ChangeField::getCommitterNameAndEmail);

  /** Serialized change object, used for pre-populating results. */
  public static final Field<ChangeData, byte[]> CHANGE =
      storedOnly("_change")
          .build(
              changeGetter(change -> toProto(ChangeProtoConverter.INSTANCE, change)),
              (cd, field) -> cd.setChange(parseProtoFrom(field, ChangeProtoConverter.INSTANCE)));

  /** Serialized approvals for the current patch set, used for pre-populating results. */
  public static final Field<ChangeData, Iterable<byte[]>> APPROVAL =
      storedOnly("_approval")
          .buildRepeatable(
              cd -> toProtos(PatchSetApprovalProtoConverter.INSTANCE, cd.currentApprovals()),
              (cd, field) ->
                  cd.setCurrentApprovals(
                      decodeProtos(field, PatchSetApprovalProtoConverter.INSTANCE)));

  public static String formatLabel(String label, int value) {
    return formatLabel(label, value, /* accountId= */ null, /* count= */ null);
  }

  public static String formatLabel(String label, int value, @Nullable Integer count) {
    return formatLabel(label, value, /* accountId= */ null, count);
  }

  public static String formatLabel(String label, int value, Account.Id accountId) {
    return formatLabel(label, value, accountId, /* count= */ null);
  }

  public static String formatLabel(
      String label, int value, @Nullable Account.Id accountId, @Nullable Integer count) {
    return label.toLowerCase()
        + (value >= 0 ? "+" : "")
        + value
        + (accountId != null ? "," + formatAccount(accountId) : "")
        + (count != null ? ",count=" + count : "");
  }

  public static String formatLabel(
      String label, String value, @Nullable Account.Id accountId, @Nullable Integer count) {
    return label.toLowerCase()
        + "="
        + value
        + (accountId != null ? "," + formatAccount(accountId) : "")
        + (count != null ? ",count=" + count : "");
  }

  private static String formatAccount(Account.Id accountId) {
    if (ChangeQueryBuilder.OWNER_ACCOUNT_ID.equals(accountId)) {
      return ChangeQueryBuilder.ARG_ID_OWNER;
    } else if (ChangeQueryBuilder.NON_UPLOADER_ACCOUNT_ID.equals(accountId)) {
      return ChangeQueryBuilder.ARG_ID_NON_UPLOADER;
    }
    return Integer.toString(accountId.get());
  }


  public static final Field<ChangeData, String> COMMIT_MESSAGE_FIELD = Field.<ChangeData, String>builder("CommitMessage", Field.STRING_TYPE).build(ChangeData::commitMessage);

  /** Commit message of the current patch set. */
  public static final FieldSpec COMMIT_MESSAGE_FULL_TEXT_SPEC = COMMIT_MESSAGE_FIELD.fullText(ChangeQueryBuilder.FIELD_MESSAGE);


  /** Commit message of the current patch set. */
  public static final FieldSpec COMMIT_MESSAGE_EXACT_SPEC = COMMIT_MESSAGE_FIELD.exact(ChangeQueryBuilder.FIELD_MESSAGE_EXACT);


  public static final Field<ChangeData, Integer> ADDED_FIELD = Field.<ChangeData, Integer>builder("LinesAdded", Field.INTEGER_TYPE).build(cd -> cd.changedLines().isPresent() ? cd.changedLines().get().insertions : null,
  (cd, field) -> {
    if (field != null) {
      cd.setLinesInserted(field);
    }
  });

  public static final FieldSpec ADDED_SPEC = ADDED_FIELD.integerRange(ChangeQueryBuilder.FIELD_ADDED);


  /** Determines if this change is private. */
  public static final Field<ChangeData, String> PRIVATE_FIELD = Field.<ChangeData, String>builder("Private", Field.STRING_TYPE).size(1).
      build(cd -> cd.change().isPrivate() ? "1" : "0");

  public static final FieldSpec PRIVATE_SPEC =
      PRIVATE_FIELD.exact(ChangeQueryBuilder.FIELD_PRIVATE);

  /** Determines if this change is work in progress. */
  public static final Field<ChangeData, String> WIP_FIELD = Field.<ChangeData, String>builder("WIP", Field.STRING_TYPE).size(1).
      build(cd -> cd.change().isWorkInProgress() ? "1" : "0");

  public static final FieldSpec WIP_SPEC =
      WIP_FIELD.exact(ChangeQueryBuilder.FIELD_WIP);

  /** Determines if this change has started review. */
  public static final Field<ChangeData, String> STARTED_FIELD = Field.<ChangeData, String>builder("Started", Field.STRING_TYPE).size(1).
          build(cd -> cd.change().hasReviewStarted() ? "1" : "0");

  public static final FieldSpec STARTED_SPEC =
      STARTED_FIELD.exact(ChangeQueryBuilder.FIELD_STARTED);

  /** Users who have commented on this change. */
  public static final Field<ChangeData, Iterable<Integer>> COMMENTBY_FIELD = Field.<ChangeData, Iterable<Integer>>builder("CommentBy", Field.ITERABLE_INTEGER_TYPE)
          .build(
              cd ->
                  Stream.concat(
                          cd.messages().stream().map(ChangeMessage::getAuthor),
                          cd.publishedComments().stream().map(c -> c.author.getId()))
                      .filter(Objects::nonNull)
                      .map(Account.Id::get)
                      .collect(toSet()));

  public static final FieldSpec COMMENTBY_SPEC = COMMENTBY_FIELD.integer(ChangeQueryBuilder.FIELD_COMMENTBY);


  public static final Field<ChangeData, Iterable<Entities.PatchSet>> PATCH_SET_FIELD
      = Field.<ChangeData, Iterable<Entities.PatchSet>>builder("PatchSet",   new TypeToken<Iterable<Entities.PatchSet>>() {}).stored(true).
      build((cd) -> toRawProtos(PatchSetProtoConverter.INSTANCE, cd.patchSets()),
          (cd, field) -> cd.setPatchSets(decodeRawProtos(field, PatchSetProtoConverter.INSTANCE)), PatchSetProtoConverter.INSTANCE);

  public static final FieldSpec PATCH_SET_SPEC = PATCH_SET_FIELD.addFieldSpec("_patch_set", SearchOptions.STORE_ONLY);

  /**
   * JSON type for storing SubmitRecords.
   *
   * <p>Stored fields need to use a stable format over a long period; this type insulates the index
   * from implementation changes in SubmitRecord itself.
   */
  public static class StoredSubmitRecord {
    static class StoredLabel {
      String label;
      SubmitRecord.Label.Status status;
      Integer appliedBy;
    }

    static class StoredRequirement {
      String fallbackText;
      String type;
      @Deprecated Map<String, String> data;
    }

    String ruleName;
    SubmitRecord.Status status;
    List<StoredLabel> labels;
    List<StoredRequirement> requirements;
    String errorMessage;

    public StoredSubmitRecord(SubmitRecord rec) {
      this.ruleName = rec.ruleName;
      this.status = rec.status;
      this.errorMessage = rec.errorMessage;
      if (rec.labels != null) {
        this.labels = new ArrayList<>(rec.labels.size());
        for (SubmitRecord.Label label : rec.labels) {
          StoredLabel sl = new StoredLabel();
          sl.label = label.label;
          sl.status = label.status;
          sl.appliedBy = label.appliedBy != null ? label.appliedBy.get() : null;
          this.labels.add(sl);
        }
      }
      if (rec.requirements != null) {
        this.requirements = new ArrayList<>(rec.requirements.size());
        for (LegacySubmitRequirement requirement : rec.requirements) {
          StoredRequirement sr = new StoredRequirement();
          sr.type = requirement.type();
          sr.fallbackText = requirement.fallbackText();
          // For backwards compatibility, write an empty map to the index.
          // This is required, because the LegacySubmitRequirement AutoValue can't
          // handle null in the old code.
          // TODO(hiesel): Remove once we have rolled out the new code
          //  and waited long enough to not need to roll back.
          sr.data = ImmutableMap.of();
          this.requirements.add(sr);
        }
      }
    }

    public SubmitRecord toSubmitRecord() {
      SubmitRecord rec = new SubmitRecord();
      rec.ruleName = ruleName;
      rec.status = status;
      rec.errorMessage = errorMessage;
      if (labels != null) {
        rec.labels = new ArrayList<>(labels.size());
        for (StoredLabel label : labels) {
          SubmitRecord.Label srl = new SubmitRecord.Label();
          srl.label = label.label;
          srl.status = label.status;
          srl.appliedBy = label.appliedBy != null ? Account.id(label.appliedBy) : null;
          rec.labels.add(srl);
        }
      }
      if (requirements != null) {
        rec.requirements = new ArrayList<>(requirements.size());
        for (StoredRequirement req : requirements) {
          LegacySubmitRequirement sr =
              LegacySubmitRequirement.builder()
                  .setType(req.type)
                  .setFallbackText(req.fallbackText)
                  .build();
          rec.requirements.add(sr);
        }
      }
      return rec;
    }
  }


  public static void parseSubmitRecords(
      Collection<String> values, SubmitRuleOptions opts, ChangeData out) {
    List<SubmitRecord> records = parseSubmitRecords(values);
    out.setSubmitRecords(opts, records);
  }

  @VisibleForTesting
  static List<SubmitRecord> parseSubmitRecords(Collection<String> values) {
    return values.stream()
        .map(v -> GSON.fromJson(v, StoredSubmitRecord.class).toSubmitRecord())
        .collect(toList());
  }

  @VisibleForTesting
  static List<byte[]> storedSubmitRecords(List<SubmitRecord> records) {
    return Lists.transform(records, r -> GSON.toJson(new StoredSubmitRecord(r)).getBytes(UTF_8));
  }

  private static Iterable<byte[]> storedSubmitRecords(ChangeData cd, SubmitRuleOptions opts) {
    return storedSubmitRecords(cd.submitRecords(opts));
  }

  public static List<String> formatSubmitRecordValues(ChangeData cd) {
    Set<String> submitRecordValues = new HashSet<>();
    submitRecordValues.addAll(
        formatSubmitRecordValues(
            cd.submitRecords(SUBMIT_RULE_OPTIONS_STRICT), cd.change().getOwner()));
    // Also backfill results of submit requirements such that users can query submit requirement
    // results using the label operator, for example a query with "label:CR=NEED" will match with
    // changes that have a submit-requirement with name="CR" and status=UNSATISFIED.
    // Reason: We are preserving backward compatibility of the operators `label:$name=$status`
    // which were previously working with submit records. Now admins can configure submit
    // requirements and continue querying them with the label operator.
    submitRecordValues.addAll(formatSubmitRequirementValues(cd.submitRequirements().values()));
    return submitRecordValues.stream().collect(Collectors.toList());
  }

  @VisibleForTesting
  static List<String> formatSubmitRecordValues(List<SubmitRecord> records, Account.Id changeOwner) {
    List<String> result = new ArrayList<>();
    for (SubmitRecord rec : records) {
      result.add(rec.status.name());
      if (rec.labels == null) {
        continue;
      }
      for (SubmitRecord.Label label : rec.labels) {
        String sl = label.status.toString() + ',' + label.label.toLowerCase();
        result.add(sl);
        String slc = sl + ',';
        if (label.appliedBy != null) {
          result.add(slc + label.appliedBy.get());
          if (label.appliedBy.equals(changeOwner)) {
            result.add(slc + ChangeQueryBuilder.OWNER_ACCOUNT_ID.get());
          }
        }
      }
    }
    return result;
  }

  /**
   * Generate submit requirement result formats that are compatible with the legacy submit record
   * statuses.
   */
  @VisibleForTesting
  static List<String> formatSubmitRequirementValues(Collection<SubmitRequirementResult> srResults) {
    List<String> result = new ArrayList<>();
    for (SubmitRequirementResult srResult : srResults) {
      switch (srResult.status()) {
        case SATISFIED:
        case OVERRIDDEN:
        case FORCED:
          result.add(
              SubmitRecord.Label.Status.OK.name()
                  + ","
                  + srResult.submitRequirement().name().toLowerCase());
          result.add(
              SubmitRecord.Label.Status.MAY.name()
                  + ","
                  + srResult.submitRequirement().name().toLowerCase());
          break;
        case UNSATISFIED:
          result.add(
              SubmitRecord.Label.Status.NEED.name()
                  + ","
                  + srResult.submitRequirement().name().toLowerCase());
          result.add(
              SubmitRecord.Label.Status.REJECT.name()
                  + ","
                  + srResult.submitRequirement().name().toLowerCase());
          break;
        case NOT_APPLICABLE:
        case ERROR:
          result.add(
              SubmitRecord.Label.Status.IMPOSSIBLE.name()
                  + ","
                  + srResult.submitRequirement().name().toLowerCase());
      }
    }
    return result;
  }

  /** Serialized submit requirements, used for pre-populating results. */
  public static final Field<ChangeData, Iterable<byte[]>> STORED_SUBMIT_REQUIREMENTS_FILED = Field.<ChangeData, Iterable<byte[]>>builder("StoredSubmitRequirements", Field.ITERABLE_BYTE_ARRAY_TYPE)
          .build(
              cd ->
                  toProtos(
                      SubmitRequirementProtoConverter.INSTANCE, cd.submitRequirements().values()),
              (cd, field) -> parseSubmitRequirements(field, cd));

  public static FieldSpec STORED_SUBMIT_REQUIREMENTS_SPEC = STORED_SUBMIT_REQUIREMENTS_FILED.storedOnly("full_submit_requirements");

  private static void parseSubmitRequirements(Iterable<byte[]> values, ChangeData out) {
    out.setSubmitRequirements(
        StreamSupport.stream(values.spliterator(), false)
            .map(
                f ->
                    SubmitRequirementProtoConverter.INSTANCE.fromProto(
                        Protos.parseUnchecked(
                            SubmitRequirementProtoConverter.INSTANCE.getParser(), f)))
            .filter(sr -> !sr.isLegacy())
            .collect(
                ImmutableMap.toImmutableMap(sr -> sr.submitRequirement(), Function.identity())));
  }

  /**
   * All values of all refs that were used in the course of indexing this document.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name:[hex sha]}.
   */
  public static final Field<ChangeData, Iterable<byte[]>> REF_STATE_FILED = Field.<ChangeData, Iterable<byte[]>>builder("RefState",  Field.ITERABLE_BYTE_ARRAY_TYPE).stored().build(
              cd -> {
                List<byte[]> result = new ArrayList<>();
                cd.getRefStates()
                    .entries()
                    .forEach(e -> result.add(e.getValue().toByteArray(e.getKey())));
                return result;
              },
              (cd, field) -> cd.setRefStates(RefState.parseStates(field)));
  public static final FieldSpec REF_STATE_SPEC = REF_STATE_FILED.storedOnly("ref_state");

  /**
   * All ref wildcard patterns that were used in the course of indexing this document.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name/*}. See {@link
   * RefStatePattern} for the pattern format.
   */
  public static final Field<ChangeData, Iterable<byte[]>> REF_STATE_PATTERN_FIELD = Field.<ChangeData, Iterable<byte[]>>builder("RefState",   Field.ITERABLE_BYTE_ARRAY_TYPE).stored().build(
              cd -> {
                Change.Id id = cd.getId();
                Project.NameKey project = cd.change().getProject();
                List<byte[]> result = new ArrayList<>(3);
                result.add(
                    RefStatePattern.create(
                            RefNames.REFS_USERS + "*/" + RefNames.EDIT_PREFIX + id + "/*")
                        .toByteArray(project));
                result.add(
                    RefStatePattern.create(RefNames.refsStarredChangesPrefix(id) + "*")
                        .toByteArray(allUsers(cd)));
                result.add(
                    RefStatePattern.create(RefNames.refsDraftCommentsPrefix(id) + "*")
                        .toByteArray(allUsers(cd)));
                return result;
              },
              (cd, field) -> cd.setRefStatePatterns(field));

  public static final FieldSpec REF_STATE_PATTERN_SPEC = REF_STATE_PATTERN_FIELD.storedOnly("ref_state_pattern");

  private static String getTopic(ChangeData cd) {
    Change c = cd.change();
    if (c == null) {
      return null;
    }
    return firstNonNull(c.getTopic(), "");
  }

  private static <T> List<byte[]> toProtos(ProtoConverter<?, T> converter, Collection<T> objects) {
    return objects.stream().map(object -> toProto(converter, object)).collect(toImmutableList());
  }

  private static <T> byte[] toProto(ProtoConverter<?, T> converter, T object) {
    return Protos.toByteArray(converter.toProto(object));
  }

  private static <P extends MessageLite, T> List<P> toRawProtos(ProtoConverter<P, T> converter, Collection<T> objects) {
    return objects.stream().map(object -> toRawProto(converter, object)).collect(toImmutableList());
  }

  private static <P extends MessageLite, T> P toRawProto(ProtoConverter<P, T> converter, T object) {
    return converter.toProto(object);
  }

  private static <T> List<T> decodeProtos(Iterable<byte[]> raw, ProtoConverter<?, T> converter) {
    return StreamSupport.stream(raw.spliterator(), false)
        .map(bytes -> parseProtoFrom(bytes, converter))
        .collect(toImmutableList());
  }

  private static <P extends MessageLite, T> List<T> decodeRawProtos(Iterable<P> raw, ProtoConverter<P, T> converter) {
    return StreamSupport.stream(raw.spliterator(), false)
        .map(proto -> decodeRawProto(proto, converter))
        .collect(toImmutableList());
  }

  private static <P extends MessageLite, T> T decodeRawProto(
      P raw, ProtoConverter<P, T> converter) {
    return converter.fromProto(raw);
  }

  private static <P extends MessageLite, T> T parseProtoFrom(
      byte[] bytes, ProtoConverter<P, T> converter) {
    P message = Protos.parseUnchecked(converter.getParser(), bytes, 0, bytes.length);
    return converter.fromProto(message);
  }

  private static <T> Field.Getter<ChangeData, T> changeGetter(Function<Change, T> func) {
    return in -> in.change() != null ? func.apply(in.change()) : null;
  }

  private static AllUsersName allUsers(ChangeData cd) {
    return cd.getAllUsersNameForIndexing();
  }
}
