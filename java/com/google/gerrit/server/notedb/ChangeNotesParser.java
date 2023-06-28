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

package com.google.gerrit.server.notedb;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_ATTENTION;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_BRANCH;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_CHANGE_ID;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_CHERRY_PICK_OF;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_COMMIT;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_COPIED_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_CURRENT;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_CUSTOM_KEYED_VALUE;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_GROUPS;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_PATCH_SET_DESCRIPTION;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_PRIVATE;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_REAL_USER;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_REVERT_OF;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_SUBJECT;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_SUBMISSION_ID;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_TAG;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_TOPIC;
import static com.google.gerrit.server.notedb.ChangeNoteFooters.FOOTER_WORK_IN_PROGRESS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.parseCommitMessageRange;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.joining;

import com.google.common.base.Enums;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.flogger.FluentLogger;
import com.google.common.primitives.Ints;
import com.google.errorprone.annotations.FormatMethod;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRecord.Label.Status;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.gerrit.server.notedb.ChangeNotesParseApprovalUtil.ParsedPatchSetApproval;
import com.google.gerrit.server.util.LabelVote;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * Parses {@link ChangeNotesState} out of the change meta ref.
 *
 * <p>NOTE: all changes to the change notes storage format must be both forward and backward
 * compatible, i.e.:
 *
 * <ul>
 *   <li>The server, running the new binary version must be able to parse the data, written by the
 *       previous binary version.
 *   <li>The server, running the old binary version must be able to parse the data, written by the
 *       new binary version.
 * </ul>
 *
 * <p>Thus, when introducing storage format update, the following procedure must be used:
 *
 * <ol>
 *   <li>The read path ({@link ChangeNotesParser}) needs to be updated to handle both the old and
 *       the new data format.
 *   <li>In a separate change, the write path (e.g. {@link ChangeUpdate}, {@link ChangeNoteJson}) is
 *       updated to write the new format, guarded by {@link
 *       com.google.gerrit.server.experiments.ExperimentFeatures} flag, if possible.
 *   <li>Once the 'read' change is roll out and is roll back safe, the 'write' change can be
 *       submitted/the experiment flag can be flipped.
 * </ol>
 */
class ChangeNotesParser {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Splitter RULE_SPLITTER = Splitter.on(": ");
  private static final Splitter HASHTAG_SPLITTER = Splitter.on(",");

  // Private final members initialized in the constructor.
  private final ChangeNoteJson changeNoteJson;
  private final NoteDbMetrics metrics;
  private final Change.Id id;
  private final ObjectId tip;
  private final ChangeNotesRevWalk walk;

  // Private final but mutable members initialized in the constructor and filled
  // in during the parsing process.
  private final Table<Account.Id, ReviewerStateInternal, Instant> reviewers;
  private final Table<Address, ReviewerStateInternal, Instant> reviewersByEmail;
  private final List<Account.Id> allPastReviewers;
  private final List<ReviewerStatusUpdate> reviewerUpdates;
  /** Holds only the most recent update per user. Older updates are discarded. */
  private final Map<Account.Id, AttentionSetUpdate> latestAttentionStatus;
  /** Holds all updates to attention set. */
  private final List<AttentionSetUpdate> allAttentionSetUpdates;

  private final List<SubmitRecord> submitRecords;
  private final ListMultimap<ObjectId, HumanComment> humanComments;
  private final List<SubmitRequirementResult> submitRequirementResults;
  private final Map<PatchSet.Id, PatchSet.Builder> patchSets;
  private final Set<PatchSet.Id> deletedPatchSets;
  private final Map<PatchSet.Id, PatchSetState> patchSetStates;
  private final List<PatchSet.Id> currentPatchSets;
  private final TreeMap<String, String> customKeyedValues;
  private final Map<PatchSetApproval.Key, PatchSetApproval.Builder> approvals;
  private final List<PatchSetApproval.Builder> bufferedApprovals;
  private final List<ChangeMessage> allChangeMessages;

  // Non-final private members filled in during the parsing process.
  private String branch;
  private Change.Status status;
  private String topic;
  private Set<String> hashtags;
  private Instant createdOn;
  private Instant lastUpdatedOn;
  private Account.Id ownerId;
  private String serverId;
  private String changeId;
  private String subject;
  private String originalSubject;
  private String submissionId;
  private String tag;
  private RevisionNoteMap<ChangeRevisionNote> revisionNoteMap;
  private Boolean isPrivate;
  private Boolean workInProgress;
  private Boolean previousWorkInProgressFooter;
  private Boolean hasReviewStarted;
  private ReviewerSet pendingReviewers;
  private ReviewerByEmailSet pendingReviewersByEmail;
  private Change.Id revertOf;
  private int updateCount;
  // Null indicates that the field was not parsed (yet).
  // We only set the value once, based on the latest update (the actual value or Optional.empty() if
  // the latest record unsets the field).
  private Optional<PatchSet.Id> cherryPickOf;
  private Instant mergedOn;
  private final NoteDbUtil noteDbUtil;

  ChangeNotesParser(
      Change.Id changeId,
      ObjectId tip,
      ChangeNotesRevWalk walk,
      ChangeNoteJson changeNoteJson,
      NoteDbMetrics metrics,
      NoteDbUtil noteDbUtil) {
    this.id = changeId;
    this.tip = tip;
    this.walk = walk;
    this.changeNoteJson = changeNoteJson;
    this.metrics = metrics;
    this.noteDbUtil = noteDbUtil;
    approvals = new LinkedHashMap<>();
    bufferedApprovals = new ArrayList<>();
    reviewers = HashBasedTable.create();
    reviewersByEmail = HashBasedTable.create();
    pendingReviewers = ReviewerSet.empty();
    pendingReviewersByEmail = ReviewerByEmailSet.empty();
    allPastReviewers = new ArrayList<>();
    reviewerUpdates = new ArrayList<>();
    latestAttentionStatus = new HashMap<>();
    allAttentionSetUpdates = new ArrayList<>();
    submitRecords = Lists.newArrayListWithExpectedSize(1);
    allChangeMessages = new ArrayList<>();
    humanComments = MultimapBuilder.hashKeys().arrayListValues().build();
    submitRequirementResults = new ArrayList<>();
    patchSets = new HashMap<>();
    deletedPatchSets = new HashSet<>();
    patchSetStates = new HashMap<>();
    currentPatchSets = new ArrayList<>();
    customKeyedValues = new TreeMap<>();
  }

  ChangeNotesState parseAll() throws ConfigInvalidException, IOException {
    // Don't include initial parse in timer, as this might do more I/O to page
    // in the block containing most commits. Later reads are not guaranteed to
    // avoid I/O, but often should.
    walk.reset();
    walk.markStart(walk.parseCommit(tip));

    try (Timer0.Context timer = metrics.parseLatency.start()) {
      ChangeNotesCommit commit;
      while ((commit = walk.next()) != null) {
        parse(commit);
      }
      if (hasReviewStarted == null) {
        if (previousWorkInProgressFooter == null) {
          hasReviewStarted = true;
        } else {
          hasReviewStarted = !previousWorkInProgressFooter;
        }
      }
      parseNotes();
      allPastReviewers.addAll(reviewers.rowKeySet());
      pruneReviewers();
      pruneReviewersByEmail();

      updatePatchSetStates();
      checkMandatoryFooters();
    }

    pruneEmptyCustomKeyedValues();
    return buildState();
  }

  RevisionNoteMap<ChangeRevisionNote> getRevisionNoteMap() {
    return revisionNoteMap;
  }

  private ChangeNotesState buildState() throws ConfigInvalidException {
    return ChangeNotesState.create(
        tip.copy(),
        id,
        Change.key(changeId),
        createdOn,
        lastUpdatedOn,
        ownerId,
        serverId,
        branch,
        buildCurrentPatchSetId(),
        subject,
        topic,
        originalSubject,
        submissionId,
        status,
        firstNonNull(hashtags, ImmutableSet.of()),
        ImmutableSortedMap.copyOfSorted(customKeyedValues),
        buildPatchSets(),
        buildApprovals(),
        ReviewerSet.fromTable(Tables.transpose(reviewers)),
        ReviewerByEmailSet.fromTable(Tables.transpose(reviewersByEmail)),
        pendingReviewers,
        pendingReviewersByEmail,
        allPastReviewers,
        buildReviewerUpdates(),
        ImmutableSet.copyOf(latestAttentionStatus.values()),
        allAttentionSetUpdates,
        submitRecords,
        buildAllMessages(),
        humanComments,
        submitRequirementResults,
        firstNonNull(isPrivate, false),
        firstNonNull(workInProgress, false),
        firstNonNull(hasReviewStarted, true),
        revertOf,
        cherryPickOf != null ? cherryPickOf.orElse(null) : null,
        updateCount,
        mergedOn);
  }

  private Map<PatchSet.Id, PatchSet> buildPatchSets() throws ConfigInvalidException {
    Map<PatchSet.Id, PatchSet> result = Maps.newHashMapWithExpectedSize(patchSets.size());
    for (Map.Entry<PatchSet.Id, PatchSet.Builder> e : patchSets.entrySet()) {
      try {
        PatchSet ps = e.getValue().build();
        result.put(ps.id(), ps);
      } catch (Exception ex) {
        ConfigInvalidException cie = parseException("Error building patch set %s", e.getKey());
        cie.initCause(ex);
        throw cie;
      }
    }
    return result;
  }

  @Nullable
  private PatchSet.Id buildCurrentPatchSetId() {
    // currentPatchSets are in parse order, i.e. newest first. Pick the first
    // patch set that was marked as current, excluding deleted patch sets.
    for (PatchSet.Id psId : currentPatchSets) {
      if (patchSetCommitParsed(psId)) {
        return psId;
      }
    }
    return null;
  }

  private ListMultimap<PatchSet.Id, PatchSetApproval> buildApprovals() {
    ListMultimap<PatchSet.Id, PatchSetApproval> result =
        MultimapBuilder.hashKeys().arrayListValues().build();
    for (PatchSetApproval.Builder a : approvals.values()) {
      if (!patchSetCommitParsed(a.key().patchSetId())) {
        continue; // Patch set deleted or missing.
      } else if (allPastReviewers.contains(a.key().accountId())
          && !reviewers.containsRow(a.key().accountId())) {
        continue; // Reviewer was explicitly removed.
      }
      result.put(a.key().patchSetId(), a.build());
    }
    if (status != null && status.isClosed() && !isAnyApprovalCopied(result)) {
      // If the change is closed, check if there are "submit records" with approvals that do not
      // exist on the latest patch-set and copy them to the latest patch-set.
      // We do not invoke this logic if any approval is copied. This is because prior to change
      // https://gerrit-review.googlesource.com/c/gerrit/+/318135 we used to copy approvals
      // dynamically (e.g. when requesting the change page). After that change, we started
      // persisting copied votes in NoteDb, so we don't need to do this back-filling.
      // Prior to that change (318135), we could've had changes with dynamically copied approvals
      // that were merged in NoteDb but these approvals do not exist on the latest patch-set, so
      // we need to back-fill these approvals.
      PatchSet.Id latestPs = buildCurrentPatchSetId();
      backFillMissingCopiedApprovalsFromSubmitRecords(result, latestPs).stream()
          .forEach(a -> result.put(latestPs, a));
    }
    result.keySet().forEach(k -> result.get(k).sort(ChangeNotes.PSA_BY_TIME));
    return result;
  }

  /**
   * Returns patch-set approvals that do not exist on the latest patch-set but for which a submit
   * record exists in NoteDb when the change was merged.
   */
  private List<PatchSetApproval> backFillMissingCopiedApprovalsFromSubmitRecords(
      ListMultimap<PatchSet.Id, PatchSetApproval> allApprovals, @Nullable PatchSet.Id latestPs) {
    List<PatchSetApproval> copiedApprovals = new ArrayList<>();
    if (latestPs == null) {
      return copiedApprovals;
    }
    List<PatchSetApproval> approvalsOnLatestPs = allApprovals.get(latestPs);
    ListMultimap<Account.Id, PatchSetApproval> approvalsByUser = getApprovalsByUser(allApprovals);
    List<SubmitRecord.Label> submitRecordLabels =
        submitRecords.stream()
            .filter(r -> r.labels != null)
            .flatMap(r -> r.labels.stream())
            .filter(label -> Status.OK.equals(label.status) || Status.MAY.equals(label.status))
            .collect(Collectors.toList());
    for (SubmitRecord.Label recordLabel : submitRecordLabels) {
      String labelName = recordLabel.label;
      Account.Id appliedBy = recordLabel.appliedBy;
      if (appliedBy == null || labelName == null) {
        continue;
      }
      boolean existsAtLatestPs =
          approvalsOnLatestPs.stream()
              .anyMatch(a -> a.accountId().equals(appliedBy) && a.label().equals(labelName));
      if (existsAtLatestPs) {
        continue;
      }
      // Search for an approval for this label on the max previous patch-set and copy the approval.
      Collection<PatchSetApproval> userApprovals =
          approvalsByUser.get(appliedBy).stream()
              .filter(approval -> approval.label().equals(labelName))
              .collect(Collectors.toList());
      if (userApprovals.isEmpty()) {
        continue;
      }
      PatchSetApproval lastApproved =
          Collections.max(userApprovals, comparingInt(a -> a.patchSetId().get()));
      copiedApprovals.add(lastApproved.copyWithPatchSet(latestPs));
    }
    return copiedApprovals;
  }

  private boolean isAnyApprovalCopied(ListMultimap<PatchSet.Id, PatchSetApproval> allApprovals) {
    return allApprovals.values().stream().anyMatch(approval -> approval.copied());
  }

  private ListMultimap<Account.Id, PatchSetApproval> getApprovalsByUser(
      ListMultimap<PatchSet.Id, PatchSetApproval> allApprovals) {
    return allApprovals.values().stream()
        .collect(
            ImmutableListMultimap.toImmutableListMultimap(
                PatchSetApproval::accountId, Function.identity()));
  }

  private List<ReviewerStatusUpdate> buildReviewerUpdates() {
    List<ReviewerStatusUpdate> result = new ArrayList<>();
    HashMap<Account.Id, ReviewerStateInternal> lastState = new HashMap<>();
    for (ReviewerStatusUpdate u : Lists.reverse(reviewerUpdates)) {
      if (!Objects.equals(ownerId, u.reviewer()) && lastState.get(u.reviewer()) != u.state()) {
        result.add(u);
        lastState.put(u.reviewer(), u.state());
      }
    }
    return result;
  }

  private List<ChangeMessage> buildAllMessages() {
    return Lists.reverse(allChangeMessages);
  }

  private void parse(ChangeNotesCommit commit) throws ConfigInvalidException {
    Instant commitTimestamp = getCommitTimestamp(commit);

    createdOn = commitTimestamp;
    parseTag(commit);

    if (branch == null) {
      branch = parseBranch(commit);
    }

    PatchSet.Id psId = parsePatchSetId(commit);
    PatchSetState psState = parsePatchSetState(commit);
    if (psState != null) {
      if (!patchSetStates.containsKey(psId)) {
        patchSetStates.put(psId, psState);
      }
      if (psState == PatchSetState.DELETED) {
        deletedPatchSets.add(psId);
      }
    }

    Account.Id accountId = parseIdent(commit);
    if (accountId != null) {
      ownerId = accountId;
      PersonIdent personIdent = commit.getAuthorIdent();
      serverId = NoteDbUtil.extractHostPartFromPersonIdent(personIdent);
    } else {
      serverId = "UNKNOWN_SERVER_ID";
    }
    Account.Id realAccountId = parseRealAccountId(commit, accountId);

    if (changeId == null) {
      changeId = parseChangeId(commit);
    }

    String currSubject = parseSubject(commit);
    if (currSubject != null) {
      if (subject == null) {
        subject = currSubject;
      }
      originalSubject = currSubject;
    }

    boolean hasChangeMessage =
        parseChangeMessage(psId, accountId, realAccountId, commit, commitTimestamp);
    if (topic == null) {
      topic = parseTopic(commit);
    }

    parseHashtags(commit);
    parseCustomKeyedValues(commit);
    parseAttentionSetUpdates(commit);

    parseSubmission(commit, commitTimestamp);

    if (lastUpdatedOn == null || commitTimestamp.isAfter(lastUpdatedOn)) {
      lastUpdatedOn = commitTimestamp;
    }

    if (deletedPatchSets.contains(psId)) {
      // Do not update PS details as PS was deleted and this meta data is of no relevance.
      return;
    }

    // Parse mutable patch set fields first so they can be recorded in the PendingPatchSetFields.
    parseDescription(psId, commit);
    parseGroups(psId, commit);

    ObjectId currRev = parseRevision(commit);
    if (currRev != null) {
      parsePatchSet(psId, currRev, accountId, realAccountId, commitTimestamp);
    }
    parseCurrentPatchSet(psId, commit);

    if (status == null) {
      status = parseStatus(commit);
    }

    // Parse approvals after status to treat approvals in the same commit as
    // "Status: merged" as non-post-submit.
    for (String line : commit.getFooterLineValues(FOOTER_LABEL)) {
      parseApproval(psId, accountId, realAccountId, commitTimestamp, line);
    }
    for (String line : commit.getFooterLineValues(FOOTER_COPIED_LABEL)) {
      parseCopiedApproval(psId, commitTimestamp, line);
    }

    for (ReviewerStateInternal state : ReviewerStateInternal.values()) {
      for (String line : commit.getFooterLineValues(state.getFooterKey())) {
        parseReviewer(commitTimestamp, state, line);
      }
      for (String line : commit.getFooterLineValues(state.getByEmailFooterKey())) {
        parseReviewerByEmail(commitTimestamp, state, line);
      }
      // Don't update timestamp when a reviewer was added, matching RevewDb
      // behavior.
    }

    if (isPrivate == null) {
      parseIsPrivate(commit);
    }

    if (revertOf == null) {
      revertOf = parseRevertOf(commit);
    }

    if (cherryPickOf == null) {
      cherryPickOf = parseCherryPickOf(commit);
    }

    previousWorkInProgressFooter = null;
    parseWorkInProgress(commit);
    if (countTowardsMaxUpdatesLimit(commit, hasChangeMessage)) {
      updateCount++;
    }
  }

  private void parseSubmission(ChangeNotesCommit commit, Instant commitTimestamp)
      throws ConfigInvalidException {
    // Only parse the most recent sumbit commit (there should be exactly one).
    if (submissionId == null) {
      submissionId = parseSubmissionId(commit);
    }

    if (submissionId != null && mergedOn == null) {
      mergedOn = commitTimestamp;
    }

    if (submitRecords.isEmpty()) {
      // Only parse the most recent set of submit records; any older ones are
      // still there, but not currently used.
      parseSubmitRecords(commit.getFooterLineValues(FOOTER_SUBMITTED_WITH));
    }
  }

  private String parseSubmissionId(ChangeNotesCommit commit) throws ConfigInvalidException {
    return parseOneFooter(commit, FOOTER_SUBMISSION_ID);
  }

  @Nullable
  private String parseBranch(ChangeNotesCommit commit) throws ConfigInvalidException {
    String branch = parseOneFooter(commit, FOOTER_BRANCH);
    return branch != null ? RefNames.fullName(branch) : null;
  }

  private String parseChangeId(ChangeNotesCommit commit) throws ConfigInvalidException {
    return parseOneFooter(commit, FOOTER_CHANGE_ID);
  }

  private String parseSubject(ChangeNotesCommit commit) throws ConfigInvalidException {
    return parseOneFooter(commit, FOOTER_SUBJECT);
  }

  private Account.Id parseRealAccountId(ChangeNotesCommit commit, Account.Id effectiveAccountId)
      throws ConfigInvalidException {
    String realUser = parseOneFooter(commit, FOOTER_REAL_USER);
    if (realUser == null) {
      return effectiveAccountId;
    }
    PersonIdent ident = RawParseUtils.parsePersonIdent(realUser);
    return parseIdent(ident);
  }

  private String parseTopic(ChangeNotesCommit commit) throws ConfigInvalidException {
    return parseOneFooter(commit, FOOTER_TOPIC);
  }

  @Nullable
  private String parseOneFooter(ChangeNotesCommit commit, FooterKey footerKey)
      throws ConfigInvalidException {
    List<String> footerLines = commit.getFooterLineValues(footerKey);
    if (footerLines.isEmpty()) {
      return null;
    } else if (footerLines.size() > 1) {
      throw expectedOneFooter(footerKey, footerLines);
    }
    return footerLines.get(0);
  }

  private String parseExactlyOneFooter(ChangeNotesCommit commit, FooterKey footerKey)
      throws ConfigInvalidException {
    String line = parseOneFooter(commit, footerKey);
    if (line == null) {
      throw expectedOneFooter(footerKey, Collections.emptyList());
    }
    return line;
  }

  @Nullable
  private ObjectId parseRevision(ChangeNotesCommit commit) throws ConfigInvalidException {
    String sha = parseOneFooter(commit, FOOTER_COMMIT);
    if (sha == null) {
      return null;
    }
    try {
      return ObjectId.fromString(sha);
    } catch (InvalidObjectIdException e) {
      ConfigInvalidException cie = invalidFooter(FOOTER_COMMIT, sha);
      cie.initCause(e);
      throw cie;
    }
  }

  private void parsePatchSet(
      PatchSet.Id psId, ObjectId rev, Account.Id accountId, Account.Id realAccountId, Instant ts)
      throws ConfigInvalidException {
    if (accountId == null) {
      throw parseException("patch set %s requires an identified user as uploader", psId.get());
    }
    if (patchSetCommitParsed(psId)) {
      ObjectId commitId = patchSets.get(psId).commitId().orElseThrow(IllegalStateException::new);
      throw new ConfigInvalidException(
          String.format(
              "Multiple revisions parsed for patch set %s: %s and %s",
              psId.get(), commitId.name(), rev.name()));
    }
    patchSets
        .computeIfAbsent(psId, id -> PatchSet.builder())
        .id(psId)
        .commitId(rev)
        .uploader(accountId)
        .realUploader(realAccountId)
        .createdOn(ts);
    // Fields not set here:
    // * Groups, parsed earlier in parseGroups.
    // * Description, parsed earlier in parseDescription.
    // * Push certificate, parsed later in parseNotes.
  }

  private void parseGroups(PatchSet.Id psId, ChangeNotesCommit commit)
      throws ConfigInvalidException {
    String groupsStr = parseOneFooter(commit, FOOTER_GROUPS);
    if (groupsStr == null) {
      return;
    }
    checkPatchSetCommitNotParsed(psId, FOOTER_GROUPS);
    PatchSet.Builder pending = patchSets.computeIfAbsent(psId, id -> PatchSet.builder());
    if (pending.groups().isEmpty()) {
      pending.groups(PatchSet.splitGroups(groupsStr));
    }
  }

  private void parseCurrentPatchSet(PatchSet.Id psId, ChangeNotesCommit commit)
      throws ConfigInvalidException {
    // This commit implies a new current patch set if either it creates a new
    // patch set, or sets the current field explicitly.
    boolean current = false;
    if (parseOneFooter(commit, FOOTER_COMMIT) != null) {
      current = true;
    } else {
      String currentStr = parseOneFooter(commit, FOOTER_CURRENT);
      if (Boolean.TRUE.toString().equalsIgnoreCase(currentStr)) {
        current = true;
      } else if (currentStr != null) {
        // Only "true" is allowed; unsetting the current patch set makes no
        // sense.
        throw invalidFooter(FOOTER_CURRENT, currentStr);
      }
    }
    if (current) {
      currentPatchSets.add(psId);
    }
  }

  private void parseHashtags(ChangeNotesCommit commit) throws ConfigInvalidException {
    // Commits are parsed in reverse order and only the last set of hashtags
    // should be used.
    if (hashtags != null) {
      return;
    }
    List<String> hashtagsLines = commit.getFooterLineValues(FOOTER_HASHTAGS);
    if (hashtagsLines.isEmpty()) {
      return;
    } else if (hashtagsLines.size() > 1) {
      throw expectedOneFooter(FOOTER_HASHTAGS, hashtagsLines);
    } else if (hashtagsLines.get(0).isEmpty()) {
      hashtags = ImmutableSet.of();
    } else {
      hashtags = Sets.newHashSet(HASHTAG_SPLITTER.split(hashtagsLines.get(0)));
    }
  }

  private void parseCustomKeyedValues(ChangeNotesCommit commit) {
    for (String customKeyedValueLine : commit.getFooterLineValues(FOOTER_CUSTOM_KEYED_VALUE)) {
      String[] parts = customKeyedValueLine.split("=", 2);
      String key = parts[0];
      String value = parts[1];
      // Commits are parsed in reverse order and only the last set of values
      // should be used.  An empty value for a key means it's a deletion.
      customKeyedValues.putIfAbsent(key, value);
    }
  }

  private void pruneEmptyCustomKeyedValues() {
    List<String> toRemove = new ArrayList<>();
    for (Map.Entry<String, String> entry : customKeyedValues.entrySet()) {
      if (entry.getValue().length() == 0) {
        toRemove.add(entry.getKey());
      }
    }

    for (String key : toRemove) {
      customKeyedValues.remove(key);
    }
  }

  private void parseAttentionSetUpdates(ChangeNotesCommit commit) throws ConfigInvalidException {
    List<String> attentionStrings = commit.getFooterLineValues(FOOTER_ATTENTION);
    for (String attentionString : attentionStrings) {

      Optional<AttentionSetUpdate> attentionStatus =
          ChangeNoteUtil.attentionStatusFromJson(
              Instant.ofEpochSecond(commit.getCommitTime()), attentionString, noteDbUtil);
      if (!attentionStatus.isPresent()) {
        throw invalidFooter(FOOTER_ATTENTION, attentionString);
      }
      // Processing is in reverse chronological order. Keep only the latest update.
      latestAttentionStatus.putIfAbsent(attentionStatus.get().account(), attentionStatus.get());

      // Keep all updates as well.
      allAttentionSetUpdates.add(attentionStatus.get());
    }
  }

  private void parseTag(ChangeNotesCommit commit) throws ConfigInvalidException {
    tag = null;
    List<String> tagLines = commit.getFooterLineValues(FOOTER_TAG);
    if (tagLines.isEmpty()) {
      return;
    } else if (tagLines.size() == 1) {
      tag = tagLines.get(0);
    } else {
      throw expectedOneFooter(FOOTER_TAG, tagLines);
    }
  }

  @Nullable
  private Change.Status parseStatus(ChangeNotesCommit commit) throws ConfigInvalidException {
    List<String> statusLines = commit.getFooterLineValues(FOOTER_STATUS);
    if (statusLines.isEmpty()) {
      return null;
    } else if (statusLines.size() > 1) {
      throw expectedOneFooter(FOOTER_STATUS, statusLines);
    }
    Change.Status status =
        Enums.getIfPresent(Change.Status.class, statusLines.get(0).toUpperCase(Locale.US)).orNull();
    if (status == null) {
      throw invalidFooter(FOOTER_STATUS, statusLines.get(0));
    }
    // All approvals after MERGED and before the next status change get the postSubmit
    // bit. (Currently the state can't change from MERGED to something else, but just in case.) The
    // exception is the legacy SUBM approval, which is never considered post-submit, but might end
    // up sorted after the submit during rebuilding.
    if (status == Change.Status.MERGED) {
      for (PatchSetApproval.Builder psa : bufferedApprovals) {
        if (!psa.key().isLegacySubmit()) {
          psa.postSubmit(true);
        }
      }
    }
    bufferedApprovals.clear();
    return status;
  }

  private PatchSet.Id parsePatchSetId(ChangeNotesCommit commit) throws ConfigInvalidException {
    String psIdLine = parseExactlyOneFooter(commit, FOOTER_PATCH_SET);
    int s = psIdLine.indexOf(' ');
    String psIdStr = s < 0 ? psIdLine : psIdLine.substring(0, s);
    Integer psId = Ints.tryParse(psIdStr);
    if (psId == null) {
      throw invalidFooter(FOOTER_PATCH_SET, psIdStr);
    }
    return PatchSet.id(id, psId);
  }

  @Nullable
  private PatchSetState parsePatchSetState(ChangeNotesCommit commit) throws ConfigInvalidException {
    String psIdLine = parseExactlyOneFooter(commit, FOOTER_PATCH_SET);
    int s = psIdLine.indexOf(' ');
    if (s < 0) {
      return null;
    }
    String withParens = psIdLine.substring(s + 1);
    if (withParens.startsWith("(") && withParens.endsWith(")")) {
      PatchSetState state =
          Enums.getIfPresent(
                  PatchSetState.class,
                  withParens.substring(1, withParens.length() - 1).toUpperCase(Locale.US))
              .orNull();
      if (state != null) {
        return state;
      }
    }
    throw invalidFooter(FOOTER_PATCH_SET, psIdLine);
  }

  private void parseDescription(PatchSet.Id psId, ChangeNotesCommit commit)
      throws ConfigInvalidException {
    List<String> descLines = commit.getFooterLineValues(FOOTER_PATCH_SET_DESCRIPTION);
    if (descLines.isEmpty()) {
      return;
    }

    checkPatchSetCommitNotParsed(psId, FOOTER_PATCH_SET_DESCRIPTION);
    if (descLines.size() == 1) {
      String desc = descLines.get(0).trim();
      PatchSet.Builder pending = patchSets.computeIfAbsent(psId, p -> PatchSet.builder());
      if (!pending.description().isPresent()) {
        pending.description(Optional.of(desc));
      }
    } else {
      throw expectedOneFooter(FOOTER_PATCH_SET_DESCRIPTION, descLines);
    }
  }

  private boolean parseChangeMessage(
      PatchSet.Id psId,
      Account.Id accountId,
      Account.Id realAccountId,
      ChangeNotesCommit commit,
      Instant ts) {
    Optional<String> changeMsgString = getChangeMessageString(commit);
    if (!changeMsgString.isPresent()) {
      return false;
    }

    ChangeMessage changeMessage =
        ChangeMessage.create(
            ChangeMessage.key(psId.changeId(), commit.name()),
            accountId,
            ts,
            psId,
            changeMsgString.get(),
            realAccountId,
            tag);
    return allChangeMessages.add(changeMessage);
  }

  public static Optional<String> getChangeMessageString(ChangeNotesCommit commit) {
    byte[] raw = commit.getRawBuffer();
    Charset enc = RawParseUtils.parseEncoding(raw);

    Optional<ChangeNoteUtil.CommitMessageRange> range = parseCommitMessageRange(commit);
    return range.map(
        commitMessageRange ->
            commitMessageRange.hasChangeMessage()
                ? RawParseUtils.decode(
                    enc,
                    raw,
                    commitMessageRange.changeMessageStart(),
                    commitMessageRange.changeMessageEnd() + 1)
                : null);
  }

  private void parseNotes() throws IOException, ConfigInvalidException {
    ObjectReader reader = walk.getObjectReader();
    ChangeNotesCommit tipCommit = walk.parseCommit(tip);
    revisionNoteMap =
        RevisionNoteMap.parse(
            changeNoteJson, reader, NoteMap.read(reader, tipCommit), HumanComment.Status.PUBLISHED);
    Map<ObjectId, ChangeRevisionNote> rns = revisionNoteMap.revisionNotes;

    for (Map.Entry<ObjectId, ChangeRevisionNote> e : rns.entrySet()) {
      for (HumanComment c : e.getValue().getEntities()) {

        noteDbUtil
            .parseIdent(String.format("%s@%s", c.author.getId(), c.serverId))
            .ifPresent(id -> c.author = new Comment.Identity(id));

        humanComments.put(e.getKey(), c);
      }
    }

    // Lookup submit requirement results from the revision notes of the last PS that has stored
    // submit requirements. This is important for cases where the change was abandoned/un-abandoned
    // multiple times. With each abandon, we store submit requirement results in NoteDb, so we can
    // end up having stored SRs in many revision notes. We should only return SRs from the last
    // PS of them.
    for (PatchSet.Builder ps :
        patchSets.values().stream()
            .sorted(comparingInt((PatchSet.Builder p) -> p.id().get()).reversed())
            .collect(Collectors.toList())) {
      Optional<ObjectId> maybePsCommitId = ps.commitId();
      if (!maybePsCommitId.isPresent()) {
        continue;
      }
      ObjectId psCommitId = maybePsCommitId.get();
      if (rns.containsKey(psCommitId)
          && rns.get(psCommitId).getSubmitRequirementsResult() != null) {
        rns.get(psCommitId)
            .getSubmitRequirementsResult()
            .forEach(sr -> submitRequirementResults.add(sr));
        break;
      }
    }

    for (PatchSet.Builder b : patchSets.values()) {
      ObjectId commitId =
          b.commitId()
              .orElseThrow(
                  () ->
                      new IllegalStateException("never parsed commit ID for patch set " + b.id()));
      ChangeRevisionNote rn = rns.get(commitId);
      if (rn != null && rn.getPushCert() != null) {
        b.pushCertificate(Optional.of(rn.getPushCert()));
      }
    }
  }

  /** Parses copied {@link PatchSetApproval}. */
  private void parseCopiedApproval(PatchSet.Id psId, Instant ts, String line)
      throws ConfigInvalidException {
    ParsedPatchSetApproval parsedPatchSetApproval =
        ChangeNotesParseApprovalUtil.parseCopiedApproval(line);
    checkFooter(
        parsedPatchSetApproval.accountIdent().isPresent(),
        FOOTER_COPIED_LABEL,
        parsedPatchSetApproval.footerLine());
    PersonIdent accountIdent =
        RawParseUtils.parsePersonIdent(parsedPatchSetApproval.accountIdent().get());

    checkFooter(accountIdent != null, FOOTER_COPIED_LABEL, parsedPatchSetApproval.footerLine());
    Account.Id accountId = parseIdent(accountIdent);

    Account.Id realAccountId = null;
    if (parsedPatchSetApproval.realAccountIdent().isPresent()) {
      PersonIdent realIdent =
          RawParseUtils.parsePersonIdent(parsedPatchSetApproval.realAccountIdent().get());
      checkFooter(realIdent != null, FOOTER_COPIED_LABEL, parsedPatchSetApproval.footerLine());
      realAccountId = parseIdent(realIdent);
    }

    LabelVote labelVote;
    try {
      if (!parsedPatchSetApproval.isRemoval()) {
        labelVote = LabelVote.parseWithEquals(parsedPatchSetApproval.labelVote());
      } else {
        String labelName = parsedPatchSetApproval.labelVote();
        LabelType.checkNameInternal(labelName);
        labelVote = LabelVote.create(labelName, (short) 0);
      }
    } catch (IllegalArgumentException e) {
      ConfigInvalidException pe =
          parseException(
              "invalid %s: %s", FOOTER_COPIED_LABEL, parsedPatchSetApproval.footerLine());
      pe.initCause(e);
      throw pe;
    }

    PatchSetApproval.Builder psa =
        PatchSetApproval.builder()
            .key(PatchSetApproval.key(psId, accountId, LabelId.create(labelVote.label())))
            .uuid(parsedPatchSetApproval.uuid().map(PatchSetApproval::uuid))
            .value(labelVote.value())
            .granted(ts)
            .tag(parsedPatchSetApproval.tag())
            .copied(true);
    if (realAccountId != null) {
      psa.realAccountId(realAccountId);
    }
    approvals.putIfAbsent(psa.key(), psa);
    bufferedApprovals.add(psa);
  }

  private void parseApproval(
      PatchSet.Id psId, Account.Id accountId, Account.Id realAccountId, Instant ts, String line)
      throws ConfigInvalidException {
    if (accountId == null) {
      throw parseException("patch set %s requires an identified user as uploader", psId.get());
    }
    PatchSetApproval.Builder psa;
    ParsedPatchSetApproval parsedPatchSetApproval =
        ChangeNotesParseApprovalUtil.parseApproval(line);
    if (line.startsWith("-")) {
      psa = parseRemoveApproval(psId, accountId, realAccountId, ts, parsedPatchSetApproval);
    } else {
      psa = parseAddApproval(psId, accountId, realAccountId, ts, parsedPatchSetApproval);
    }
    bufferedApprovals.add(psa);
  }

  /** Parses {@link PatchSetApproval} out of the {@link ChangeNoteFooters#FOOTER_LABEL} value. */
  private PatchSetApproval.Builder parseAddApproval(
      PatchSet.Id psId,
      Account.Id committerId,
      Account.Id realAccountId,
      Instant ts,
      ParsedPatchSetApproval parsedPatchSetApproval)
      throws ConfigInvalidException {

    Account.Id approverId = parseApprover(committerId, parsedPatchSetApproval);

    LabelVote l;
    try {
      l = LabelVote.parseWithEquals(parsedPatchSetApproval.labelVote());
    } catch (IllegalArgumentException e) {
      ConfigInvalidException pe =
          parseException("invalid %s: %s", FOOTER_LABEL, parsedPatchSetApproval.footerLine());
      pe.initCause(e);
      throw pe;
    }

    PatchSetApproval.Builder psa =
        PatchSetApproval.builder()
            .key(PatchSetApproval.key(psId, approverId, LabelId.create(l.label())))
            .uuid(parsedPatchSetApproval.uuid().map(PatchSetApproval::uuid))
            .value(l.value())
            .granted(ts)
            .tag(Optional.ofNullable(tag));
    if (!Objects.equals(realAccountId, committerId)) {
      psa.realAccountId(realAccountId);
    }
    approvals.putIfAbsent(psa.key(), psa);
    return psa;
  }

  private PatchSetApproval.Builder parseRemoveApproval(
      PatchSet.Id psId,
      Account.Id committerId,
      Account.Id realAccountId,
      Instant ts,
      ParsedPatchSetApproval parsedPatchSetApproval)
      throws ConfigInvalidException {

    checkFooter(
        parsedPatchSetApproval.footerLine().startsWith("-"),
        FOOTER_LABEL,
        parsedPatchSetApproval.footerLine());
    Account.Id approverId = parseApprover(committerId, parsedPatchSetApproval);

    try {
      LabelType.checkNameInternal(parsedPatchSetApproval.labelVote());
    } catch (IllegalArgumentException e) {
      ConfigInvalidException pe =
          parseException("invalid %s: %s", FOOTER_LABEL, parsedPatchSetApproval.footerLine());
      pe.initCause(e);
      throw pe;
    }

    // Store an actual 0-vote approval in the map for a removed approval, because ApprovalCopier
    // needs an actual approval in order to block copying an earlier approval over a later delete.
    PatchSetApproval.Builder remove =
        PatchSetApproval.builder()
            .key(
                PatchSetApproval.key(
                    psId, approverId, LabelId.create(parsedPatchSetApproval.labelVote())))
            .value(0)
            .granted(ts);
    if (!Objects.equals(realAccountId, committerId)) {
      remove.realAccountId(realAccountId);
    }
    approvals.putIfAbsent(remove.key(), remove);
    return remove;
  }

  /**
   * Identifies the {@link com.google.gerrit.entities.Account.Id} that issued the vote.
   *
   * <p>There are potentially 3 accounts involved here: 1. The account from the commit, which is the
   * effective IdentifiedUser that produced the update. 2. The account in the label footer itself,
   * which is used during submit to copy other users' labels to a new patch set. 3. The account in
   * the Real-user footer, indicating that the whole update operation was executed by this user on
   * behalf of the effective user.
   */
  private Account.Id parseApprover(
      Account.Id committerId, ParsedPatchSetApproval parsedPatchSetApproval)
      throws ConfigInvalidException {
    Account.Id effectiveAccountId;
    if (parsedPatchSetApproval.accountIdent().isPresent()) {
      PersonIdent ident =
          RawParseUtils.parsePersonIdent(parsedPatchSetApproval.accountIdent().get());
      checkFooter(ident != null, FOOTER_LABEL, parsedPatchSetApproval.footerLine());
      effectiveAccountId = parseIdent(ident);
    } else {
      effectiveAccountId = committerId;
    }
    return effectiveAccountId;
  }

  private void parseSubmitRecords(List<String> lines) throws ConfigInvalidException {
    SubmitRecord rec = null;

    for (String line : lines) {
      int c = line.indexOf(": ");
      if (c < 0) {
        rec = new SubmitRecord();
        submitRecords.add(rec);
        int s = line.indexOf(' ');
        String statusStr = s >= 0 ? line.substring(0, s) : line;
        rec.status = Enums.getIfPresent(SubmitRecord.Status.class, statusStr).orNull();
        checkFooter(rec.status != null, FOOTER_SUBMITTED_WITH, line);
        if (s >= 0) {
          rec.errorMessage = line.substring(s);
        }
      } else {
        checkFooter(rec != null, FOOTER_SUBMITTED_WITH, line);
        if (line.startsWith("Rule-Name: ")) {
          String ruleName = RULE_SPLITTER.splitToList(line).get(1);
          rec.ruleName = ruleName;
          continue;
        }
        SubmitRecord.Label label = new SubmitRecord.Label();
        if (rec.labels == null) {
          rec.labels = new ArrayList<>();
        }
        rec.labels.add(label);

        label.status =
            Enums.getIfPresent(SubmitRecord.Label.Status.class, line.substring(0, c)).orNull();
        checkFooter(label.status != null, FOOTER_SUBMITTED_WITH, line);
        int c2 = line.indexOf(": ", c + 2);
        if (c2 >= 0) {
          label.label = line.substring(c + 2, c2);
          PersonIdent ident = RawParseUtils.parsePersonIdent(line.substring(c2 + 2));
          checkFooter(ident != null, FOOTER_SUBMITTED_WITH, line);
          label.appliedBy = parseIdent(ident);
        } else {
          label.label = line.substring(c + 2);
        }
      }
    }
  }

  @Nullable
  private Account.Id parseIdent(ChangeNotesCommit commit) throws ConfigInvalidException {
    // Check if the author name/email is the same as the committer name/email,
    // i.e. was the server ident at the time this commit was made.
    PersonIdent a = commit.getAuthorIdent();
    PersonIdent c = commit.getCommitterIdent();
    if (a.getName().equals(c.getName()) && a.getEmailAddress().equals(c.getEmailAddress())) {
      return null;
    }
    return parseIdent(a);
  }

  private void parseReviewer(Instant ts, ReviewerStateInternal state, String line)
      throws ConfigInvalidException {
    PersonIdent ident = RawParseUtils.parsePersonIdent(line);
    if (ident == null) {
      throw invalidFooter(state.getFooterKey(), line);
    }
    Account.Id accountId = parseIdent(ident);
    reviewerUpdates.add(ReviewerStatusUpdate.create(ts, ownerId, accountId, state));
    if (!reviewers.containsRow(accountId)) {
      reviewers.put(accountId, state, ts);
    }
  }

  private void parseReviewerByEmail(Instant ts, ReviewerStateInternal state, String line)
      throws ConfigInvalidException {
    Address adr;
    try {
      adr = Address.parse(line);
    } catch (IllegalArgumentException e) {
      ConfigInvalidException cie = invalidFooter(state.getByEmailFooterKey(), line);
      cie.initCause(e);
      throw cie;
    }
    if (!reviewersByEmail.containsRow(adr)) {
      reviewersByEmail.put(adr, state, ts);
    }
  }

  private void parseIsPrivate(ChangeNotesCommit commit) throws ConfigInvalidException {
    String raw = parseOneFooter(commit, FOOTER_PRIVATE);
    if (raw == null) {
      return;
    } else if (Boolean.TRUE.toString().equalsIgnoreCase(raw)) {
      isPrivate = true;
      return;
    } else if (Boolean.FALSE.toString().equalsIgnoreCase(raw)) {
      isPrivate = false;
      return;
    }
    throw invalidFooter(FOOTER_PRIVATE, raw);
  }

  private void parseWorkInProgress(ChangeNotesCommit commit) throws ConfigInvalidException {
    String raw = parseOneFooter(commit, FOOTER_WORK_IN_PROGRESS);
    if (raw == null) {
      // No change to WIP state in this revision.
      previousWorkInProgressFooter = null;
      return;
    } else if (Boolean.TRUE.toString().equalsIgnoreCase(raw)) {
      // This revision moves the change into WIP.
      previousWorkInProgressFooter = true;
      if (workInProgress == null) {
        // Because this is the first time workInProgress is being set, we know
        // that this change's current state is WIP. All the reviewer updates
        // we've seen so far are pending, so take a snapshot of the reviewers
        // and reviewersByEmail tables.
        pendingReviewers =
            ReviewerSet.fromTable(Tables.transpose(ImmutableTable.copyOf(reviewers)));
        pendingReviewersByEmail =
            ReviewerByEmailSet.fromTable(Tables.transpose(ImmutableTable.copyOf(reviewersByEmail)));
        workInProgress = true;
      }
      return;
    } else if (Boolean.FALSE.toString().equalsIgnoreCase(raw)) {
      previousWorkInProgressFooter = false;
      hasReviewStarted = true;
      if (workInProgress == null) {
        workInProgress = false;
      }
      return;
    }
    throw invalidFooter(FOOTER_WORK_IN_PROGRESS, raw);
  }

  @Nullable
  private Change.Id parseRevertOf(ChangeNotesCommit commit) throws ConfigInvalidException {
    String footer = parseOneFooter(commit, FOOTER_REVERT_OF);
    if (footer == null) {
      return null;
    }
    Integer revertOf = Ints.tryParse(footer);
    if (revertOf == null) {
      throw invalidFooter(FOOTER_REVERT_OF, footer);
    }
    return Change.id(revertOf);
  }

  /**
   * Parses {@link ChangeNoteFooters#FOOTER_CHERRY_PICK_OF} of the commit.
   *
   * @param commit the commit to parse.
   * @return {@link Optional} value of the parsed footer or {@code null} if the footer is missing in
   *     this commit.
   * @throws ConfigInvalidException if the footer value could not be parsed as a valid {@link
   *     com.google.gerrit.entities.PatchSet.Id}.
   */
  @Nullable
  private Optional<PatchSet.Id> parseCherryPickOf(ChangeNotesCommit commit)
      throws ConfigInvalidException {
    String footer = parseOneFooter(commit, FOOTER_CHERRY_PICK_OF);
    if (footer == null) {
      // The footer is missing, nothing to parse.
      return null;
    } else if (footer.equals("")) {
      // Empty footer value, cherryPickOf was unset at this commit.
      return Optional.empty();
    } else {
      try {
        return Optional.of(PatchSet.Id.parse(footer));
      } catch (IllegalArgumentException e) {
        throw new ConfigInvalidException("\"" + footer + "\" is not a valid patchset", e);
      }
    }
  }

  /**
   * Returns the {@link Timestamp} when the commit was applied.
   *
   * <p>The author's date only notes when the commit was originally made. Thus, use the commiter's
   * date as it accounts for the rebase, cherry-pick, commit --amend and other commands that rewrite
   * the history of the branch.
   *
   * <p>Don't use {@link org.eclipse.jgit.revwalk.RevCommit#getCommitTime} directly because it
   * returns int and would overflow.
   *
   * @param commit the commit to return commit time.
   * @return the timestamp when the commit was applied.
   */
  private Instant getCommitTimestamp(ChangeNotesCommit commit) {
    return commit.getCommitterIdent().getWhenAsInstant();
  }

  private void pruneReviewers() {
    Iterator<Table.Cell<Account.Id, ReviewerStateInternal, Instant>> rit =
        reviewers.cellSet().iterator();
    while (rit.hasNext()) {
      Table.Cell<Account.Id, ReviewerStateInternal, Instant> e = rit.next();
      if (e.getColumnKey() == ReviewerStateInternal.REMOVED) {
        rit.remove();
      }
    }
  }

  private void pruneReviewersByEmail() {
    Iterator<Table.Cell<Address, ReviewerStateInternal, Instant>> rit =
        reviewersByEmail.cellSet().iterator();
    while (rit.hasNext()) {
      Table.Cell<Address, ReviewerStateInternal, Instant> e = rit.next();
      if (e.getColumnKey() == ReviewerStateInternal.REMOVED) {
        rit.remove();
      }
    }
  }

  private void updatePatchSetStates() {
    Set<PatchSet.Id> missing = new TreeSet<>(comparing(PatchSet.Id::get));
    patchSets.keySet().stream().filter(p -> !patchSetCommitParsed(p)).forEach(p -> missing.add(p));

    for (Map.Entry<PatchSet.Id, PatchSetState> e : patchSetStates.entrySet()) {
      switch (e.getValue()) {
        case PUBLISHED:
        default:
          break;

        case DELETED:
          patchSets.remove(e.getKey());
          break;
      }
    }

    // Post-process other collections to remove items corresponding to deleted
    // (or otherwise missing) patch sets. This is safer than trying to prevent
    // insertion, as it will also filter out items racily added after the patch
    // set was deleted.
    int pruned =
        pruneEntitiesForMissingPatchSets(allChangeMessages, ChangeMessage::getPatchSetId, missing);
    pruned +=
        pruneEntitiesForMissingPatchSets(
            humanComments.values(), c -> PatchSet.id(id, c.key.patchSetId), missing);
    pruned +=
        pruneEntitiesForMissingPatchSets(
            approvals.values(), psa -> psa.key().patchSetId(), missing);

    if (!missing.isEmpty()) {
      logger.atWarning().log(
          "ignoring %s additional entities due to missing patch sets: %s", pruned, missing);
    }
  }

  private <T> int pruneEntitiesForMissingPatchSets(
      Iterable<T> ents, Function<T, PatchSet.Id> psIdFunc, Set<PatchSet.Id> missing) {
    int pruned = 0;
    for (Iterator<T> it = ents.iterator(); it.hasNext(); ) {
      PatchSet.Id psId = psIdFunc.apply(it.next());
      if (!patchSetCommitParsed(psId)) {
        pruned++;
        missing.add(psId);
        it.remove();
      } else if (deletedPatchSets.contains(psId)) {
        it.remove(); // Not an error we need to report, don't increment pruned.
      }
    }
    return pruned;
  }

  private void checkMandatoryFooters() throws ConfigInvalidException {
    List<FooterKey> missing = new ArrayList<>();
    if (branch == null) {
      missing.add(FOOTER_BRANCH);
    }
    if (changeId == null) {
      missing.add(FOOTER_CHANGE_ID);
    }
    if (originalSubject == null || subject == null) {
      missing.add(FOOTER_SUBJECT);
    }
    if (!missing.isEmpty()) {
      throw parseException(
          "%s",
          "Missing footers: " + missing.stream().map(FooterKey::getName).collect(joining(", ")));
    }
  }

  private ConfigInvalidException expectedOneFooter(FooterKey footer, List<String> actual) {
    return parseException("missing or multiple %s: %s", footer.getName(), actual);
  }

  private ConfigInvalidException invalidFooter(FooterKey footer, String actual) {
    return parseException("invalid %s: %s", footer.getName(), actual);
  }

  private void checkFooter(boolean expr, FooterKey footer, String actual)
      throws ConfigInvalidException {
    if (!expr) {
      throw invalidFooter(footer, actual);
    }
  }

  private void checkPatchSetCommitNotParsed(PatchSet.Id psId, FooterKey footer)
      throws ConfigInvalidException {
    if (patchSetCommitParsed(psId)) {
      throw parseException(
          "%s field found for patch set %s before patch set was originally defined",
          footer.getName(), psId.get());
    }
  }

  private boolean patchSetCommitParsed(PatchSet.Id psId) {
    PatchSet.Builder pending = patchSets.get(psId);
    return pending != null && pending.commitId().isPresent();
  }

  @FormatMethod
  private ConfigInvalidException parseException(String fmt, Object... args) {
    return ChangeNotes.parseException(id, fmt, args);
  }

  private Account.Id parseIdent(PersonIdent ident) throws ConfigInvalidException {
    return noteDbUtil
        .parseIdent(ident)
        .orElseThrow(
            () -> parseException("cannot retrieve account id: %s", ident.getEmailAddress()));
  }

  protected boolean countTowardsMaxUpdatesLimit(
      ChangeNotesCommit commit, boolean hasChangeMessage) {
    return !commit.isAttentionSetCommitOnly(hasChangeMessage);
  }
}
