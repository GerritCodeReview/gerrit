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
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.index.FieldDef.exact;
import static com.google.gerrit.server.index.FieldDef.fullText;
import static com.google.gerrit.server.index.FieldDef.intRange;
import static com.google.gerrit.server.index.FieldDef.integer;
import static com.google.gerrit.server.index.FieldDef.prefix;
import static com.google.gerrit.server.index.FieldDef.storedOnly;
import static com.google.gerrit.server.index.FieldDef.timestamp;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.StarredChangesUtil;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.index.FieldDef;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.SchemaUtil;
import com.google.gerrit.server.index.change.StalenessChecker.RefState;
import com.google.gerrit.server.index.change.StalenessChecker.RefStatePattern;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.notedb.RobotCommentNotes;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.query.change.ChangeStatusPredicate;
import com.google.gson.Gson;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.gwtorm.server.OrmException;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.FooterLine;

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
  public static final int NO_ASSIGNEE = -1;

  private static final Gson GSON = OutputFormat.JSON_COMPACT.newGson();

  /** Legacy change ID. */
  public static final FieldDef<ChangeData, Integer> LEGACY_ID =
      integer("legacy_id").stored().build(cd -> cd.getId().get());

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
      exact(ChangeQueryBuilder.FIELD_REF).build(changeGetter(c -> c.getDest().get()));

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

  public static Set<String> getFileParts(ChangeData cd) throws OrmException {
    List<String> paths = cd.currentFilePaths();
    if (paths == null) {
      return ImmutableSet.of();
    }
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

  /** Owner/creator of the change. */
  public static final FieldDef<ChangeData, Integer> OWNER =
      integer(ChangeQueryBuilder.FIELD_OWNER).build(changeGetter(c -> c.getOwner().get()));

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
      if (c.getColumnKey().getName() != null) {
        // Add another entry without the name to provide search functionality on the email
        Address emailOnly = new Address(c.getColumnKey().getEmail());
        r.add(getReviewerByEmailFieldValue(c.getRowKey(), emailOnly));
      }
      r.add(v + ',' + c.getValue().getTime());
    }
    return r;
  }

  public static String getReviewerByEmailFieldValue(ReviewerStateInternal state, Address adr) {
    return state.toString() + ',' + adr;
  }

  public static ReviewerSet parseReviewerFieldValues(Iterable<String> values) {
    ImmutableTable.Builder<ReviewerStateInternal, Account.Id, Timestamp> b =
        ImmutableTable.builder();
    for (String v : values) {
      int f = v.indexOf(',');
      if (f < 0) {
        continue;
      }
      int l = v.lastIndexOf(',');
      if (l == f) {
        continue;
      }
      b.put(
          ReviewerStateInternal.valueOf(v.substring(0, f)),
          Account.Id.parse(v.substring(f + 1, l)),
          new Timestamp(Long.valueOf(v.substring(l + 1, v.length()))));
    }
    return ReviewerSet.fromTable(b.build());
  }

  public static ReviewerByEmailSet parseReviewerByEmailFieldValues(Iterable<String> values) {
    ImmutableTable.Builder<ReviewerStateInternal, Address, Timestamp> b = ImmutableTable.builder();
    for (String v : values) {
      int f = v.indexOf(',');
      if (f < 0) {
        continue;
      }
      int l = v.lastIndexOf(',');
      if (l == f) {
        continue;
      }
      b.put(
          ReviewerStateInternal.valueOf(v.substring(0, f)),
          Address.parse(v.substring(f + 1, l)),
          new Timestamp(Long.valueOf(v.substring(l + 1, v.length()))));
    }
    return ReviewerByEmailSet.fromTable(b.build());
  }

  /** Commit ID of any patch set on the change, using prefix match. */
  public static final FieldDef<ChangeData, Iterable<String>> COMMIT =
      prefix(ChangeQueryBuilder.FIELD_COMMIT).buildRepeatable(ChangeField::getRevisions);

  /** Commit ID of any patch set on the change, using exact match. */
  public static final FieldDef<ChangeData, Iterable<String>> EXACT_COMMIT =
      exact(ChangeQueryBuilder.FIELD_EXACTCOMMIT).buildRepeatable(ChangeField::getRevisions);

  private static Set<String> getRevisions(ChangeData cd) throws OrmException {
    Set<String> revisions = new HashSet<>();
    for (PatchSet ps : cd.patchSets()) {
      if (ps.getRevision() != null) {
        revisions.add(ps.getRevision().get());
      }
    }
    return revisions;
  }

  /** Tracking id extracted from a footer. */
  public static final FieldDef<ChangeData, Iterable<String>> TR =
      exact(ChangeQueryBuilder.FIELD_TR)
          .buildRepeatable(
              (ChangeData cd, FillArgs a) -> {
                List<FooterLine> footers = cd.commitFooters();
                if (footers == null) {
                  return ImmutableSet.of();
                }
                return Sets.newHashSet(a.trackingFooters.extract(footers).values());
              });

  /** List of labels on the current patch set including change owner votes. */
  public static final FieldDef<ChangeData, Iterable<String>> LABEL =
      exact("label2").buildRepeatable(cd -> getLabels(cd, true));

  private static Iterable<String> getLabels(ChangeData cd, boolean owners) throws OrmException {
    Set<String> allApprovals = new HashSet<>();
    Set<String> distinctApprovals = new HashSet<>();
    for (PatchSetApproval a : cd.currentApprovals()) {
      if (a.getValue() != 0 && !a.isLegacySubmit()) {
        allApprovals.add(formatLabel(a.getLabel(), a.getValue(), a.getAccountId()));
        if (owners && cd.change().getOwner().equals(a.getAccountId())) {
          allApprovals.add(
              formatLabel(a.getLabel(), a.getValue(), ChangeQueryBuilder.OWNER_ACCOUNT_ID));
        }
        distinctApprovals.add(formatLabel(a.getLabel(), a.getValue()));
      }
    }
    allApprovals.addAll(distinctApprovals);
    return allApprovals;
  }

  public static Set<String> getAuthorParts(ChangeData cd) throws OrmException, IOException {
    return SchemaUtil.getPersonParts(cd.getAuthor());
  }

  public static Set<String> getAuthorNameAndEmail(ChangeData cd) throws OrmException, IOException {
    return getNameAndEmail(cd.getAuthor());
  }

  public static Set<String> getCommitterParts(ChangeData cd) throws OrmException, IOException {
    return SchemaUtil.getPersonParts(cd.getCommitter());
  }

  public static Set<String> getCommitterNameAndEmail(ChangeData cd)
      throws OrmException, IOException {
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

  public static final ProtobufCodec<Change> CHANGE_CODEC = CodecFactory.encoder(Change.class);

  /** Serialized change object, used for pre-populating results. */
  public static final FieldDef<ChangeData, byte[]> CHANGE =
      storedOnly("_change").build(changeGetter(CHANGE_CODEC::encodeToByteArray));

  public static final ProtobufCodec<PatchSetApproval> APPROVAL_CODEC =
      CodecFactory.encoder(PatchSetApproval.class);

  /** Serialized approvals for the current patch set, used for pre-populating results. */
  public static final FieldDef<ChangeData, Iterable<byte[]>> APPROVAL =
      storedOnly("_approval")
          .buildRepeatable(cd -> toProtos(APPROVAL_CODEC, cd.currentApprovals()));

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

  /** Number of unresolved comments of the change. */
  public static final FieldDef<ChangeData, Integer> UNRESOLVED_COMMENT_COUNT =
      intRange(ChangeQueryBuilder.FIELD_UNRESOLVED_COMMENT_COUNT)
          .stored()
          .build(ChangeData::unresolvedCommentCount);

  /** Whether the change is mergeable. */
  public static final FieldDef<ChangeData, String> MERGEABLE =
      exact(ChangeQueryBuilder.FIELD_MERGEABLE)
          .stored()
          .build(
              (ChangeData cd, FillArgs a) -> {
                Boolean m = cd.isMergeable(a.accounts);
                if (m == null) {
                  return null;
                }
                return m ? "1" : "0";
              });

  /** The number of inserted lines in this change. */
  public static final FieldDef<ChangeData, Integer> ADDED =
      intRange(ChangeQueryBuilder.FIELD_ADDED)
          .stored()
          .build(cd -> cd.changedLines().isPresent() ? cd.changedLines().get().insertions : null);

  /** The number of deleted lines in this change. */
  public static final FieldDef<ChangeData, Integer> DELETED =
      intRange(ChangeQueryBuilder.FIELD_DELETED)
          .stored()
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
              cd ->
                  cd.patchSets().stream().flatMap(ps -> ps.getGroups().stream()).collect(toSet()));

  public static final ProtobufCodec<PatchSet> PATCH_SET_CODEC =
      CodecFactory.encoder(PatchSet.class);

  /** Serialized patch set object, used for pre-populating results. */
  public static final FieldDef<ChangeData, Iterable<byte[]>> PATCH_SET =
      storedOnly("_patch_set").buildRepeatable(cd -> toProtos(PATCH_SET_CODEC, cd.patchSets()));

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

  // Submit rule options in this class should never use fastEvalLabels. This
  // slows down indexing slightly but produces correct search results.
  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_LENIENT =
      SubmitRuleOptions.defaults().allowClosed(true).allowDraft(true).build();

  public static final SubmitRuleOptions SUBMIT_RULE_OPTIONS_STRICT =
      SubmitRuleOptions.defaults().build();

  /**
   * JSON type for storing SubmitRecords.
   *
   * <p>Stored fields need to use a stable format over a long period; this type insulates the index
   * from implementation changes in SubmitRecord itself.
   */
  static class StoredSubmitRecord {
    static class StoredLabel {
      String label;
      SubmitRecord.Label.Status status;
      Integer appliedBy;
    }

    SubmitRecord.Status status;
    List<StoredLabel> labels;
    String errorMessage;

    StoredSubmitRecord(SubmitRecord rec) {
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
    }

    private SubmitRecord toSubmitRecord() {
      SubmitRecord rec = new SubmitRecord();
      rec.status = status;
      rec.errorMessage = errorMessage;
      if (labels != null) {
        rec.labels = new ArrayList<>(labels.size());
        for (StoredLabel label : labels) {
          SubmitRecord.Label srl = new SubmitRecord.Label();
          srl.label = label.label;
          srl.status = label.status;
          srl.appliedBy = label.appliedBy != null ? new Account.Id(label.appliedBy) : null;
          rec.labels.add(srl);
        }
      }
      return rec;
    }
  }

  public static final FieldDef<ChangeData, Iterable<String>> SUBMIT_RECORD =
      exact("submit_record")
          .buildRepeatable((ChangeData cd, FillArgs a) -> formatSubmitRecordValues(a.accounts, cd));

  public static final FieldDef<ChangeData, Iterable<byte[]>> STORED_SUBMIT_RECORD_STRICT =
      storedOnly("full_submit_record_strict")
          .buildRepeatable(
              (ChangeData cd, FillArgs a) ->
                  storedSubmitRecords(a.accounts, cd, SUBMIT_RULE_OPTIONS_STRICT));

  public static final FieldDef<ChangeData, Iterable<byte[]>> STORED_SUBMIT_RECORD_LENIENT =
      storedOnly("full_submit_record_lenient")
          .buildRepeatable(
              (ChangeData cd, FillArgs a) ->
                  storedSubmitRecords(a.accounts, cd, SUBMIT_RULE_OPTIONS_LENIENT));

  public static void parseSubmitRecords(
      Collection<String> values, SubmitRuleOptions opts, ChangeData out) {
    checkArgument(!opts.fastEvalLabels());
    List<SubmitRecord> records = parseSubmitRecords(values);
    if (records.isEmpty()) {
      // Assume no values means the field is not in the index;
      // SubmitRuleEvaluator ensures the list is non-empty.
      return;
    }
    out.setSubmitRecords(opts, records);

    // Cache the fastEvalLabels variant as well so it can be used by
    // ChangeJson.
    out.setSubmitRecords(opts.toBuilder().fastEvalLabels(true).build(), records);
  }

  @VisibleForTesting
  static List<SubmitRecord> parseSubmitRecords(Collection<String> values) {
    return values
        .stream()
        .map(v -> GSON.fromJson(v, StoredSubmitRecord.class).toSubmitRecord())
        .collect(toList());
  }

  @VisibleForTesting
  static List<byte[]> storedSubmitRecords(List<SubmitRecord> records) {
    return Lists.transform(records, r -> GSON.toJson(new StoredSubmitRecord(r)).getBytes(UTF_8));
  }

  private static Iterable<byte[]> storedSubmitRecords(
      Accounts accounts, ChangeData cd, SubmitRuleOptions opts) throws OrmException {
    return storedSubmitRecords(cd.submitRecords(accounts, opts));
  }

  public static List<String> formatSubmitRecordValues(Accounts accounts, ChangeData cd)
      throws OrmException {
    return formatSubmitRecordValues(
        cd.submitRecords(accounts, SUBMIT_RULE_OPTIONS_STRICT), cd.change().getOwner());
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
              (cd, a) -> {
                List<byte[]> result = new ArrayList<>();
                Project.NameKey project = cd.change().getProject();

                cd.editRefs()
                    .values()
                    .forEach(r -> result.add(RefState.of(r).toByteArray(project)));
                cd.starRefs()
                    .values()
                    .forEach(r -> result.add(RefState.of(r.ref()).toByteArray(a.allUsers)));

                if (PrimaryStorage.of(cd.change()) == PrimaryStorage.NOTE_DB) {
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
                      .forEach(r -> result.add(RefState.of(r).toByteArray(a.allUsers)));
                }

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
              (cd, a) -> {
                Change.Id id = cd.getId();
                Project.NameKey project = cd.change().getProject();
                List<byte[]> result = new ArrayList<>(3);
                result.add(
                    RefStatePattern.create(
                            RefNames.REFS_USERS + "*/" + RefNames.EDIT_PREFIX + id + "/*")
                        .toByteArray(project));
                result.add(
                    RefStatePattern.create(RefNames.refsStarredChangesPrefix(id) + "*")
                        .toByteArray(a.allUsers));
                if (PrimaryStorage.of(cd.change()) == PrimaryStorage.NOTE_DB) {
                  result.add(
                      RefStatePattern.create(RefNames.refsDraftCommentsPrefix(id) + "*")
                          .toByteArray(a.allUsers));
                }
                return result;
              });

  private static String getTopic(ChangeData cd) throws OrmException {
    Change c = cd.change();
    if (c == null) {
      return null;
    }
    return firstNonNull(c.getTopic(), "");
  }

  private static <T> List<byte[]> toProtos(ProtobufCodec<T> codec, Collection<T> objs)
      throws OrmException {
    List<byte[]> result = Lists.newArrayListWithCapacity(objs.size());
    ByteArrayOutputStream out = new ByteArrayOutputStream(256);
    try {
      for (T obj : objs) {
        out.reset();
        CodedOutputStream cos = CodedOutputStream.newInstance(out);
        codec.encode(obj, cos);
        cos.flush();
        result.add(out.toByteArray());
      }
    } catch (IOException e) {
      throw new OrmException(e);
    }
    return result;
  }

  private static <T> FieldDef.Getter<ChangeData, T> changeGetter(Function<Change, T> func) {
    return in -> in.change() != null ? func.apply(in.change()) : null;
  }
}
