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
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.index.FieldDef.exact;
import static com.google.gerrit.index.FieldDef.fullText;
import static com.google.gerrit.index.FieldDef.intRange;
import static com.google.gerrit.index.FieldDef.integer;
import static com.google.gerrit.index.FieldDef.prefix;
import static com.google.gerrit.index.FieldDef.storedOnly;
import static com.google.gerrit.index.FieldDef.timestamp;
import static com.google.gerrit.server.util.AttentionSetUtil.additionsOnly;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.converter.ChangeProtoConverter;
import com.google.gerrit.entities.converter.PatchSetApprovalProtoConverter;
import com.google.gerrit.entities.converter.PatchSetProtoConverter;
import com.google.gerrit.entities.converter.ProtoConverter;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.RefState;
import com.google.gerrit.index.SchemaUtil;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.index.change.StalenessChecker.RefStatePattern;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.notedb.RobotCommentNotes;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gson.Gson;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
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

  /** Legacy change ID. */
  public static final FieldDef<ChangeData, Integer> LEGACY_ID =
      integer("legacy_id").stored().build(cd -> cd.getId().get());

  public static final FieldDef<ChangeData, String> LEGACY_ID_STR =
      exact("legacy_id_str").stored().build(cd -> String.valueOf(cd.getId().get()));

  /** Newer style Change-Id key. */
  public static final FieldDef<ChangeData, String> ID =
      prefix(ChangeQueryBuilder.FIELD_CHANGE_ID).build(changeGetter(c -> c.getKey().get()));

  /** Change status string, in the same format as {@code status:}. */
  public static final FieldDef<ChangeData, String> STATUS =
      exact(ChangeQueryBuilder.FIELD_STATUS)
          .build(changeGetter(c -> ChangeStatusPredicate.canonicalize(c.getStatus())));

  /** Project containing the change. */
  public static final FieldDef<ChangeData, String> PROJECT =
      exact(ChangeQueryBuilder.FIELD_PROJECT)
          .stored()
          .build(changeGetter(c -> c.getProject().get()));

  /** Project containing the change, as a prefix field. */
  public static final FieldDef<ChangeData, String> PROJECTS =
      prefix(ChangeQueryBuilder.FIELD_PROJECTS).build(changeGetter(c -> c.getProject().get()));

  /** Reference (aka branch) the change will submit onto. */
  public static final FieldDef<ChangeData, String> REF =
      exact(ChangeQueryBuilder.FIELD_REF).build(changeGetter(c -> c.getDest().branch()));

  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> EXACT_TOPIC =
      exact("topic4").build(ChangeField::getTopic);

  /** Topic, a short annotation on the branch. */
  public static final FieldDef<ChangeData, String> FUZZY_TOPIC =
      fullText("topic5").build(ChangeField::getTopic);

  /** Submission id assigned by MergeOp. */
  public static final FieldDef<ChangeData, String> SUBMISSIONID =
      exact(ChangeQueryBuilder.FIELD_SUBMISSIONID).build(changeGetter(Change::getSubmissionId));

  /** Last update time since January 1, 1970. */
  public static final FieldDef<ChangeData, Timestamp> UPDATED =
      timestamp("updated2").stored().build(changeGetter(Change::getLastUpdatedOn));

  /** List of full file paths modified in the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> PATH =
      // Named for backwards compatibility.
      exact(ChangeQueryBuilder.FIELD_FILE)
          .buildRepeatable(cd -> firstNonNull(cd.currentFilePaths(), ImmutableList.of()));

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
  public static final FieldDef<ChangeData, Iterable<String>> HASHTAG =
      exact(ChangeQueryBuilder.FIELD_HASHTAG)
          .buildRepeatable(cd -> cd.hashtags().stream().map(String::toLowerCase).collect(toSet()));

  /** Hashtags with original case. */
  public static final FieldDef<ChangeData, Iterable<byte[]>> HASHTAG_CASE_AWARE =
      storedOnly("_hashtag")
          .buildRepeatable(
              cd -> cd.hashtags().stream().map(t -> t.getBytes(UTF_8)).collect(toSet()));

  /** Components of each file path modified in the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> FILE_PART =
      exact(ChangeQueryBuilder.FIELD_FILEPART).buildRepeatable(ChangeField::getFileParts);

  /** File extensions of each file modified in the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> EXTENSION =
      exact(ChangeQueryBuilder.FIELD_EXTENSION).buildRepeatable(ChangeField::getExtensions);

  public static Set<String> getExtensions(ChangeData cd) {
    return extensions(cd).collect(toSet());
  }

  /**
   * File extensions of each file modified in the current patch set as a sorted list. The purpose of
   * this field is to allow matching changes that only touch files with certain file extensions.
   */
  public static final FieldDef<ChangeData, String> ONLY_EXTENSIONS =
      exact(ChangeQueryBuilder.FIELD_ONLY_EXTENSIONS).build(ChangeField::getAllExtensionsAsList);

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
  public static final FieldDef<ChangeData, Iterable<String>> FOOTER =
      exact(ChangeQueryBuilder.FIELD_FOOTER).buildRepeatable(ChangeField::getFooters);

  public static Set<String> getFooters(ChangeData cd) {
    return cd.commitFooters().stream()
        .map(f -> f.toString().toLowerCase(Locale.US))
        .collect(toSet());
  }

  /** Folders that are touched by the current patch set. */
  public static final FieldDef<ChangeData, Iterable<String>> DIRECTORY =
      exact(ChangeQueryBuilder.FIELD_DIRECTORY).buildRepeatable(ChangeField::getDirectories);

  public static Set<String> getDirectories(ChangeData cd) {
    List<String> paths = cd.currentFilePaths();

    Splitter s = Splitter.on('/').omitEmptyStrings();
    Set<String> r = new HashSet<>();
    for (String path : paths) {
      StringBuilder directory = new StringBuilder();
      r.add(directory.toString());
      String nextPart = null;
      for (String part : s.split(path)) {
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
  public static final FieldDef<ChangeData, Integer> OWNER =
      integer(ChangeQueryBuilder.FIELD_OWNER).build(changeGetter(c -> c.getOwner().get()));

  /** References the source change number that this change was cherry-picked from. */
  public static final FieldDef<ChangeData, Integer> CHERRY_PICK_OF_CHANGE =
      integer(ChangeQueryBuilder.FIELD_CHERRY_PICK_OF_CHANGE)
          .build(
              cd ->
                  cd.change().getCherryPickOf() != null
                      ? cd.change().getCherryPickOf().changeId().get()
                      : null);

  /** References the source change patch-set that this change was cherry-picked from. */
  public static final FieldDef<ChangeData, Integer> CHERRY_PICK_OF_PATCHSET =
      integer(ChangeQueryBuilder.FIELD_CHERRY_PICK_OF_PATCHSET)
          .build(
              cd ->
                  cd.change().getCherryPickOf() != null
                      ? cd.change().getCherryPickOf().get()
                      : null);

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
   * @see #ATTENTION_SET_FULL
   */
  public static final FieldDef<ChangeData, Iterable<Integer>> ATTENTION_SET_USERS =
      integer(ChangeQueryBuilder.FIELD_ATTENTION_SET_USERS)
          .buildRepeatable(ChangeField::getAttentionSetUserIds);

  /**
   * The full attention set data including timestamp, reason and possible future fields.
   *
   * @see #ATTENTION_SET_USERS
   */
  public static final FieldDef<ChangeData, Iterable<byte[]>> ATTENTION_SET_FULL =
      storedOnly(ChangeQueryBuilder.FIELD_ATTENTION_SET_FULL)
          .buildRepeatable(ChangeField::storedAttentionSet);

  /** The user assigned to the change. */
  public static final FieldDef<ChangeData, Integer> ASSIGNEE =
      integer(ChangeQueryBuilder.FIELD_ASSIGNEE)
          .build(changeGetter(c -> c.getAssignee() != null ? c.getAssignee().get() : NO_ASSIGNEE));

  /** Reviewer(s) associated with the change. */
  public static final FieldDef<ChangeData, Iterable<String>> REVIEWER =
      exact("reviewer2").stored().buildRepeatable(cd -> getReviewerFieldValues(cd.reviewers()));

  /** Reviewer(s) associated with the change that do not have a gerrit account. */
  public static final FieldDef<ChangeData, Iterable<String>> REVIEWER_BY_EMAIL =
      exact("reviewer_by_email")
          .stored()
          .buildRepeatable(cd -> getReviewerByEmailFieldValues(cd.reviewersByEmail()));

  /** Reviewer(s) modified during change's current WIP phase. */
  public static final FieldDef<ChangeData, Iterable<String>> PENDING_REVIEWER =
      exact(ChangeQueryBuilder.FIELD_PENDING_REVIEWER)
          .stored()
          .buildRepeatable(cd -> getReviewerFieldValues(cd.pendingReviewers()));

  /** Reviewer(s) by email modified during change's current WIP phase. */
  public static final FieldDef<ChangeData, Iterable<String>> PENDING_REVIEWER_BY_EMAIL =
      exact(ChangeQueryBuilder.FIELD_PENDING_REVIEWER_BY_EMAIL)
          .stored()
          .buildRepeatable(cd -> getReviewerByEmailFieldValues(cd.pendingReviewersByEmail()));

  /** References a change that this change reverts. */
  public static final FieldDef<ChangeData, Integer> REVERT_OF =
      integer(ChangeQueryBuilder.FIELD_REVERTOF)
          .build(cd -> cd.change().getRevertOf() != null ? cd.change().getRevertOf().get() : null);

  @VisibleForTesting
  static List<String> getReviewerFieldValues(ReviewerSet reviewers) {
    List<String> r = new ArrayList<>(reviewers.asTable().size() * 2);
    for (Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> c :
        reviewers.asTable().cellSet()) {
      String v = getReviewerFieldValue(c.getRowKey(), c.getColumnKey());
      r.add(v);
      r.add(v + ',' + c.getValue().getTime());
    }
    return r;
  }

  public static String getReviewerFieldValue(ReviewerStateInternal state, Account.Id id) {
    return state.toString() + ',' + id;
  }

  @VisibleForTesting
  static List<String> getReviewerByEmailFieldValues(ReviewerByEmailSet reviewersByEmail) {
    List<String> r = new ArrayList<>(reviewersByEmail.asTable().size() * 2);
    for (Table.Cell<ReviewerStateInternal, Address, Timestamp> c :
        reviewersByEmail.asTable().cellSet()) {
      String v = getReviewerByEmailFieldValue(c.getRowKey(), c.getColumnKey());
      r.add(v);
      if (c.getColumnKey().name() != null) {
        // Add another entry without the name to provide search functionality on the email
        Address emailOnly = Address.create(c.getColumnKey().email());
        r.add(getReviewerByEmailFieldValue(c.getRowKey(), emailOnly));
      }
      r.add(v + ',' + c.getValue().getTime());
    }
    return r;
  }

  public static String getReviewerByEmailFieldValue(ReviewerStateInternal state, Address adr) {
    return state.toString() + ',' + adr;
  }

  public static ReviewerSet parseReviewerFieldValues(Change.Id changeId, Iterable<String> values) {
    ImmutableTable.Builder<ReviewerStateInternal, Account.Id, Timestamp> b =
        ImmutableTable.builder();
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
      Timestamp timestamp = new Timestamp(l);

      b.put(reviewerState.get(), accountId.get(), timestamp);
    }
    return ReviewerSet.fromTable(b.build());
  }

  public static ReviewerByEmailSet parseReviewerByEmailFieldValues(
      Change.Id changeId, Iterable<String> values) {
    ImmutableTable.Builder<ReviewerStateInternal, Address, Timestamp> b = ImmutableTable.builder();
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
      Timestamp timestamp = new Timestamp(l);

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
  public static final FieldDef<ChangeData, Iterable<String>> COMMIT =
      prefix(ChangeQueryBuilder.FIELD_COMMIT).buildRepeatable(ChangeField::getRevisions);

  /** Commit ID of any patch set on the change, using exact match. */
  public static final FieldDef<ChangeData, Iterable<String>> EXACT_COMMIT =
      exact(ChangeQueryBuilder.FIELD_EXACTCOMMIT).buildRepeatable(ChangeField::getRevisions);

  private static ImmutableSet<String> getRevisions(ChangeData cd) {
    return cd.patchSets().stream().map(ps -> ps.commitId().name()).collect(toImmutableSet());
  }

  /** Tracking id extracted from a footer. */
  public static final FieldDef<ChangeData, Iterable<String>> TR =
      exact(ChangeQueryBuilder.FIELD_TR)
          .buildRepeatable(cd -> ImmutableSet.copyOf(cd.trackingFooters().values()));

  /** List of labels on the current patch set including change owner votes. */
  public static final FieldDef<ChangeData, Iterable<String>> LABEL =
      exact("label2").buildRepeatable(cd -> getLabels(cd, true));

  private static Iterable<String> getLabels(ChangeData cd, boolean owners) {
    Set<String> allApprovals = new HashSet<>();
    Set<String> distinctApprovals = new HashSet<>();
    for (PatchSetApproval a : cd.currentApprovals()) {
      if (a.value() != 0 && !a.isLegacySubmit()) {
        allApprovals.add(formatLabel(a.label(), a.value(), a.accountId()));
        if (owners && cd.change().getOwner().equals(a.accountId())) {
          allApprovals.add(formatLabel(a.label(), a.value(), ChangeQueryBuilder.OWNER_ACCOUNT_ID));
        }
        distinctApprovals.add(formatLabel(a.label(), a.value()));
      }
    }
    allApprovals.addAll(distinctApprovals);
    return allApprovals;
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
  public static final FieldDef<ChangeData, Iterable<String>> AUTHOR =
      fullText(ChangeQueryBuilder.FIELD_AUTHOR).buildRepeatable(ChangeField::getAuthorParts);

  /** The exact name, email address and NameEmail of the author. */
  public static final FieldDef<ChangeData, Iterable<String>> EXACT_AUTHOR =
      exact(ChangeQueryBuilder.FIELD_EXACTAUTHOR)
          .buildRepeatable(ChangeField::getAuthorNameAndEmail);

  /**
   * The exact email address, or any part of the committer name or email address, in the current
   * patch set.
   */
  public static final FieldDef<ChangeData, Iterable<String>> COMMITTER =
      fullText(ChangeQueryBuilder.FIELD_COMMITTER).buildRepeatable(ChangeField::getCommitterParts);

  /** The exact name, email address, and NameEmail of the committer. */
  public static final FieldDef<ChangeData, Iterable<String>> EXACT_COMMITTER =
      exact(ChangeQueryBuilder.FIELD_EXACTCOMMITTER)
          .buildRepeatable(ChangeField::getCommitterNameAndEmail);

  /** Serialized change object, used for pre-populating results. */
  public static final FieldDef<ChangeData, byte[]> CHANGE =
      storedOnly("_change")
          .build(changeGetter(change -> toProto(ChangeProtoConverter.INSTANCE, change)));

  /** Serialized approvals for the current patch set, used for pre-populating results. */
  public static final FieldDef<ChangeData, Iterable<byte[]>> APPROVAL =
      storedOnly("_approval")
          .buildRepeatable(
              cd -> toProtos(PatchSetApprovalProtoConverter.INSTANCE, cd.currentApprovals()));

  public static String formatLabel(String label, int value) {
    return formatLabel(label, value, null);
  }

  public static String formatLabel(String label, int value, Account.Id accountId) {
    return label.toLowerCase()
        + (value >= 0 ? "+" : "")
        + value
        + (accountId != null ? "," + formatAccount(accountId) : "");
  }

  private static String formatAccount(Account.Id accountId) {
    if (ChangeQueryBuilder.OWNER_ACCOUNT_ID.equals(accountId)) {
      return ChangeQueryBuilder.ARG_ID_OWNER;
    }
    return Integer.toString(accountId.get());
  }

  /** Commit message of the current patch set. */
  public static final FieldDef<ChangeData, String> COMMIT_MESSAGE =
      fullText(ChangeQueryBuilder.FIELD_MESSAGE).build(ChangeData::commitMessage);

  /** Summary or inline comment. */
  public static final FieldDef<ChangeData, Iterable<String>> COMMENT =
      fullText(ChangeQueryBuilder.FIELD_COMMENT)
          .buildRepeatable(
              cd ->
                  Stream.concat(
                          cd.publishedComments().stream().map(c -> c.message),
                          cd.messages().stream().map(ChangeMessage::getMessage))
                      .collect(toSet()));

  /** Number of unresolved comment threads of the change, including robot comments. */
  public static final FieldDef<ChangeData, Integer> UNRESOLVED_COMMENT_COUNT =
      intRange(ChangeQueryBuilder.FIELD_UNRESOLVED_COMMENT_COUNT)
          .build(ChangeData::unresolvedCommentCount);

  /** Total number of published inline comments of the change, including robot comments. */
  public static final FieldDef<ChangeData, Integer> TOTAL_COMMENT_COUNT =
      intRange("total_comments").build(ChangeData::totalCommentCount);

  /** Whether the change is mergeable. */
  public static final FieldDef<ChangeData, String> MERGEABLE =
      exact(ChangeQueryBuilder.FIELD_MERGEABLE)
          .stored()
          .build(
              cd -> {
                Boolean m = cd.isMergeable();
                if (m == null) {
                  return null;
                }
                return m ? "1" : "0";
              });

  /** Whether the change is a merge commit. */
  public static final FieldDef<ChangeData, String> MERGE =
      exact(ChangeQueryBuilder.FIELD_MERGE)
          .stored()
          .build(
              cd -> {
                Boolean m = cd.isMerge();
                if (m == null) {
                  return null;
                }
                return m ? "1" : "0";
              });

  /** The number of inserted lines in this change. */
  public static final FieldDef<ChangeData, Integer> ADDED =
      intRange(ChangeQueryBuilder.FIELD_ADDED)
          .build(cd -> cd.changedLines().isPresent() ? cd.changedLines().get().insertions : null);

  /** The number of deleted lines in this change. */
  public static final FieldDef<ChangeData, Integer> DELETED =
      intRange(ChangeQueryBuilder.FIELD_DELETED)
          .build(cd -> cd.changedLines().isPresent() ? cd.changedLines().get().deletions : null);

  /** The total number of modified lines in this change. */
  public static final FieldDef<ChangeData, Integer> DELTA =
      intRange(ChangeQueryBuilder.FIELD_DELTA)
          .build(cd -> cd.changedLines().map(c -> c.insertions + c.deletions).orElse(null));

  /** Determines if this change is private. */
  public static final FieldDef<ChangeData, String> PRIVATE =
      exact(ChangeQueryBuilder.FIELD_PRIVATE).build(cd -> cd.change().isPrivate() ? "1" : "0");

  /** Determines if this change is work in progress. */
  public static final FieldDef<ChangeData, String> WIP =
      exact(ChangeQueryBuilder.FIELD_WIP).build(cd -> cd.change().isWorkInProgress() ? "1" : "0");

  /** Determines if this change has started review. */
  public static final FieldDef<ChangeData, String> STARTED =
      exact(ChangeQueryBuilder.FIELD_STARTED)
          .build(cd -> cd.change().hasReviewStarted() ? "1" : "0");

  /** Users who have commented on this change. */
  public static final FieldDef<ChangeData, Iterable<Integer>> COMMENTBY =
      integer(ChangeQueryBuilder.FIELD_COMMENTBY)
          .buildRepeatable(
              cd ->
                  Stream.concat(
                          cd.messages().stream().map(ChangeMessage::getAuthor),
                          cd.publishedComments().stream().map(c -> c.author.getId()))
                      .filter(Objects::nonNull)
                      .map(Account.Id::get)
                      .collect(toSet()));

  /** Star labels on this change in the format: &lt;account-id&gt;:&lt;label&gt; */
  public static final FieldDef<ChangeData, Iterable<String>> STAR =
      exact(ChangeQueryBuilder.FIELD_STAR)
          .stored()
          .buildRepeatable(
              cd ->
                  Iterables.transform(
                      cd.stars().entries(),
                      e ->
                          StarredChangesUtil.StarField.create(e.getKey(), e.getValue())
                              .toString()));

  /** Users that have starred the change with any label. */
  public static final FieldDef<ChangeData, Iterable<Integer>> STARBY =
      integer(ChangeQueryBuilder.FIELD_STARBY)
          .buildRepeatable(cd -> Iterables.transform(cd.stars().keySet(), Account.Id::get));

  /** Opaque group identifiers for this change's patch sets. */
  public static final FieldDef<ChangeData, Iterable<String>> GROUP =
      exact(ChangeQueryBuilder.FIELD_GROUP)
          .buildRepeatable(
              cd -> cd.patchSets().stream().flatMap(ps -> ps.groups().stream()).collect(toSet()));

  /** Serialized patch set object, used for pre-populating results. */
  public static final FieldDef<ChangeData, Iterable<byte[]>> PATCH_SET =
      storedOnly("_patch_set")
          .buildRepeatable(cd -> toProtos(PatchSetProtoConverter.INSTANCE, cd.patchSets()));

  /** Users who have edits on this change. */
  public static final FieldDef<ChangeData, Iterable<Integer>> EDITBY =
      integer(ChangeQueryBuilder.FIELD_EDITBY)
          .buildRepeatable(cd -> cd.editsByUser().stream().map(Account.Id::get).collect(toSet()));

  /** Users who have draft comments on this change. */
  public static final FieldDef<ChangeData, Iterable<Integer>> DRAFTBY =
      integer(ChangeQueryBuilder.FIELD_DRAFTBY)
          .buildRepeatable(cd -> cd.draftsByUser().stream().map(Account.Id::get).collect(toSet()));

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
  public static final FieldDef<ChangeData, Iterable<Integer>> REVIEWEDBY =
      integer(ChangeQueryBuilder.FIELD_REVIEWEDBY)
          .stored()
          .buildRepeatable(
              cd -> {
                Set<Account.Id> reviewedBy = cd.reviewedBy();
                if (reviewedBy.isEmpty()) {
                  return ImmutableSet.of(NOT_REVIEWED);
                }
                return reviewedBy.stream().map(Account.Id::get).collect(toList());
              });

  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_LENIENT =
      SubmitRuleOptions.builder().allowClosed(true).build();

  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_STRICT =
      SubmitRuleOptions.builder().build();

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

    SubmitRecord.Status status;
    List<StoredLabel> labels;
    List<StoredRequirement> requirements;
    String errorMessage;

    public StoredSubmitRecord(SubmitRecord rec) {
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
        for (SubmitRequirement requirement : rec.requirements) {
          StoredRequirement sr = new StoredRequirement();
          sr.type = requirement.type();
          sr.fallbackText = requirement.fallbackText();
          // For backwards compatibility, write an empty map to the index.
          // This is required, because the SubmitRequirement AutoValue can't
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
          SubmitRequirement sr =
              SubmitRequirement.builder()
                  .setType(req.type)
                  .setFallbackText(req.fallbackText)
                  .build();
          rec.requirements.add(sr);
        }
      }
      return rec;
    }
  }

  public static final FieldDef<ChangeData, Iterable<String>> SUBMIT_RECORD =
      exact("submit_record").buildRepeatable(ChangeField::formatSubmitRecordValues);

  public static final FieldDef<ChangeData, Iterable<byte[]>> STORED_SUBMIT_RECORD_STRICT =
      storedOnly("full_submit_record_strict")
          .buildRepeatable(cd -> storedSubmitRecords(cd, SUBMIT_RULE_OPTIONS_STRICT));

  public static final FieldDef<ChangeData, Iterable<byte[]>> STORED_SUBMIT_RECORD_LENIENT =
      storedOnly("full_submit_record_lenient")
          .buildRepeatable(cd -> storedSubmitRecords(cd, SUBMIT_RULE_OPTIONS_LENIENT));

  public static void parseSubmitRecords(
      Collection<String> values, SubmitRuleOptions opts, ChangeData out) {
    List<SubmitRecord> records = parseSubmitRecords(values);
    if (records.isEmpty()) {
      // Assume no values means the field is not in the index;
      // SubmitRuleEvaluator ensures the list is non-empty.
      return;
    }
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
    return formatSubmitRecordValues(
        cd.submitRecords(SUBMIT_RULE_OPTIONS_STRICT), cd.change().getOwner());
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
   * All values of all refs that were used in the course of indexing this document.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name:[hex sha]}.
   */
  public static final FieldDef<ChangeData, Iterable<byte[]>> REF_STATE =
      storedOnly("ref_state")
          .buildRepeatable(
              cd -> {
                List<byte[]> result = new ArrayList<>();
                Project.NameKey project = cd.change().getProject();

                cd.editRefs()
                    .values()
                    .forEach(r -> result.add(RefState.of(r).toByteArray(project)));
                cd.starRefs()
                    .values()
                    .forEach(r -> result.add(RefState.of(r.ref()).toByteArray(allUsers(cd))));

                ChangeNotes notes = cd.notes();
                result.add(
                    RefState.create(notes.getRefName(), notes.getMetaId()).toByteArray(project));
                notes.getRobotComments(); // Force loading robot comments.
                RobotCommentNotes robotNotes = notes.getRobotCommentNotes();
                result.add(
                    RefState.create(robotNotes.getRefName(), robotNotes.getMetaId())
                        .toByteArray(project));
                cd.draftRefs()
                    .values()
                    .forEach(r -> result.add(RefState.of(r).toByteArray(allUsers(cd))));

                return result;
              });

  /**
   * All ref wildcard patterns that were used in the course of indexing this document.
   *
   * <p>Emitted as UTF-8 encoded strings of the form {@code project:ref/name/*}. See {@link
   * RefStatePattern} for the pattern format.
   */
  public static final FieldDef<ChangeData, Iterable<byte[]>> REF_STATE_PATTERN =
      storedOnly("ref_state_pattern")
          .buildRepeatable(
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
              });

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

  private static <T> FieldDef.Getter<ChangeData, T> changeGetter(Function<Change, T> func) {
    return in -> in.change() != null ? func.apply(in.change()) : null;
  }

  private static AllUsersName allUsers(ChangeData cd) {
    return cd.getAllUsersNameForIndexing();
  }
}
