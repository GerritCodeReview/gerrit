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
import com.google.gerrit.index.IndexedField;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.SchemaFieldDefs;
import com.google.gerrit.index.SchemaUtil;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.cache.proto.Cache;
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
import java.util.Arrays;
import java.util.Collection;
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
 *
 * <p>Note that this class does not override {@link Object#equals(Object)}. It relies on instances
 * being singletons so that the default (i.e. reference) comparison works.
 */
public class ChangeField {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final int NO_ASSIGNEE = -1;

  private static final Gson GSON = OutputFormat.JSON_COMPACT.newGson();

  /**
   * To avoid the non-google dependency on org.apache.lucene.index.IndexWriter.MAX_TERM_LENGTH it is
   * redefined here.
   */
  public static final int MAX_TERM_LENGTH = (1 << 15) - 2;

  // TODO: Rename LEGACY_ID to NUMERIC_ID
  /** Legacy change ID. */
  public static final IndexedField<ChangeData, String> NUMERIC_ID_STR_FIELD =
      IndexedField.<ChangeData>stringBuilder("NumericIdStr")
          .stored()
          .required()
          // The numeric change id is integer in string form
          .size(10)
          .build(cd -> String.valueOf(cd.getVirtualId().get()));

  public static final IndexedField<ChangeData, String>.SearchSpec NUMERIC_ID_STR_SPEC =
      NUMERIC_ID_STR_FIELD.exact("legacy_id_str");

  /** Newer style Change-Id key. */
  public static final IndexedField<ChangeData, String> CHANGE_ID_FIELD =
      IndexedField.<ChangeData>stringBuilder("ChangeId")
          .required()
          // The new style key is in form Isha1
          .size(41)
          .build(changeGetter(c -> c.getKey().get()));

  public static final IndexedField<ChangeData, String>.SearchSpec CHANGE_ID_SPEC =
      CHANGE_ID_FIELD.prefix(ChangeQueryBuilder.FIELD_CHANGE_ID);

  /** Change status string, in the same format as {@code status:}. */
  public static final IndexedField<ChangeData, String> STATUS_FIELD =
      IndexedField.<ChangeData>stringBuilder("Status")
          .required()
          .size(20)
          .build(changeGetter(c -> ChangeStatusPredicate.canonicalize(c.getStatus())));

  public static final IndexedField<ChangeData, String>.SearchSpec STATUS_SPEC =
      STATUS_FIELD.exact(ChangeQueryBuilder.FIELD_STATUS);

  /** Project containing the change. */
  public static final IndexedField<ChangeData, String> PROJECT_FIELD =
      IndexedField.<ChangeData>stringBuilder("Project")
          .required()
          .stored()
          .size(200)
          .build(changeGetter(c -> c.getProject().get()));

  public static final IndexedField<ChangeData, String>.SearchSpec PROJECT_SPEC =
      PROJECT_FIELD.exact(ChangeQueryBuilder.FIELD_PROJECT);

  /** Project containing the change, as a prefix field. */
  public static final IndexedField<ChangeData, String>.SearchSpec PROJECTS_SPEC =
      PROJECT_FIELD.prefix(ChangeQueryBuilder.FIELD_PROJECTS);

  /** Reference (aka branch) the change will submit onto. */
  public static final IndexedField<ChangeData, String> REF_FIELD =
      IndexedField.<ChangeData>stringBuilder("Ref")
          .required()
          .size(300)
          .build(changeGetter(c -> c.getDest().branch()));

  public static final IndexedField<ChangeData, String>.SearchSpec REF_SPEC =
      REF_FIELD.exact(ChangeQueryBuilder.FIELD_REF);

  /** Topic, a short annotation on the branch. */
  public static final IndexedField<ChangeData, String> TOPIC_FIELD =
      IndexedField.<ChangeData>stringBuilder("Topic").size(500).build(ChangeField::getTopic);

  public static final IndexedField<ChangeData, String>.SearchSpec EXACT_TOPIC =
      TOPIC_FIELD.exact("topic4");

  /** Topic, a short annotation on the branch. */
  public static final IndexedField<ChangeData, String>.SearchSpec FUZZY_TOPIC =
      TOPIC_FIELD.fullText("topic5");

  /** Topic, a short annotation on the branch. */
  public static final IndexedField<ChangeData, String>.SearchSpec PREFIX_TOPIC =
      TOPIC_FIELD.prefix("topic6");

  /** {@link com.google.gerrit.entities.SubmissionId} assigned by MergeOp. */
  public static final IndexedField<ChangeData, String> SUBMISSIONID_FIELD =
      IndexedField.<ChangeData>stringBuilder("SubmissionId")
          .size(500)
          .build(changeGetter(Change::getSubmissionId));

  public static final IndexedField<ChangeData, String>.SearchSpec SUBMISSIONID_SPEC =
      SUBMISSIONID_FIELD.exact(ChangeQueryBuilder.FIELD_SUBMISSIONID);

  /** Last update time since January 1, 1970. */
  // TODO(issue-15518): Migrate type for timestamp index fields from Timestamp to Instant
  public static final IndexedField<ChangeData, Timestamp> UPDATED_FIELD =
      IndexedField.<ChangeData>timestampBuilder("LastUpdated")
          .stored()
          .build(changeGetter(change -> Timestamp.from(change.getLastUpdatedOn())));

  public static final IndexedField<ChangeData, Timestamp>.SearchSpec UPDATED_SPEC =
      UPDATED_FIELD.timestamp("updated2");

  /** When this change was merged, time since January 1, 1970. */
  // TODO(issue-15518): Migrate type for timestamp index fields from Timestamp to Instant
  public static final IndexedField<ChangeData, Timestamp> MERGED_ON_FIELD =
      IndexedField.<ChangeData>timestampBuilder("MergedOn")
          .stored()
          .build(
              cd -> cd.getMergedOn().map(Timestamp::from).orElse(null),
              (cd, field) -> cd.setMergedOn(field != null ? field.toInstant() : null));

  public static final IndexedField<ChangeData, Timestamp>.SearchSpec MERGED_ON_SPEC =
      MERGED_ON_FIELD.timestamp(ChangeQueryBuilder.FIELD_MERGED_ON);

  /** List of full file paths modified in the current patch set. */
  public static final IndexedField<ChangeData, Iterable<String>> PATH_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("ModifiedFile")
          .build(cd -> firstNonNull(cd.currentFilePaths(), ImmutableList.of()));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec PATH_SPEC =
      PATH_FIELD
          // Named for backwards compatibility.
          .exact(ChangeQueryBuilder.FIELD_FILE);

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
  public static final IndexedField<ChangeData, Iterable<String>> HASHTAG_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("Hashtag")
          .size(200)
          .build(cd -> cd.hashtags().stream().map(String::toLowerCase).collect(toSet()));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec HASHTAG_SPEC =
      HASHTAG_FIELD.exact(ChangeQueryBuilder.FIELD_HASHTAG);

  /** Hashtags as fulltext field for in-string search. */
  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec FUZZY_HASHTAG =
      HASHTAG_FIELD.fullText("hashtag2");

  /** Hashtags as prefix field for in-string search. */
  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec PREFIX_HASHTAG =
      HASHTAG_FIELD.prefix("hashtag3");

  /** Hashtags with original case. */
  public static final IndexedField<ChangeData, Iterable<byte[]>> HASHTAG_CASE_AWARE_FIELD =
      IndexedField.<ChangeData>iterableByteArrayBuilder("HashtagCaseAware")
          .stored()
          .build(
              cd -> cd.hashtags().stream().map(t -> t.getBytes(UTF_8)).collect(toSet()),
              (cd, field) ->
                  cd.setHashtags(
                      StreamSupport.stream(field.spliterator(), false)
                          .map(f -> new String(f, UTF_8))
                          .collect(toImmutableSet())));

  public static final IndexedField<ChangeData, Iterable<byte[]>>.SearchSpec
      HASHTAG_CASE_AWARE_SPEC = HASHTAG_CASE_AWARE_FIELD.storedOnly("_hashtag");

  /** Components of each file path modified in the current patch set. */
  public static final IndexedField<ChangeData, Iterable<String>> FILE_PART_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("FilePart").build(ChangeField::getFileParts);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec FILE_PART_SPEC =
      FILE_PART_FIELD.exact(ChangeQueryBuilder.FIELD_FILEPART);

  /** File extensions of each file modified in the current patch set. */
  public static final IndexedField<ChangeData, Iterable<String>> EXTENSION_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("Extension")
          .size(100)
          .build(ChangeField::getExtensions);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec EXTENSION_SPEC =
      EXTENSION_FIELD.exact(ChangeQueryBuilder.FIELD_EXTENSION);

  public static Set<String> getExtensions(ChangeData cd) {
    return extensions(cd).collect(toSet());
  }

  /**
   * File extensions of each file modified in the current patch set as a sorted list. The purpose of
   * this field is to allow matching changes that only touch files with certain file extensions.
   */
  public static final IndexedField<ChangeData, String> ONLY_EXTENSIONS_FIELD =
      IndexedField.<ChangeData>stringBuilder("OnlyExtensions")
          .build(ChangeField::getAllExtensionsAsList);

  public static final IndexedField<ChangeData, String>.SearchSpec ONLY_EXTENSIONS_SPEC =
      ONLY_EXTENSIONS_FIELD.exact(ChangeQueryBuilder.FIELD_ONLY_EXTENSIONS);

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
  public static final IndexedField<ChangeData, Iterable<String>> FOOTER_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("Footer").build(ChangeField::getFooters);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec FOOTER_SPEC =
      FOOTER_FIELD.exact(ChangeQueryBuilder.FIELD_FOOTER);

  public static Set<String> getFooters(ChangeData cd) {
    return cd.commitFooters().stream()
        .map(f -> f.toString().toLowerCase(Locale.US))
        .collect(toSet());
  }

  /** Footers from the commit message of the current patch set. */
  public static final IndexedField<ChangeData, Iterable<String>> FOOTER_NAME_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("FooterName")
          .build(ChangeField::getFootersNames);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec FOOTER_NAME =
      FOOTER_NAME_FIELD.exact(ChangeQueryBuilder.FIELD_FOOTER_NAME);

  public static Set<String> getFootersNames(ChangeData cd) {
    return cd.commitFooters().stream().map(f -> f.getKey()).collect(toSet());
  }

  /** Folders that are touched by the current patch set. */
  public static final IndexedField<ChangeData, Iterable<String>> DIRECTORY_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("DirField").build(ChangeField::getDirectories);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec DIRECTORY_SPEC =
      DIRECTORY_FIELD.exact(ChangeQueryBuilder.FIELD_DIRECTORY);

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
  public static final IndexedField<ChangeData, Integer> OWNER_FIELD =
      IndexedField.<ChangeData>integerBuilder("Owner")
          .required()
          .build(changeGetter(c -> c.getOwner().get()));

  public static final IndexedField<ChangeData, Integer>.SearchSpec OWNER_SPEC =
      OWNER_FIELD.integer(ChangeQueryBuilder.FIELD_OWNER);

  /** Uploader of the latest patch set. */
  public static final IndexedField<ChangeData, Integer> UPLOADER_FIELD =
      IndexedField.<ChangeData>integerBuilder("Uploader")
          .required()
          .build(cd -> cd.currentPatchSet().uploader().get());

  public static final IndexedField<ChangeData, Integer>.SearchSpec UPLOADER_SPEC =
      UPLOADER_FIELD.integer(ChangeQueryBuilder.FIELD_UPLOADER);

  /** References the source change number that this change was cherry-picked from. */
  public static final IndexedField<ChangeData, Integer> CHERRY_PICK_OF_CHANGE_FIELD =
      IndexedField.<ChangeData>integerBuilder("CherryPickOfChange")
          .build(
              cd ->
                  cd.change().getCherryPickOf() != null
                      ? cd.change().getCherryPickOf().changeId().get()
                      : null);

  public static final IndexedField<ChangeData, Integer>.SearchSpec CHERRY_PICK_OF_CHANGE =
      CHERRY_PICK_OF_CHANGE_FIELD.integer(ChangeQueryBuilder.FIELD_CHERRY_PICK_OF_CHANGE);

  /** References the source change patch-set that this change was cherry-picked from. */
  public static final IndexedField<ChangeData, Integer> CHERRY_PICK_OF_PATCHSET_FIELD =
      IndexedField.<ChangeData>integerBuilder("CherryPickOfPatchset")
          .build(
              cd ->
                  cd.change().getCherryPickOf() != null
                      ? cd.change().getCherryPickOf().get()
                      : null);

  public static final IndexedField<ChangeData, Integer>.SearchSpec CHERRY_PICK_OF_PATCHSET =
      CHERRY_PICK_OF_PATCHSET_FIELD.integer(ChangeQueryBuilder.FIELD_CHERRY_PICK_OF_PATCHSET);

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

  /**
   * Users included in the attention set of the change. This omits timestamp, reason and possible
   * future fields.
   *
   * @see #ATTENTION_SET_FULL_SPEC
   */
  public static final IndexedField<ChangeData, Iterable<Integer>> ATTENTION_SET_USERS_FIELD =
      IndexedField.<ChangeData>iterableIntegerBuilder("AttentionSetUsers")
          .build(ChangeField::getAttentionSetUserIds);

  public static final IndexedField<ChangeData, Iterable<Integer>>.SearchSpec ATTENTION_SET_USERS =
      ATTENTION_SET_USERS_FIELD.integer(ChangeQueryBuilder.FIELD_ATTENTION_SET_USERS);

  /** Number of changes that contain attention set. */
  public static final IndexedField<ChangeData, Integer> ATTENTION_SET_USERS_COUNT_FIELD =
      IndexedField.<ChangeData>integerBuilder("AttentionSetUsersCount")
          .stored()
          .build(cd -> additionsOnly(cd.attentionSet()).size());

  public static final IndexedField<ChangeData, Integer>.SearchSpec ATTENTION_SET_USERS_COUNT =
      ATTENTION_SET_USERS_COUNT_FIELD.integerRange(
          ChangeQueryBuilder.FIELD_ATTENTION_SET_USERS_COUNT);

  /**
   * The full attention set data including timestamp, reason and possible future fields.
   *
   * @see #ATTENTION_SET_USERS
   */
  public static final IndexedField<ChangeData, Iterable<byte[]>> ATTENTION_SET_FULL_FIELD =
      IndexedField.<ChangeData>iterableByteArrayBuilder("AttentionSetFull")
          .stored()
          .required()
          .build(
              ChangeField::storedAttentionSet,
              (cd, value) ->
                  parseAttentionSet(
                      StreamSupport.stream(value.spliterator(), false)
                          .map(v -> new String(v, UTF_8))
                          .collect(toImmutableSet()),
                      cd));

  public static final IndexedField<ChangeData, Iterable<byte[]>>.SearchSpec
      ATTENTION_SET_FULL_SPEC =
          ATTENTION_SET_FULL_FIELD.storedOnly(ChangeQueryBuilder.FIELD_ATTENTION_SET_FULL);

  /** The user assigned to the change. */
  // The getter always returns NO_ASSIGNEE, since assignee field is deprecated.
  @Deprecated
  public static final IndexedField<ChangeData, Integer> ASSIGNEE_FIELD =
      IndexedField.<ChangeData>integerBuilder("Assignee").build(changeGetter(c -> NO_ASSIGNEE));

  @Deprecated
  public static final IndexedField<ChangeData, Integer>.SearchSpec ASSIGNEE_SPEC =
      ASSIGNEE_FIELD.integer(ChangeQueryBuilder.FIELD_ASSIGNEE);

  /** Reviewer(s) associated with the change. */
  public static final IndexedField<ChangeData, Iterable<String>> REVIEWER_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("Reviewer")
          .stored()
          .build(
              cd -> getReviewerFieldValues(cd.reviewers()),
              (cd, field) -> cd.setReviewers(parseReviewerFieldValues(cd.getId(), field)));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec REVIEWER_SPEC =
      REVIEWER_FIELD.exact("reviewer2");

  /** Reviewer(s) associated with the change that do not have a gerrit account. */
  public static final IndexedField<ChangeData, Iterable<String>> REVIEWER_BY_EMAIL_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("ReviewerByEmail")
          .stored()
          .build(
              cd -> getReviewerByEmailFieldValues(cd.reviewersByEmail()),
              (cd, field) ->
                  cd.setReviewersByEmail(parseReviewerByEmailFieldValues(cd.getId(), field)));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec REVIEWER_BY_EMAIL =
      REVIEWER_BY_EMAIL_FIELD.exact("reviewer_by_email");

  /** Reviewer(s) modified during change's current WIP phase. */
  public static final IndexedField<ChangeData, Iterable<String>> PENDING_REVIEWER_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("PendingReviewer")
          .stored()
          .build(
              cd -> getReviewerFieldValues(cd.pendingReviewers()),
              (cd, field) -> cd.setPendingReviewers(parseReviewerFieldValues(cd.getId(), field)));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec PENDING_REVIEWER_SPEC =
      PENDING_REVIEWER_FIELD.exact(ChangeQueryBuilder.FIELD_PENDING_REVIEWER);

  /** Reviewer(s) by email modified during change's current WIP phase. */
  public static final IndexedField<ChangeData, Iterable<String>> PENDING_REVIEWER_BY_EMAIL_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("PendingReviewerByEmail")
          .stored()
          .build(
              cd -> getReviewerByEmailFieldValues(cd.pendingReviewersByEmail()),
              (cd, field) ->
                  cd.setPendingReviewersByEmail(
                      parseReviewerByEmailFieldValues(cd.getId(), field)));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec
      PENDING_REVIEWER_BY_EMAIL =
          PENDING_REVIEWER_BY_EMAIL_FIELD.exact(ChangeQueryBuilder.FIELD_PENDING_REVIEWER_BY_EMAIL);

  /** References a change that this change reverts. */
  public static final IndexedField<ChangeData, Integer> REVERT_OF_FIELD =
      IndexedField.<ChangeData>integerBuilder("RevertOf")
          .build(cd -> cd.change().getRevertOf() != null ? cd.change().getRevertOf().get() : null);

  public static final IndexedField<ChangeData, Integer>.SearchSpec REVERT_OF =
      REVERT_OF_FIELD.integer(ChangeQueryBuilder.FIELD_REVERTOF);

  public static final IndexedField<ChangeData, String> IS_PURE_REVERT_FIELD =
      IndexedField.<ChangeData>stringBuilder("IsPureRevert")
          .size(1)
          .build(cd -> Boolean.TRUE.equals(cd.isPureRevert()) ? "1" : "0");

  public static final IndexedField<ChangeData, String>.SearchSpec IS_PURE_REVERT_SPEC =
      IS_PURE_REVERT_FIELD.fullText(ChangeQueryBuilder.FIELD_PURE_REVERT);

  /**
   * Determines if a change is submittable based on {@link
   * com.google.gerrit.entities.SubmitRequirement}s.
   */
  public static final IndexedField<ChangeData, String> IS_SUBMITTABLE_FIELD =
      IndexedField.<ChangeData>stringBuilder("IsSubmittable")
          .size(1)
          .build(
              cd ->
                  // All submit requirements should be fulfilled
                  cd.submitRequirementsIncludingLegacy().values().stream()
                          .allMatch(SubmitRequirementResult::fulfilled)
                      ? "1"
                      : "0");

  public static final IndexedField<ChangeData, String>.SearchSpec IS_SUBMITTABLE_SPEC =
      IS_SUBMITTABLE_FIELD.exact(ChangeQueryBuilder.FIELD_IS_SUBMITTABLE);

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

  /** Commit ID of any patch set on the change, using prefix match. */
  public static final IndexedField<ChangeData, Iterable<String>> COMMIT_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("CommitId")
          .size(40)
          .required()
          .build(ChangeField::getRevisions);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec COMMIT_SPEC =
      COMMIT_FIELD.prefix(ChangeQueryBuilder.FIELD_COMMIT);

  /** Commit ID of any patch set on the change, using exact match. */
  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec EXACT_COMMIT_SPEC =
      COMMIT_FIELD.exact(ChangeQueryBuilder.FIELD_EXACTCOMMIT);

  private static ImmutableSet<String> getRevisions(ChangeData cd) {
    return cd.patchSets().stream().map(ps -> ps.commitId().name()).collect(toImmutableSet());
  }

  /** Tracking id extracted from a footer. */
  public static final IndexedField<ChangeData, Iterable<String>> TR_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("TrackingFooter")
          .build(cd -> ImmutableSet.copyOf(cd.trackingFooters().values()));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec TR_SPEC =
      TR_FIELD.exact(ChangeQueryBuilder.FIELD_TR);

  /** List of labels on the current patch set including change owner votes. */
  public static final IndexedField<ChangeData, Iterable<String>> LABEL_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("Label").required().build(cd -> getLabels(cd));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec LABEL_SPEC =
      LABEL_FIELD.exact("label2");

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
  public static final IndexedField<ChangeData, Iterable<String>> AUTHOR_PARTS_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("AuthorParts")
          .required()
          .description(
              "The exact email address, or any part of the author name or email address, in the current patch set.")
          .build(ChangeField::getAuthorParts);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec AUTHOR_PARTS_SPEC =
      AUTHOR_PARTS_FIELD.fullText(ChangeQueryBuilder.FIELD_AUTHOR);

  /** The exact name, email address and NameEmail of the author. */
  public static final IndexedField<ChangeData, Iterable<String>> EXACT_AUTHOR_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("ExactAuthor")
          .required()
          .description("The exact name, email address and NameEmail of the author.")
          .build(ChangeField::getAuthorNameAndEmail);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec EXACT_AUTHOR_SPEC =
      EXACT_AUTHOR_FIELD.exact(ChangeQueryBuilder.FIELD_EXACTAUTHOR);

  /**
   * The exact email address, or any part of the committer name or email address, in the current
   * patch set.
   */
  public static final IndexedField<ChangeData, Iterable<String>> COMMITTER_PARTS_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("CommitterParts")
          .description(
              "The exact email address, or any part of the committer name or email address, in the current patch set.")
          .required()
          .build(ChangeField::getCommitterParts);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec COMMITTER_PARTS_SPEC =
      COMMITTER_PARTS_FIELD.fullText(ChangeQueryBuilder.FIELD_COMMITTER);

  /** The exact name, email address, and NameEmail of the committer. */
  public static final IndexedField<ChangeData, Iterable<String>> EXACT_COMMITTER_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("ExactCommiter")
          .required()
          .description("The exact name, email address, and NameEmail of the committer.")
          .build(ChangeField::getCommitterNameAndEmail);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec EXACT_COMMITTER_SPEC =
      EXACT_COMMITTER_FIELD.exact(ChangeQueryBuilder.FIELD_EXACTCOMMITTER);

  /** Serialized change object, used for pre-populating results. */
  private static final TypeToken<Entities.Change> CHANGE_TYPE_TOKEN =
      new TypeToken<>() {
        private static final long serialVersionUID = 1L;
      };

  public static final IndexedField<ChangeData, Entities.Change> CHANGE_FIELD =
      IndexedField.<ChangeData, Entities.Change>builder("Change", CHANGE_TYPE_TOKEN)
          .stored()
          .required()
          .protoConverter(Optional.of(ChangeProtoConverter.INSTANCE))
          .build(
              changeGetter(change -> entityToProto(ChangeProtoConverter.INSTANCE, change)),
              (cd, value) ->
                  cd.setChange(decodeProtoToEntity(value, ChangeProtoConverter.INSTANCE)));

  public static final IndexedField<ChangeData, Entities.Change>.SearchSpec CHANGE_SPEC =
      CHANGE_FIELD.storedOnly("_change");

  /** Serialized approvals for the current patch set, used for pre-populating results. */
  private static final TypeToken<Iterable<Entities.PatchSetApproval>> APPROVAL_TYPE_TOKEN =
      new TypeToken<>() {
        private static final long serialVersionUID = 1L;
      };

  public static final IndexedField<ChangeData, Iterable<Entities.PatchSetApproval>> APPROVAL_FIELD =
      IndexedField.<ChangeData, Iterable<Entities.PatchSetApproval>>builder(
              "Approval", APPROVAL_TYPE_TOKEN)
          .stored()
          .required()
          .protoConverter(Optional.of(PatchSetApprovalProtoConverter.INSTANCE))
          .build(
              cd ->
                  entitiesToProtos(PatchSetApprovalProtoConverter.INSTANCE, cd.currentApprovals()),
              (cd, field) ->
                  cd.setCurrentApprovals(
                      decodeProtosToEntities(field, PatchSetApprovalProtoConverter.INSTANCE)));

  public static final IndexedField<ChangeData, Iterable<Entities.PatchSetApproval>>.SearchSpec
      APPROVAL_SPEC = APPROVAL_FIELD.storedOnly("_approval");

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
    return label.toLowerCase(Locale.US)
        + (value >= 0 ? "+" : "")
        + value
        + (accountId != null ? "," + formatAccount(accountId) : "")
        + (count != null ? ",count=" + count : "");
  }

  public static String formatLabel(String label, String value, @Nullable Integer count) {
    return formatLabel(label, value, /* accountId= */ null, count);
  }

  public static String formatLabel(
      String label, String value, @Nullable Account.Id accountId, @Nullable Integer count) {
    return label.toLowerCase(Locale.US)
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

  /** Commit message of the current patch set. */
  public static final IndexedField<ChangeData, String> COMMIT_MESSAGE_FIELD =
      IndexedField.<ChangeData>stringBuilder("CommitMessage")
          .required()
          .build(ChangeData::commitMessage);

  public static final IndexedField<ChangeData, String>.SearchSpec COMMIT_MESSAGE =
      COMMIT_MESSAGE_FIELD.fullText(ChangeQueryBuilder.FIELD_MESSAGE);

  /** Commit message of the current patch set, used to exactly match the commit message */
  public static final IndexedField<ChangeData, String> COMMIT_MESSAGE_EXACT_FIELD =
      IndexedField.<ChangeData>stringBuilder("CommitMessageExact")
          .required()
          .description(
              "Same as CommitMessage, but truncated, since supporting such large tokens may be problematic for indexes.")
          .build(cd -> truncateStringValueToMaxTermLength(cd.commitMessage()));

  public static final IndexedField<ChangeData, String>.SearchSpec COMMIT_MESSAGE_EXACT =
      COMMIT_MESSAGE_EXACT_FIELD.exact(ChangeQueryBuilder.FIELD_MESSAGE_EXACT);

  /** Subject of the current patch set (aka first line of the commit message). */
  public static final IndexedField<ChangeData, String> SUBJECT_FIELD =
      IndexedField.<ChangeData>stringBuilder("Subject")
          .required()
          .build(changeGetter(Change::getSubject));

  public static final IndexedField<ChangeData, String>.SearchSpec SUBJECT_SPEC =
      SUBJECT_FIELD.fullText(ChangeQueryBuilder.FIELD_SUBJECT);

  public static final IndexedField<ChangeData, String>.SearchSpec PREFIX_SUBJECT_SPEC =
      SUBJECT_FIELD.prefix(ChangeQueryBuilder.FIELD_PREFIX_SUBJECT);

  /** Summary or inline comment. */
  public static final IndexedField<ChangeData, Iterable<String>> COMMENT_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("Comment")
          .build(
              cd ->
                  Stream.concat(
                          cd.publishedComments().stream().map(c -> c.message),
                          // Some endpoint allow passing user message in input, and we still want to
                          // search by that. Index on message template with placeholders for user
                          // data, so we don't
                          // persist user identifiable information data in index.
                          cd.messages().stream().map(ChangeMessage::getMessage))
                      .collect(toSet()));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec COMMENT_SPEC =
      COMMENT_FIELD.fullText(ChangeQueryBuilder.FIELD_COMMENT);

  /** Number of unresolved comment threads of the change, including robot comments. */
  public static final IndexedField<ChangeData, Integer> UNRESOLVED_COMMENT_COUNT_FIELD =
      IndexedField.<ChangeData>integerBuilder("UnresolvedCommentCount")
          .stored()
          .build(
              ChangeData::unresolvedCommentCount,
              (cd, field) -> cd.setUnresolvedCommentCount(field));

  public static final IndexedField<ChangeData, Integer>.SearchSpec UNRESOLVED_COMMENT_COUNT_SPEC =
      UNRESOLVED_COMMENT_COUNT_FIELD.integerRange(
          ChangeQueryBuilder.FIELD_UNRESOLVED_COMMENT_COUNT);

  /** Total number of published inline comments of the change, including robot comments. */
  public static final IndexedField<ChangeData, Integer> TOTAL_COMMENT_COUNT_FIELD =
      IndexedField.<ChangeData>integerBuilder("TotalCommentCount")
          .stored()
          .build(ChangeData::totalCommentCount, (cd, field) -> cd.setTotalCommentCount(field));

  public static final IndexedField<ChangeData, Integer>.SearchSpec TOTAL_COMMENT_COUNT_SPEC =
      TOTAL_COMMENT_COUNT_FIELD.integerRange("total_comments");

  /** Whether the change is mergeable. */
  public static final IndexedField<ChangeData, String> MERGEABLE_FIELD =
      IndexedField.<ChangeData>stringBuilder("Mergeable")
          .stored()
          .size(1)
          .build(
              cd -> {
                Boolean m = cd.isMergeable();
                if (m == null) {
                  return null;
                }
                return m ? "1" : "0";
              },
              (cd, field) -> cd.setMergeable(field == null ? false : field.equals("1")));

  public static final IndexedField<ChangeData, String>.SearchSpec MERGEABLE_SPEC =
      MERGEABLE_FIELD.exact(ChangeQueryBuilder.FIELD_MERGEABLE);

  /** Whether the change is a merge commit. */
  public static final IndexedField<ChangeData, String> MERGE_FIELD =
      IndexedField.<ChangeData>stringBuilder("Merge")
          .stored()
          .size(1)
          .build(
              cd -> {
                Boolean m = cd.isMerge();
                if (m == null) {
                  return null;
                }
                return m ? "1" : "0";
              });

  public static final IndexedField<ChangeData, String>.SearchSpec MERGE_SPEC =
      MERGE_FIELD.exact(ChangeQueryBuilder.FIELD_MERGE);

  /** Whether the change is a cherry pick of another change. */
  public static final IndexedField<ChangeData, String> CHERRY_PICK_FIELD =
      IndexedField.<ChangeData>stringBuilder("CherryPick")
          .stored()
          .size(1)
          .build(cd -> cd.change().getCherryPickOf() != null ? "1" : "0");

  public static final IndexedField<ChangeData, String>.SearchSpec CHERRY_PICK_SPEC =
      CHERRY_PICK_FIELD.exact(ChangeQueryBuilder.FIELD_CHERRYPICK);

  /** The number of inserted lines in this change. */
  public static final IndexedField<ChangeData, Integer> ADDED_LINES_FIELD =
      IndexedField.<ChangeData>integerBuilder("AddedLines")
          .stored()
          .build(
              cd -> cd.changedLines().isPresent() ? cd.changedLines().get().insertions : null,
              (cd, field) -> {
                if (field != null) {
                  cd.setLinesInserted(field);
                }
              });

  public static final IndexedField<ChangeData, Integer>.SearchSpec ADDED_LINES_SPEC =
      ADDED_LINES_FIELD.integerRange(ChangeQueryBuilder.FIELD_ADDED);

  /** The number of deleted lines in this change. */
  public static final IndexedField<ChangeData, Integer> DELETED_LINES_FIELD =
      IndexedField.<ChangeData>integerBuilder("DeletedLines")
          .stored()
          .build(
              cd -> cd.changedLines().isPresent() ? cd.changedLines().get().deletions : null,
              (cd, field) -> {
                if (field != null) {
                  cd.setLinesDeleted(field);
                }
              });

  public static final IndexedField<ChangeData, Integer>.SearchSpec DELETED_LINES_SPEC =
      DELETED_LINES_FIELD.integerRange(ChangeQueryBuilder.FIELD_DELETED);

  /** The total number of modified lines in this change. */
  public static final IndexedField<ChangeData, Integer> DELTA_LINES_FIELD =
      IndexedField.<ChangeData>integerBuilder("DeltaLines")
          .stored()
          .build(cd -> cd.changedLines().map(c -> c.insertions + c.deletions).orElse(null));

  public static final IndexedField<ChangeData, Integer>.SearchSpec DELTA_LINES_SPEC =
      DELTA_LINES_FIELD.integerRange(ChangeQueryBuilder.FIELD_DELTA);

  /** Determines if this change is private. */
  public static final IndexedField<ChangeData, String> PRIVATE_FIELD =
      IndexedField.<ChangeData>stringBuilder("IsPrivate")
          .size(1)
          .build(cd -> cd.change().isPrivate() ? "1" : "0");

  public static final IndexedField<ChangeData, String>.SearchSpec PRIVATE_SPEC =
      PRIVATE_FIELD.exact(ChangeQueryBuilder.FIELD_PRIVATE);

  /** Determines if this change is work in progress. */
  public static final IndexedField<ChangeData, String> WIP_FIELD =
      IndexedField.<ChangeData>stringBuilder("WIP")
          .size(1)
          .build(cd -> cd.change().isWorkInProgress() ? "1" : "0");

  public static final IndexedField<ChangeData, String>.SearchSpec WIP_SPEC =
      WIP_FIELD.exact(ChangeQueryBuilder.FIELD_WIP);

  /** Determines if this change has started review. */
  public static final IndexedField<ChangeData, String> STARTED_FIELD =
      IndexedField.<ChangeData>stringBuilder("ReviewStarted")
          .size(1)
          .build(cd -> cd.change().hasReviewStarted() ? "1" : "0");

  public static final IndexedField<ChangeData, String>.SearchSpec STARTED_SPEC =
      STARTED_FIELD.exact(ChangeQueryBuilder.FIELD_STARTED);

  /** Users who have commented on this change. */
  public static final IndexedField<ChangeData, Iterable<Integer>> COMMENTBY_FIELD =
      IndexedField.<ChangeData>iterableIntegerBuilder("CommentBy")
          .build(
              cd ->
                  Stream.concat(
                          cd.messages().stream().map(ChangeMessage::getAuthor),
                          cd.publishedComments().stream().map(c -> c.author.getId()))
                      .filter(Objects::nonNull)
                      .map(Account.Id::get)
                      .collect(toSet()));

  public static final IndexedField<ChangeData, Iterable<Integer>>.SearchSpec COMMENTBY_SPEC =
      COMMENTBY_FIELD.integer(ChangeQueryBuilder.FIELD_COMMENTBY);

  /** Star labels on this change in the format: &lt;account-id&gt;:&lt;label&gt; */
  public static final IndexedField<ChangeData, Iterable<String>> STAR_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("Star")
          .stored()
          .build(
              cd ->
                  Iterables.transform(
                      cd.stars().entries(),
                      e ->
                          StarredChangesUtil.StarField.create(e.getKey(), e.getValue()).toString()),
              (cd, field) ->
                  cd.setStars(
                      StreamSupport.stream(field.spliterator(), false)
                          .map(f -> StarredChangesUtil.StarField.parse(f))
                          .collect(toImmutableListMultimap(e -> e.accountId(), e -> e.label()))));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec STAR_SPEC =
      STAR_FIELD.exact(ChangeQueryBuilder.FIELD_STAR);

  /** Users that have starred the change with any label. */
  public static final IndexedField<ChangeData, Iterable<Integer>> STARBY_FIELD =
      IndexedField.<ChangeData>iterableIntegerBuilder("StarBy")
          .build(cd -> Iterables.transform(cd.stars().keySet(), Account.Id::get));

  public static final IndexedField<ChangeData, Iterable<Integer>>.SearchSpec STARBY_SPEC =
      STARBY_FIELD.integer(ChangeQueryBuilder.FIELD_STARBY);

  /** Opaque group identifiers for this change's patch sets. */
  public static final IndexedField<ChangeData, Iterable<String>> GROUP_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("Group")
          .build(
              cd -> cd.patchSets().stream().flatMap(ps -> ps.groups().stream()).collect(toSet()));

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec GROUP_SPEC =
      GROUP_FIELD.exact(ChangeQueryBuilder.FIELD_GROUP);

  /** Serialized patch set object, used for pre-populating results. */
  private static final TypeToken<Iterable<Entities.PatchSet>> PATCH_SET_TYPE_TOKEN =
      new TypeToken<>() {
        private static final long serialVersionUID = 1L;
      };

  public static final IndexedField<ChangeData, Iterable<Entities.PatchSet>> PATCH_SET_FIELD =
      IndexedField.<ChangeData, Iterable<Entities.PatchSet>>builder(
              "PatchSet", PATCH_SET_TYPE_TOKEN)
          .stored()
          .required()
          .protoConverter(Optional.of(PatchSetProtoConverter.INSTANCE))
          .build(
              cd -> entitiesToProtos(PatchSetProtoConverter.INSTANCE, cd.patchSets()),
              (cd, value) ->
                  cd.setPatchSets(decodeProtosToEntities(value, PatchSetProtoConverter.INSTANCE)));

  public static final IndexedField<ChangeData, Iterable<Entities.PatchSet>>.SearchSpec
      PATCH_SET_SPEC = PATCH_SET_FIELD.storedOnly("_patch_set");

  /** Users who have edits on this change. */
  public static final IndexedField<ChangeData, Iterable<Integer>> EDITBY_FIELD =
      IndexedField.<ChangeData>iterableIntegerBuilder("EditBy")
          .build(cd -> cd.editsByUser().stream().map(Account.Id::get).collect(toSet()));

  public static final IndexedField<ChangeData, Iterable<Integer>>.SearchSpec EDITBY_SPEC =
      EDITBY_FIELD.integer(ChangeQueryBuilder.FIELD_EDITBY);

  /** Users who have draft comments on this change. */
  public static final IndexedField<ChangeData, Iterable<Integer>> DRAFTBY_FIELD =
      IndexedField.<ChangeData>iterableIntegerBuilder("DraftBy")
          .build(cd -> cd.draftsByUser().stream().map(Account.Id::get).collect(toSet()));

  public static final IndexedField<ChangeData, Iterable<Integer>>.SearchSpec DRAFTBY_SPEC =
      DRAFTBY_FIELD.integer(ChangeQueryBuilder.FIELD_DRAFTBY);

  public static final Integer NOT_REVIEWED = -1;

  /**
   * Users the change was reviewed by since the last author update.
   *
   * <p>A change is considered reviewed by a user if the latest update by that user is newer than
   * the latest update by the change author. Both top-level change messages and new patch sets are
   * considered to be updates.
   *
   * <p>If the latest update is by the change owner, then the special value {@link #NOT_REVIEWED} is
   * emitted.
   */
  public static final IndexedField<ChangeData, Iterable<Integer>> REVIEWEDBY_FIELD =
      IndexedField.<ChangeData>iterableIntegerBuilder("ReviewedBy")
          .stored()
          .build(
              cd -> {
                Set<Account.Id> reviewedBy = cd.reviewedBy();
                if (reviewedBy.isEmpty()) {
                  return ImmutableSet.of(NOT_REVIEWED);
                }
                return reviewedBy.stream().map(Account.Id::get).collect(toList());
              },
              (cd, field) ->
                  cd.setReviewedBy(
                      StreamSupport.stream(field.spliterator(), false)
                          .map(Account::id)
                          .collect(toImmutableSet())));

  public static final IndexedField<ChangeData, Iterable<Integer>>.SearchSpec REVIEWEDBY_SPEC =
      REVIEWEDBY_FIELD.integer(ChangeQueryBuilder.FIELD_REVIEWEDBY);

  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_LENIENT =
      SubmitRuleOptions.builder().recomputeOnClosedChanges(true).build();

  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_STRICT =
      SubmitRuleOptions.builder().build();

  /** All submit rules results in the form of "$ruleName,$status". */
  public static final IndexedField<ChangeData, Iterable<String>> SUBMIT_RULE_RESULT_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("SubmitRuleResult")
          .build(
              cd -> {
                List<String> result = new ArrayList<>();
                List<SubmitRecord> submitRecords = cd.submitRecords(SUBMIT_RULE_OPTIONS_STRICT);
                for (SubmitRecord record : submitRecords) {
                  result.add(record.ruleName + "=" + record.status.name());
                }
                return result;
              });

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec
      SUBMIT_RULE_RESULT_SPEC = SUBMIT_RULE_RESULT_FIELD.exact("submit_rule_result");

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

  public static final IndexedField<ChangeData, Iterable<String>> SUBMIT_RECORD_FIELD =
      IndexedField.<ChangeData>iterableStringBuilder("SubmitRecord")
          .build(ChangeField::formatSubmitRecordValues);

  public static final IndexedField<ChangeData, Iterable<String>>.SearchSpec SUBMIT_RECORD_SPEC =
      SUBMIT_RECORD_FIELD.exact("submit_record");

  public static final IndexedField<ChangeData, Iterable<byte[]>> STORED_SUBMIT_RECORD_STRICT_FIELD =
      IndexedField.<ChangeData>iterableByteArrayBuilder("FullSubmitRecordStrict")
          .stored()
          .build(
              cd -> storedSubmitRecords(cd, SUBMIT_RULE_OPTIONS_STRICT),
              (cd, field) ->
                  parseSubmitRecords(
                      StreamSupport.stream(field.spliterator(), false)
                          .map(f -> new String(f, UTF_8))
                          .collect(toSet()),
                      SUBMIT_RULE_OPTIONS_STRICT,
                      cd));

  public static final IndexedField<ChangeData, Iterable<byte[]>>.SearchSpec
      STORED_SUBMIT_RECORD_STRICT_SPEC =
          STORED_SUBMIT_RECORD_STRICT_FIELD.storedOnly("full_submit_record_strict");

  public static final IndexedField<ChangeData, Iterable<byte[]>>
      STORED_SUBMIT_RECORD_LENIENT_FIELD =
          IndexedField.<ChangeData>iterableByteArrayBuilder("FullSubmitRecordLenient")
              .stored()
              .build(
                  cd -> storedSubmitRecords(cd, SUBMIT_RULE_OPTIONS_LENIENT),
                  (cd, field) ->
                      parseSubmitRecords(
                          StreamSupport.stream(field.spliterator(), false)
                              .map(f -> new String(f, UTF_8))
                              .collect(toSet()),
                          SUBMIT_RULE_OPTIONS_LENIENT,
                          cd));

  public static final IndexedField<ChangeData, Iterable<byte[]>>.SearchSpec
      STORED_SUBMIT_RECORD_LENIENT_SPEC =
          STORED_SUBMIT_RECORD_LENIENT_FIELD.storedOnly("full_submit_record_lenient");

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
        String sl = label.status.toString() + ',' + label.label.toLowerCase(Locale.US);
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
                  + srResult.submitRequirement().name().toLowerCase(Locale.US));
          result.add(
              SubmitRecord.Label.Status.MAY.name()
                  + ","
                  + srResult.submitRequirement().name().toLowerCase(Locale.US));
          break;
        case UNSATISFIED:
          result.add(
              SubmitRecord.Label.Status.NEED.name()
                  + ","
                  + srResult.submitRequirement().name().toLowerCase(Locale.US));
          result.add(
              SubmitRecord.Label.Status.REJECT.name()
                  + ","
                  + srResult.submitRequirement().name().toLowerCase(Locale.US));
          break;
        case NOT_APPLICABLE:
        case ERROR:
          result.add(
              SubmitRecord.Label.Status.IMPOSSIBLE.name()
                  + ","
                  + srResult.submitRequirement().name().toLowerCase(Locale.US));
      }
    }
    return result;
  }

  /** Serialized submit requirements, used for pre-populating results. */
  private static final TypeToken<Iterable<Cache.SubmitRequirementResultProto>>
      STORED_SUBMIT_REQUIREMENTS_TYPE_TOKEN =
          new TypeToken<>() {
            private static final long serialVersionUID = 1L;
          };

  public static final IndexedField<ChangeData, Iterable<Cache.SubmitRequirementResultProto>>
      STORED_SUBMIT_REQUIREMENTS_FIELD =
          IndexedField.<ChangeData, Iterable<Cache.SubmitRequirementResultProto>>builder(
                  "StoredSubmitRequirements", STORED_SUBMIT_REQUIREMENTS_TYPE_TOKEN)
              .stored()
              .required()
              .protoConverter(Optional.of(SubmitRequirementProtoConverter.INSTANCE))
              .build(
                  cd ->
                      entitiesToProtos(
                          SubmitRequirementProtoConverter.INSTANCE,
                          cd.submitRequirements().values()),
                  (cd, value) -> parseSubmitRequirements(value, cd));

  public static final IndexedField<ChangeData, Iterable<Cache.SubmitRequirementResultProto>>
          .SearchSpec
      STORED_SUBMIT_REQUIREMENTS_SPEC =
          STORED_SUBMIT_REQUIREMENTS_FIELD.storedOnly("full_submit_requirements");

  private static void parseSubmitRequirements(
      Iterable<Cache.SubmitRequirementResultProto> values, ChangeData out) {
    out.setSubmitRequirements(
        decodeProtosToEntities(values, SubmitRequirementProtoConverter.INSTANCE).stream()
            .filter(sr -> !sr.isLegacy())
            .collect(
                ImmutableMap.toImmutableMap(sr -> sr.submitRequirement(), Function.identity())));
  }

  /**
   * All values of all refs that were used in the course of indexing this document.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name:[hex sha]}.
   */
  public static final IndexedField<ChangeData, Iterable<byte[]>> REF_STATE_FIELD =
      IndexedField.<ChangeData>iterableByteArrayBuilder("RefState")
          .stored()
          .build(
              cd -> {
                List<byte[]> result = new ArrayList<>();
                cd.getRefStates()
                    .entries()
                    .forEach(e -> result.add(e.getValue().toByteArray(e.getKey())));
                return result;
              },
              (cd, field) -> cd.setRefStates(RefState.parseStates(field)));

  public static final IndexedField<ChangeData, Iterable<byte[]>>.SearchSpec REF_STATE_SPEC =
      REF_STATE_FIELD.storedOnly("ref_state");

  /**
   * All ref wildcard patterns that were used in the course of indexing this document.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name/*}. See {@link
   * RefStatePattern} for the pattern format.
   */
  public static final IndexedField<ChangeData, Iterable<byte[]>> REF_STATE_PATTERN_FIELD =
      IndexedField.<ChangeData>iterableByteArrayBuilder("RefStatePattern")
          .stored()
          .build(
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

  public static final IndexedField<ChangeData, Iterable<byte[]>>.SearchSpec REF_STATE_PATTERN_SPEC =
      REF_STATE_PATTERN_FIELD.storedOnly("ref_state_pattern");

  @Nullable
  private static String getTopic(ChangeData cd) {
    Change c = cd.change();
    if (c == null) {
      return null;
    }
    return firstNonNull(c.getTopic(), "");
  }

  private static <V extends MessageLite, T> V entityToProto(
      ProtoConverter<V, T> converter, T object) {
    return converter.toProto(object);
  }

  private static <V extends MessageLite, T> List<V> entitiesToProtos(
      ProtoConverter<V, T> converter, Collection<T> objects) {
    return objects.stream()
        .map(object -> entityToProto(converter, object))
        .collect(toImmutableList());
  }

  private static <V extends MessageLite, T> List<T> decodeProtosToEntities(
      Iterable<V> raw, ProtoConverter<V, T> converter) {
    return StreamSupport.stream(raw.spliterator(), false)
        .map(proto -> decodeProtoToEntity(proto, converter))
        .collect(toImmutableList());
  }

  private static <V extends MessageLite, T> T decodeProtoToEntity(
      V proto, ProtoConverter<V, T> converter) {
    return converter.fromProto(proto);
  }

  private static <T> SchemaFieldDefs.Getter<ChangeData, T> changeGetter(Function<Change, T> func) {
    return in -> in.change() != null ? func.apply(in.change()) : null;
  }

  private static AllUsersName allUsers(ChangeData cd) {
    return cd.getAllUsersNameForIndexing();
  }

  private static String truncateStringValueToMaxTermLength(String str) {
    return truncateStringValue(str, MAX_TERM_LENGTH);
  }

  @VisibleForTesting
  static String truncateStringValue(String str, int maxBytes) {
    if (maxBytes < 0) {
      throw new IllegalArgumentException("maxBytes < 0 not allowed");
    }

    if (maxBytes == 0) {
      return "";
    }

    if (str.length() > maxBytes) {
      if (Character.isHighSurrogate(str.charAt(maxBytes - 1))) {
        str = str.substring(0, maxBytes - 1);
      } else {
        str = str.substring(0, maxBytes);
      }
    }
    byte[] strBytes = str.getBytes(UTF_8);
    if (strBytes.length > maxBytes) {
      while (maxBytes > 0 && (strBytes[maxBytes] & 0xC0) == 0x80) {
        maxBytes -= 1;
      }
      if (maxBytes > 0) {
        if (strBytes.length >= maxBytes && (strBytes[maxBytes - 1] & 0xE0) == 0xC0) {
          maxBytes -= 1;
        }
        if (strBytes.length >= maxBytes && (strBytes[maxBytes - 1] & 0xF0) == 0xE0) {
          maxBytes -= 1;
        }
        if (strBytes.length >= maxBytes && (strBytes[maxBytes - 1] & 0xF8) == 0xF0) {
          maxBytes -= 1;
        }
      }
      return new String(Arrays.copyOfRange(strBytes, 0, maxBytes), UTF_8);
    }
    return str;
  }
}
