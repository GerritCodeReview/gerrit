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

import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_ASSIGNEE;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_BRANCH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CHANGE_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_COMMIT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CURRENT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_GROUPS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET_DESCRIPTION;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PRIVATE;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_READ_ONLY_UNTIL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_REAL_USER;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBJECT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMISSION_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TAG;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TOPIC;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_WORK_IN_PROGRESS;
import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.base.Enums;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.gerrit.server.util.LabelVote;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.text.ParseException;
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
import java.util.TreeSet;
import java.util.function.Function;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChangeNotesParser {
  private static final Logger log = LoggerFactory.getLogger(ChangeNotesParser.class);

  // Sentinel RevId indicating a mutable field on a patch set was parsed, but
  // the parser does not yet know its commit SHA-1.
  private static final RevId PARTIAL_PATCH_SET = new RevId("INVALID PARTIAL PATCH SET");

  @AutoValue
  abstract static class ApprovalKey {
    abstract PatchSet.Id psId();

    abstract Account.Id accountId();

    abstract String label();

    private static ApprovalKey create(PatchSet.Id psId, Account.Id accountId, String label) {
      return new AutoValue_ChangeNotesParser_ApprovalKey(psId, accountId, label);
    }
  }

  // Private final members initialized in the constructor.
  private final ChangeNoteUtil noteUtil;
  private final NoteDbMetrics metrics;
  private final Change.Id id;
  private final ObjectId tip;
  private final ChangeNotesRevWalk walk;

  // Private final but mutable members initialized in the constructor and filled
  // in during the parsing process.
  private final Table<Account.Id, ReviewerStateInternal, Timestamp> reviewers;
  private final Table<Address, ReviewerStateInternal, Timestamp> reviewersByEmail;
  private final List<Account.Id> allPastReviewers;
  private final List<ReviewerStatusUpdate> reviewerUpdates;
  private final List<SubmitRecord> submitRecords;
  private final ListMultimap<RevId, Comment> comments;
  private final Map<PatchSet.Id, PatchSet> patchSets;
  private final Set<PatchSet.Id> deletedPatchSets;
  private final Map<PatchSet.Id, PatchSetState> patchSetStates;
  private final List<PatchSet.Id> currentPatchSets;
  private final Map<ApprovalKey, PatchSetApproval> approvals;
  private final List<PatchSetApproval> bufferedApprovals;
  private final List<ChangeMessage> allChangeMessages;
  private final ListMultimap<PatchSet.Id, ChangeMessage> changeMessagesByPatchSet;

  // Non-final private members filled in during the parsing process.
  private String branch;
  private Change.Status status;
  private String topic;
  private Optional<Account.Id> assignee;
  private List<Account.Id> pastAssignees;
  private Set<String> hashtags;
  private Timestamp createdOn;
  private Timestamp lastUpdatedOn;
  private Account.Id ownerId;
  private String changeId;
  private String subject;
  private String originalSubject;
  private String submissionId;
  private String tag;
  private RevisionNoteMap<ChangeRevisionNote> revisionNoteMap;
  private Timestamp readOnlyUntil;
  private Boolean isPrivate;
  private Boolean workInProgress;
  private Boolean previousWorkInProgressFooter;
  private Boolean hasReviewStarted;
  private ReviewerSet pendingReviewers;
  private ReviewerByEmailSet pendingReviewersByEmail;

  ChangeNotesParser(
      Change.Id changeId,
      ObjectId tip,
      ChangeNotesRevWalk walk,
      ChangeNoteUtil noteUtil,
      NoteDbMetrics metrics) {
    this.id = changeId;
    this.tip = tip;
    this.walk = walk;
    this.noteUtil = noteUtil;
    this.metrics = metrics;
    approvals = new LinkedHashMap<>();
    bufferedApprovals = new ArrayList<>();
    reviewers = HashBasedTable.create();
    reviewersByEmail = HashBasedTable.create();
    pendingReviewers = ReviewerSet.empty();
    pendingReviewersByEmail = ReviewerByEmailSet.empty();
    allPastReviewers = new ArrayList<>();
    reviewerUpdates = new ArrayList<>();
    submitRecords = Lists.newArrayListWithExpectedSize(1);
    allChangeMessages = new ArrayList<>();
    changeMessagesByPatchSet = LinkedListMultimap.create();
    comments = MultimapBuilder.hashKeys().arrayListValues().build();
    patchSets = new HashMap<>();
    deletedPatchSets = new HashSet<>();
    patchSetStates = new HashMap<>();
    currentPatchSets = new ArrayList<>();
  }

  ChangeNotesState parseAll() throws ConfigInvalidException, IOException {
    // Don't include initial parse in timer, as this might do more I/O to page
    // in the block containing most commits. Later reads are not guaranteed to
    // avoid I/O, but often should.
    walk.reset();
    walk.markStart(walk.parseCommit(tip));

    try (Timer1.Context timer = metrics.parseLatency.start(CHANGES)) {
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

    return buildState();
  }

  RevisionNoteMap<ChangeRevisionNote> getRevisionNoteMap() {
    return revisionNoteMap;
  }

  private ChangeNotesState buildState() {
    return ChangeNotesState.create(
        tip.copy(),
        id,
        new Change.Key(changeId),
        createdOn,
        lastUpdatedOn,
        ownerId,
        branch,
        buildCurrentPatchSetId(),
        subject,
        topic,
        originalSubject,
        submissionId,
        assignee != null ? assignee.orElse(null) : null,
        status,
        Sets.newLinkedHashSet(Lists.reverse(pastAssignees)),
        hashtags,
        patchSets,
        buildApprovals(),
        ReviewerSet.fromTable(Tables.transpose(reviewers)),
        ReviewerByEmailSet.fromTable(Tables.transpose(reviewersByEmail)),
        pendingReviewers,
        pendingReviewersByEmail,
        allPastReviewers,
        buildReviewerUpdates(),
        submitRecords,
        buildAllMessages(),
        buildMessagesByPatchSet(),
        comments,
        readOnlyUntil,
        isPrivate,
        workInProgress,
        hasReviewStarted);
  }

  private PatchSet.Id buildCurrentPatchSetId() {
    // currentPatchSets are in parse order, i.e. newest first. Pick the first
    // patch set that was marked as current, excluding deleted patch sets.
    for (PatchSet.Id psId : currentPatchSets) {
      if (patchSets.containsKey(psId)) {
        return psId;
      }
    }
    return null;
  }

  private ListMultimap<PatchSet.Id, PatchSetApproval> buildApprovals() {
    ListMultimap<PatchSet.Id, PatchSetApproval> result =
        MultimapBuilder.hashKeys().arrayListValues().build();
    for (PatchSetApproval a : approvals.values()) {
      if (!patchSets.containsKey(a.getPatchSetId())) {
        continue; // Patch set deleted or missing.
      } else if (allPastReviewers.contains(a.getAccountId())
          && !reviewers.containsRow(a.getAccountId())) {
        continue; // Reviewer was explicitly removed.
      }
      result.put(a.getPatchSetId(), a);
    }
    for (Collection<PatchSetApproval> v : result.asMap().values()) {
      Collections.sort((List<PatchSetApproval>) v, ChangeNotes.PSA_BY_TIME);
    }
    return result;
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

  private ListMultimap<PatchSet.Id, ChangeMessage> buildMessagesByPatchSet() {
    for (Collection<ChangeMessage> v : changeMessagesByPatchSet.asMap().values()) {
      Collections.reverse((List<ChangeMessage>) v);
    }
    return changeMessagesByPatchSet;
  }

  private void parse(ChangeNotesCommit commit) throws ConfigInvalidException {
    Timestamp ts = new Timestamp(commit.getCommitterIdent().getWhen().getTime());

    createdOn = ts;
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

    parseChangeMessage(psId, accountId, realAccountId, commit, ts);
    if (topic == null) {
      topic = parseTopic(commit);
    }

    parseHashtags(commit);
    parseAssignee(commit);

    if (submissionId == null) {
      submissionId = parseSubmissionId(commit);
    }

    ObjectId currRev = parseRevision(commit);
    if (currRev != null) {
      parsePatchSet(psId, currRev, accountId, ts);
    }
    parseGroups(psId, commit);
    parseCurrentPatchSet(psId, commit);

    if (submitRecords.isEmpty()) {
      // Only parse the most recent set of submit records; any older ones are
      // still there, but not currently used.
      parseSubmitRecords(commit.getFooterLineValues(FOOTER_SUBMITTED_WITH));
    }

    if (status == null) {
      status = parseStatus(commit);
    }

    // Parse approvals after status to treat approvals in the same commit as
    // "Status: merged" as non-post-submit.
    for (String line : commit.getFooterLineValues(FOOTER_LABEL)) {
      parseApproval(psId, accountId, realAccountId, ts, line);
    }

    for (ReviewerStateInternal state : ReviewerStateInternal.values()) {
      for (String line : commit.getFooterLineValues(state.getFooterKey())) {
        parseReviewer(ts, state, line);
      }
      for (String line : commit.getFooterLineValues(state.getByEmailFooterKey())) {
        parseReviewerByEmail(ts, state, line);
      }
      // Don't update timestamp when a reviewer was added, matching RevewDb
      // behavior.
    }

    if (readOnlyUntil == null) {
      parseReadOnlyUntil(commit);
    }

    if (isPrivate == null) {
      parseIsPrivate(commit);
    }

    previousWorkInProgressFooter = null;
    parseWorkInProgress(commit);

    if (lastUpdatedOn == null || ts.after(lastUpdatedOn)) {
      lastUpdatedOn = ts;
    }

    parseDescription(psId, commit);
  }

  private String parseSubmissionId(ChangeNotesCommit commit) throws ConfigInvalidException {
    return parseOneFooter(commit, FOOTER_SUBMISSION_ID);
  }

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
    return noteUtil.parseIdent(ident, id);
  }

  private String parseTopic(ChangeNotesCommit commit) throws ConfigInvalidException {
    return parseOneFooter(commit, FOOTER_TOPIC);
  }

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
      throw expectedOneFooter(footerKey, Collections.<String>emptyList());
    }
    return line;
  }

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

  private void parsePatchSet(PatchSet.Id psId, ObjectId rev, Account.Id accountId, Timestamp ts)
      throws ConfigInvalidException {
    if (accountId == null) {
      throw parseException("patch set %s requires an identified user as uploader", psId.get());
    }
    PatchSet ps = patchSets.get(psId);
    if (ps == null) {
      ps = new PatchSet(psId);
      patchSets.put(psId, ps);
    } else if (!ps.getRevision().equals(PARTIAL_PATCH_SET)) {
      if (deletedPatchSets.contains(psId)) {
        // Do not update PS details as PS was deleted and this meta data is of
        // no relevance
        return;
      }
      throw new ConfigInvalidException(
          String.format(
              "Multiple revisions parsed for patch set %s: %s and %s",
              psId.get(), patchSets.get(psId).getRevision(), rev.name()));
    }
    ps.setRevision(new RevId(rev.name()));
    ps.setUploader(accountId);
    ps.setCreatedOn(ts);
  }

  private void parseGroups(PatchSet.Id psId, ChangeNotesCommit commit)
      throws ConfigInvalidException {
    String groupsStr = parseOneFooter(commit, FOOTER_GROUPS);
    if (groupsStr == null) {
      return;
    }
    PatchSet ps = patchSets.get(psId);
    if (ps == null) {
      ps = new PatchSet(psId);
      ps.setRevision(PARTIAL_PATCH_SET);
      patchSets.put(psId, ps);
    } else if (!ps.getGroups().isEmpty()) {
      return;
    }
    ps.setGroups(PatchSet.splitGroups(groupsStr));
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
      hashtags = Sets.newHashSet(Splitter.on(',').split(hashtagsLines.get(0)));
    }
  }

  private void parseAssignee(ChangeNotesCommit commit) throws ConfigInvalidException {
    if (pastAssignees == null) {
      pastAssignees = Lists.newArrayList();
    }
    String assigneeValue = parseOneFooter(commit, FOOTER_ASSIGNEE);
    if (assigneeValue != null) {
      Optional<Account.Id> parsedAssignee;
      if (assigneeValue.equals("")) {
        // Empty footer found, assignee deleted
        parsedAssignee = Optional.empty();
      } else {
        PersonIdent ident = RawParseUtils.parsePersonIdent(assigneeValue);
        parsedAssignee = Optional.ofNullable(noteUtil.parseIdent(ident, id));
      }
      if (assignee == null) {
        assignee = parsedAssignee;
      }
      if (parsedAssignee.isPresent()) {
        pastAssignees.add(parsedAssignee.get());
      }
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

  private Change.Status parseStatus(ChangeNotesCommit commit) throws ConfigInvalidException {
    List<String> statusLines = commit.getFooterLineValues(FOOTER_STATUS);
    if (statusLines.isEmpty()) {
      return null;
    } else if (statusLines.size() > 1) {
      throw expectedOneFooter(FOOTER_STATUS, statusLines);
    }
    Change.Status status =
        Enums.getIfPresent(Change.Status.class, statusLines.get(0).toUpperCase()).orNull();
    if (status == null) {
      throw invalidFooter(FOOTER_STATUS, statusLines.get(0));
    }
    // All approvals after MERGED and before the next status change get the postSubmit
    // bit. (Currently the state can't change from MERGED to something else, but just in case.) The
    // exception is the legacy SUBM approval, which is never considered post-submit, but might end
    // up sorted after the submit during rebuilding.
    if (status == Change.Status.MERGED) {
      for (PatchSetApproval psa : bufferedApprovals) {
        if (!psa.isLegacySubmit()) {
          psa.setPostSubmit(true);
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
    return new PatchSet.Id(id, psId);
  }

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
                  withParens.substring(1, withParens.length() - 1).toUpperCase())
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
    } else if (descLines.size() == 1) {
      String desc = descLines.get(0).trim();
      PatchSet ps = patchSets.get(psId);
      if (ps == null) {
        ps = new PatchSet(psId);
        ps.setRevision(PARTIAL_PATCH_SET);
        patchSets.put(psId, ps);
      }
      if (ps.getDescription() == null) {
        ps.setDescription(desc);
      }
    } else {
      throw expectedOneFooter(FOOTER_PATCH_SET_DESCRIPTION, descLines);
    }
  }

  private void parseChangeMessage(
      PatchSet.Id psId,
      Account.Id accountId,
      Account.Id realAccountId,
      ChangeNotesCommit commit,
      Timestamp ts) {
    byte[] raw = commit.getRawBuffer();
    int size = raw.length;
    Charset enc = RawParseUtils.parseEncoding(raw);

    int subjectStart = RawParseUtils.commitMessage(raw, 0);
    if (subjectStart < 0 || subjectStart >= size) {
      return;
    }

    int subjectEnd = RawParseUtils.endOfParagraph(raw, subjectStart);
    if (subjectEnd == size) {
      return;
    }

    int changeMessageStart;

    if (raw[subjectEnd] == '\n') {
      changeMessageStart = subjectEnd + 2; //\n\n ends paragraph
    } else if (raw[subjectEnd] == '\r') {
      changeMessageStart = subjectEnd + 4; //\r\n\r\n ends paragraph
    } else {
      return;
    }

    int ptr = size - 1;
    int changeMessageEnd = -1;
    while (ptr > changeMessageStart) {
      ptr = RawParseUtils.prevLF(raw, ptr, '\r');
      if (ptr == -1) {
        break;
      }
      if (raw[ptr] == '\n') {
        changeMessageEnd = ptr - 1;
        break;
      } else if (raw[ptr] == '\r') {
        changeMessageEnd = ptr - 3;
        break;
      }
    }

    if (ptr <= changeMessageStart) {
      return;
    }

    String changeMsgString =
        RawParseUtils.decode(enc, raw, changeMessageStart, changeMessageEnd + 1);
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(psId.getParentKey(), commit.name()), accountId, ts, psId);
    changeMessage.setMessage(changeMsgString);
    changeMessage.setTag(tag);
    changeMessage.setRealAuthor(realAccountId);
    changeMessagesByPatchSet.put(psId, changeMessage);
    allChangeMessages.add(changeMessage);
  }

  private void parseNotes() throws IOException, ConfigInvalidException {
    ObjectReader reader = walk.getObjectReader();
    ChangeNotesCommit tipCommit = walk.parseCommit(tip);
    revisionNoteMap =
        RevisionNoteMap.parse(
            noteUtil,
            id,
            reader,
            NoteMap.read(reader, tipCommit),
            PatchLineComment.Status.PUBLISHED);
    Map<RevId, ChangeRevisionNote> rns = revisionNoteMap.revisionNotes;

    for (Map.Entry<RevId, ChangeRevisionNote> e : rns.entrySet()) {
      for (Comment c : e.getValue().getComments()) {
        comments.put(e.getKey(), c);
      }
    }

    for (PatchSet ps : patchSets.values()) {
      ChangeRevisionNote rn = rns.get(ps.getRevision());
      if (rn != null && rn.getPushCert() != null) {
        ps.setPushCertificate(rn.getPushCert());
      }
    }
  }

  private void parseApproval(
      PatchSet.Id psId, Account.Id accountId, Account.Id realAccountId, Timestamp ts, String line)
      throws ConfigInvalidException {
    if (accountId == null) {
      throw parseException("patch set %s requires an identified user as uploader", psId.get());
    }
    PatchSetApproval psa;
    if (line.startsWith("-")) {
      psa = parseRemoveApproval(psId, accountId, realAccountId, ts, line);
    } else {
      psa = parseAddApproval(psId, accountId, realAccountId, ts, line);
    }
    bufferedApprovals.add(psa);
  }

  private PatchSetApproval parseAddApproval(
      PatchSet.Id psId, Account.Id committerId, Account.Id realAccountId, Timestamp ts, String line)
      throws ConfigInvalidException {
    // There are potentially 3 accounts involved here:
    //  1. The account from the commit, which is the effective IdentifiedUser
    //     that produced the update.
    //  2. The account in the label footer itself, which is used during submit
    //     to copy other users' labels to a new patch set.
    //  3. The account in the Real-user footer, indicating that the whole
    //     update operation was executed by this user on behalf of the effective
    //     user.
    Account.Id effectiveAccountId;
    String labelVoteStr;
    int s = line.indexOf(' ');
    if (s > 0) {
      // Account in the label line (2) becomes the effective ID of the
      // approval. If there is a real user (3) different from the commit user
      // (2), we actually don't store that anywhere in this case; it's more
      // important to record that the real user (3) actually initiated submit.
      labelVoteStr = line.substring(0, s);
      PersonIdent ident = RawParseUtils.parsePersonIdent(line.substring(s + 1));
      checkFooter(ident != null, FOOTER_LABEL, line);
      effectiveAccountId = noteUtil.parseIdent(ident, id);
    } else {
      labelVoteStr = line;
      effectiveAccountId = committerId;
    }

    LabelVote l;
    try {
      l = LabelVote.parseWithEquals(labelVoteStr);
    } catch (IllegalArgumentException e) {
      ConfigInvalidException pe = parseException("invalid %s: %s", FOOTER_LABEL, line);
      pe.initCause(e);
      throw pe;
    }

    PatchSetApproval psa =
        new PatchSetApproval(
            new PatchSetApproval.Key(psId, effectiveAccountId, new LabelId(l.label())),
            l.value(),
            ts);
    psa.setTag(tag);
    if (!Objects.equals(realAccountId, committerId)) {
      psa.setRealAccountId(realAccountId);
    }
    ApprovalKey k = ApprovalKey.create(psId, effectiveAccountId, l.label());
    if (!approvals.containsKey(k)) {
      approvals.put(k, psa);
    }
    return psa;
  }

  private PatchSetApproval parseRemoveApproval(
      PatchSet.Id psId, Account.Id committerId, Account.Id realAccountId, Timestamp ts, String line)
      throws ConfigInvalidException {
    // See comments in parseAddApproval about the various users involved.
    Account.Id effectiveAccountId;
    String label;
    int s = line.indexOf(' ');
    if (s > 0) {
      label = line.substring(1, s);
      PersonIdent ident = RawParseUtils.parsePersonIdent(line.substring(s + 1));
      checkFooter(ident != null, FOOTER_LABEL, line);
      effectiveAccountId = noteUtil.parseIdent(ident, id);
    } else {
      label = line.substring(1);
      effectiveAccountId = committerId;
    }

    try {
      LabelType.checkNameInternal(label);
    } catch (IllegalArgumentException e) {
      ConfigInvalidException pe = parseException("invalid %s: %s", FOOTER_LABEL, line);
      pe.initCause(e);
      throw pe;
    }

    // Store an actual 0-vote approval in the map for a removed approval, for
    // several reasons:
    //  - This is closer to the ReviewDb representation, which leads to less
    //    confusion and special-casing of NoteDb.
    //  - More importantly, ApprovalCopier needs an actual approval in order to
    //    block copying an earlier approval over a later delete.
    PatchSetApproval remove =
        new PatchSetApproval(
            new PatchSetApproval.Key(psId, effectiveAccountId, new LabelId(label)), (short) 0, ts);
    if (!Objects.equals(realAccountId, committerId)) {
      remove.setRealAccountId(realAccountId);
    }
    ApprovalKey k = ApprovalKey.create(psId, effectiveAccountId, label);
    if (!approvals.containsKey(k)) {
      approvals.put(k, remove);
    }
    return remove;
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
          label.appliedBy = noteUtil.parseIdent(ident, id);
        } else {
          label.label = line.substring(c + 2);
        }
      }
    }
  }

  private Account.Id parseIdent(ChangeNotesCommit commit) throws ConfigInvalidException {
    // Check if the author name/email is the same as the committer name/email,
    // i.e. was the server ident at the time this commit was made.
    PersonIdent a = commit.getAuthorIdent();
    PersonIdent c = commit.getCommitterIdent();
    if (a.getName().equals(c.getName()) && a.getEmailAddress().equals(c.getEmailAddress())) {
      return null;
    }
    return noteUtil.parseIdent(commit.getAuthorIdent(), id);
  }

  private void parseReviewer(Timestamp ts, ReviewerStateInternal state, String line)
      throws ConfigInvalidException {
    PersonIdent ident = RawParseUtils.parsePersonIdent(line);
    if (ident == null) {
      throw invalidFooter(state.getFooterKey(), line);
    }
    Account.Id accountId = noteUtil.parseIdent(ident, id);
    reviewerUpdates.add(ReviewerStatusUpdate.create(ts, ownerId, accountId, state));
    if (!reviewers.containsRow(accountId)) {
      reviewers.put(accountId, state, ts);
    }
  }

  private void parseReviewerByEmail(Timestamp ts, ReviewerStateInternal state, String line)
      throws ConfigInvalidException {
    Address adr;
    try {
      adr = Address.parse(line);
    } catch (IllegalArgumentException e) {
      throw invalidFooter(state.getByEmailFooterKey(), line);
    }
    if (!reviewersByEmail.containsRow(adr)) {
      reviewersByEmail.put(adr, state, ts);
    }
  }

  private void parseReadOnlyUntil(ChangeNotesCommit commit) throws ConfigInvalidException {
    String raw = parseOneFooter(commit, FOOTER_READ_ONLY_UNTIL);
    if (raw == null) {
      return;
    }
    try {
      readOnlyUntil = new Timestamp(GitDateParser.parse(raw, null, Locale.US).getTime());
    } catch (ParseException e) {
      ConfigInvalidException cie = invalidFooter(FOOTER_READ_ONLY_UNTIL, raw);
      cie.initCause(e);
      throw cie;
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

  private void pruneReviewers() {
    Iterator<Table.Cell<Account.Id, ReviewerStateInternal, Timestamp>> rit =
        reviewers.cellSet().iterator();
    while (rit.hasNext()) {
      Table.Cell<Account.Id, ReviewerStateInternal, Timestamp> e = rit.next();
      if (e.getColumnKey() == ReviewerStateInternal.REMOVED) {
        rit.remove();
      }
    }
  }

  private void pruneReviewersByEmail() {
    Iterator<Table.Cell<Address, ReviewerStateInternal, Timestamp>> rit =
        reviewersByEmail.cellSet().iterator();
    while (rit.hasNext()) {
      Table.Cell<Address, ReviewerStateInternal, Timestamp> e = rit.next();
      if (e.getColumnKey() == ReviewerStateInternal.REMOVED) {
        rit.remove();
      }
    }
  }

  private void updatePatchSetStates() {
    Set<PatchSet.Id> missing = new TreeSet<>(ReviewDbUtil.intKeyOrdering());
    for (Iterator<PatchSet> it = patchSets.values().iterator(); it.hasNext(); ) {
      PatchSet ps = it.next();
      if (ps.getRevision().equals(PARTIAL_PATCH_SET)) {
        missing.add(ps.getId());
        it.remove();
      }
    }
    for (Map.Entry<PatchSet.Id, PatchSetState> e : patchSetStates.entrySet()) {
      switch (e.getValue()) {
        case PUBLISHED:
        default:
          break;

        case DELETED:
          patchSets.remove(e.getKey());
          break;

        case DRAFT:
          PatchSet ps = patchSets.get(e.getKey());
          if (ps != null) {
            ps.setDraft(true);
          }
          break;
      }
    }

    // Post-process other collections to remove items corresponding to deleted
    // (or otherwise missing) patch sets. This is safer than trying to prevent
    // insertion, as it will also filter out items racily added after the patch
    // set was deleted.
    changeMessagesByPatchSet.keys().retainAll(patchSets.keySet());

    int pruned =
        pruneEntitiesForMissingPatchSets(allChangeMessages, ChangeMessage::getPatchSetId, missing);
    pruned +=
        pruneEntitiesForMissingPatchSets(
            comments.values(), c -> new PatchSet.Id(id, c.key.patchSetId), missing);
    pruned +=
        pruneEntitiesForMissingPatchSets(
            approvals.values(), PatchSetApproval::getPatchSetId, missing);

    if (!missing.isEmpty()) {
      log.warn("ignoring {} additional entities due to missing patch sets: {}", pruned, missing);
    }
  }

  private <T> int pruneEntitiesForMissingPatchSets(
      Iterable<T> ents, Function<T, PatchSet.Id> psIdFunc, Set<PatchSet.Id> missing) {
    int pruned = 0;
    for (Iterator<T> it = ents.iterator(); it.hasNext(); ) {
      PatchSet.Id psId = psIdFunc.apply(it.next());
      if (!patchSets.containsKey(psId)) {
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

  private ConfigInvalidException parseException(String fmt, Object... args) {
    return ChangeNotes.parseException(id, fmt, args);
  }
}
