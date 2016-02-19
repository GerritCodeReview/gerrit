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

import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_BRANCH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_CHANGE_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_COMMIT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_GROUPS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_HASHTAGS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBJECT;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMISSION_ID;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_TOPIC;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.GERRIT_PLACEHOLDER_HOST;

import com.google.common.base.Enums;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.util.LabelVote;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.RawParseUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;

class ChangeNotesParser implements AutoCloseable {
  // Sentinel RevId indicating a mutable field on a patch set was parsed, but
  // the parser does not yet know its commit SHA-1.
  private static final RevId PARTIAL_PATCH_SET =
      new RevId("INVALID PARTIAL PATCH SET");

  final Map<Account.Id, ReviewerStateInternal> reviewers;
  final List<Account.Id> allPastReviewers;
  final List<SubmitRecord> submitRecords;
  final Multimap<RevId, PatchLineComment> comments;
  final TreeMap<PatchSet.Id, PatchSet> patchSets;
  final Map<PatchSet.Id, PatchSetState> patchSetStates;
  final Map<RevId, RevisionNote> revisionNotes;

  String branch;
  Change.Status status;
  String topic;
  Set<String> hashtags;
  Timestamp createdOn;
  Timestamp lastUpdatedOn;
  Account.Id ownerId;
  String changeId;
  String subject;
  String originalSubject;
  String submissionId;
  PatchSet.Id currentPatchSetId;
  NoteMap noteMap;

  private final Change.Id id;
  private final ObjectId tip;
  private final RevWalk walk;
  private final Repository repo;
  private final Map<PatchSet.Id,
      Table<Account.Id, String, Optional<PatchSetApproval>>> approvals;
  private final List<ChangeMessage> allChangeMessages;
  private final Multimap<PatchSet.Id, ChangeMessage> changeMessagesByPatchSet;

  ChangeNotesParser(Project.NameKey project, Change.Id changeId, ObjectId tip,
      RevWalk walk, GitRepositoryManager repoManager)
      throws RepositoryNotFoundException, IOException {
    this.id = changeId;
    this.tip = tip;
    this.walk = walk;
    this.repo = repoManager.openMetadataRepository(project);
    approvals = Maps.newHashMap();
    reviewers = Maps.newLinkedHashMap();
    allPastReviewers = Lists.newArrayList();
    submitRecords = Lists.newArrayListWithExpectedSize(1);
    allChangeMessages = Lists.newArrayList();
    changeMessagesByPatchSet = LinkedListMultimap.create();
    comments = ArrayListMultimap.create();
    patchSets = Maps.newTreeMap(ReviewDbUtil.intKeyOrdering());
    patchSetStates = Maps.newHashMap();
    revisionNotes = Maps.newHashMap();
  }

  @Override
  public void close() {
    repo.close();
  }

  void parseAll() throws ConfigInvalidException, IOException {
    walk.markStart(walk.parseCommit(tip));
    for (RevCommit commit : walk) {
      parse(commit);
    }
    parseNotes();
    allPastReviewers.addAll(reviewers.keySet());
    pruneReviewers();
    updatePatchSetStates();
    checkMandatoryFooters();
  }

  ImmutableListMultimap<PatchSet.Id, PatchSetApproval>
      buildApprovals() {
    Multimap<PatchSet.Id, PatchSetApproval> result =
        ArrayListMultimap.create(approvals.keySet().size(), 3);
    for (Table<?, ?, Optional<PatchSetApproval>> curr : approvals.values()) {
      for (Optional<PatchSetApproval> psa : curr.values()) {
        if (psa.isPresent()) {
          result.put(psa.get().getPatchSetId(), psa.get());
        }
      }
    }
    for (Collection<PatchSetApproval> v : result.asMap().values()) {
      Collections.sort((List<PatchSetApproval>) v, ChangeNotes.PSA_BY_TIME);
    }
    return ImmutableListMultimap.copyOf(result);
  }

  ImmutableList<ChangeMessage> buildAllMessages() {
    return ImmutableList.copyOf(Lists.reverse(allChangeMessages));
  }

  ImmutableListMultimap<PatchSet.Id, ChangeMessage> buildMessagesByPatchSet() {
    for (Collection<ChangeMessage> v :
        changeMessagesByPatchSet.asMap().values()) {
      Collections.reverse((List<ChangeMessage>) v);
    }
    return ImmutableListMultimap.copyOf(changeMessagesByPatchSet);
  }

  private void parse(RevCommit commit) throws ConfigInvalidException {
    Timestamp ts =
        new Timestamp(commit.getCommitterIdent().getWhen().getTime());

    boolean updateTs = commit.getParentCount() == 0;
    createdOn = ts;
    if (branch == null) {
      branch = parseBranch(commit);
      updateTs |= branch != null;
    }
    if (status == null) {
      status = parseStatus(commit);
      updateTs |= status != null;
    }

    PatchSet.Id psId = parsePatchSetId(commit);
    if (currentPatchSetId == null || psId.get() > currentPatchSetId.get()) {
      currentPatchSetId = psId;
    }

    PatchSetState psState = parsePatchSetState(commit);
    if (psState != null && !patchSetStates.containsKey(psId)) {
      patchSetStates.put(psId, psState);
    }

    Account.Id accountId = parseIdent(commit);
    if (accountId != null) {
      ownerId = accountId;
    }

    if (changeId == null) {
      changeId = parseChangeId(commit);
      updateTs |= changeId != null;
    }

    String currSubject = parseSubject(commit);
    if (currSubject != null) {
      if (subject == null) {
        subject = currSubject;
      }
      originalSubject = currSubject;
      updateTs = true;
    }

    updateTs |= parseChangeMessage(psId, accountId, commit, ts) != null;
    if (topic == null) {
      topic = parseTopic(commit);
      updateTs |= topic != null;
    }

    Set<String> oldHashtags = hashtags;
    parseHashtags(commit);
    updateTs |= hashtags != oldHashtags;

    if (submissionId == null) {
      submissionId = parseSubmissionId(commit);
      updateTs |= submissionId != null;
    }

    ObjectId currRev = parseRevision(commit);
    if (currRev != null) {
      parsePatchSet(psId, currRev, accountId, ts);
      updateTs = true;
    }
    parseGroups(psId, commit);

    if (submitRecords.isEmpty()) {
      // Only parse the most recent set of submit records; any older ones are
      // still there, but not currently used.
      parseSubmitRecords(commit.getFooterLines(FOOTER_SUBMITTED_WITH));
      updateTs |= !submitRecords.isEmpty();
    }

    for (String line : commit.getFooterLines(FOOTER_LABEL)) {
      parseApproval(psId, accountId, ts, line);
      updateTs = true;
    }

    for (ReviewerStateInternal state : ReviewerStateInternal.values()) {
      for (String line : commit.getFooterLines(state.getFooterKey())) {
        parseReviewer(state, line);
      }
      // Don't update timestamp when a reviewer was added, matching RevewDb
      // behavior.
    }

    if (lastUpdatedOn == null && updateTs) {
      lastUpdatedOn = ts;
    }
  }

  private String parseSubmissionId(RevCommit commit)
      throws ConfigInvalidException {
    return parseOneFooter(commit, FOOTER_SUBMISSION_ID);
  }

  private String parseBranch(RevCommit commit) throws ConfigInvalidException {
    String branch = parseOneFooter(commit, FOOTER_BRANCH);
    return branch != null ? RefNames.fullName(branch) : null;
  }

  private String parseChangeId(RevCommit commit) throws ConfigInvalidException {
    return parseOneFooter(commit, FOOTER_CHANGE_ID);
  }

  private String parseSubject(RevCommit commit) throws ConfigInvalidException {
    return parseOneFooter(commit, FOOTER_SUBJECT);
  }

  private String parseTopic(RevCommit commit) throws ConfigInvalidException {
    return parseOneFooter(commit, FOOTER_TOPIC);
  }

  private String parseOneFooter(RevCommit commit, FooterKey footerKey)
      throws ConfigInvalidException {
    List<String> footerLines = commit.getFooterLines(footerKey);
    if (footerLines.isEmpty()) {
      return null;
    } else if (footerLines.size() > 1) {
      throw expectedOneFooter(footerKey, footerLines);
    }
    return footerLines.get(0);
  }

  private String parseExactlyOneFooter(RevCommit commit, FooterKey footerKey)
      throws ConfigInvalidException {
    String line = parseOneFooter(commit, footerKey);
    if (line == null) {
      throw expectedOneFooter(footerKey, Collections.<String> emptyList());
    }
    return line;
  }

  private ObjectId parseRevision(RevCommit commit)
      throws ConfigInvalidException {
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

  private void parsePatchSet(PatchSet.Id psId, ObjectId rev,
      Account.Id accountId, Timestamp ts) throws ConfigInvalidException {
    if (accountId == null) {
      throw parseException(
          "patch set %s requires an identified user as uploader", psId.get());
    }
    PatchSet ps = patchSets.get(psId);
    if (ps == null) {
      ps = new PatchSet(psId);
      patchSets.put(psId, ps);
    } else if (ps.getRevision() != PARTIAL_PATCH_SET) {
      throw new ConfigInvalidException(
          String.format(
              "Multiple revisions parsed for patch set %s: %s and %s",
              psId.get(), patchSets.get(psId).getRevision(), rev.name()));
    }
    ps.setRevision(new RevId(rev.name()));
    ps.setUploader(accountId);
    ps.setCreatedOn(ts);
  }

  private void parseGroups(PatchSet.Id psId, RevCommit commit)
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

  private void parseHashtags(RevCommit commit) throws ConfigInvalidException {
    // Commits are parsed in reverse order and only the last set of hashtags should be used.
    if (hashtags != null) {
      return;
    }
    List<String> hashtagsLines = commit.getFooterLines(FOOTER_HASHTAGS);
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

  private Change.Status parseStatus(RevCommit commit)
      throws ConfigInvalidException {
    List<String> statusLines = commit.getFooterLines(FOOTER_STATUS);
    if (statusLines.isEmpty()) {
      return null;
    } else if (statusLines.size() > 1) {
      throw expectedOneFooter(FOOTER_STATUS, statusLines);
    }
    Optional<Change.Status> status = Enums.getIfPresent(
        Change.Status.class, statusLines.get(0).toUpperCase());
    if (!status.isPresent()) {
      throw invalidFooter(FOOTER_STATUS, statusLines.get(0));
    }
    return status.get();
  }

  private PatchSet.Id parsePatchSetId(RevCommit commit)
      throws ConfigInvalidException {
    String psIdLine = parseExactlyOneFooter(commit, FOOTER_PATCH_SET);
    int s = psIdLine.indexOf(' ');
    String psIdStr = s < 0 ? psIdLine : psIdLine.substring(0, s);
    Integer psId = Ints.tryParse(psIdStr);
    if (psId == null) {
      throw invalidFooter(FOOTER_PATCH_SET, psIdStr);
    }
    return new PatchSet.Id(id, psId);
  }

  private PatchSetState parsePatchSetState(RevCommit commit)
      throws ConfigInvalidException {
    String psIdLine = parseExactlyOneFooter(commit, FOOTER_PATCH_SET);
    int s = psIdLine.indexOf(' ');
    if (s < 0) {
      return null;
    }
    String withParens = psIdLine.substring(s + 1);
    if (withParens.startsWith("(") && withParens.endsWith(")")) {
      Optional<PatchSetState> state = Enums.getIfPresent(PatchSetState.class,
          withParens.substring(1, withParens.length() - 1).toUpperCase());
      if (state.isPresent()) {
        return state.get();
      }
    }
    throw invalidFooter(FOOTER_PATCH_SET, psIdLine);
  }

  private ChangeMessage parseChangeMessage(PatchSet.Id psId,
      Account.Id accountId, RevCommit commit, Timestamp ts) {
    byte[] raw = commit.getRawBuffer();
    int size = raw.length;
    Charset enc = RawParseUtils.parseEncoding(raw);

    int subjectStart = RawParseUtils.commitMessage(raw, 0);
    if (subjectStart < 0 || subjectStart >= size) {
      return null;
    }

    int subjectEnd = RawParseUtils.endOfParagraph(raw, subjectStart);
    if (subjectEnd == size) {
      return null;
    }

    int changeMessageStart;

    if (raw[subjectEnd] == '\n') {
      changeMessageStart = subjectEnd + 2; //\n\n ends paragraph
    } else if (raw[subjectEnd] == '\r') {
      changeMessageStart = subjectEnd + 4; //\r\n\r\n ends paragraph
    } else {
      return null;
    }

    int ptr = size - 1;
    int changeMessageEnd = -1;
    while(ptr > changeMessageStart) {
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
      return null;
    }

    String changeMsgString = RawParseUtils.decode(enc, raw,
        changeMessageStart, changeMessageEnd + 1);
    ChangeMessage changeMessage = new ChangeMessage(
        new ChangeMessage.Key(psId.getParentKey(), commit.name()),
        accountId,
        ts,
        psId);
    changeMessage.setMessage(changeMsgString);
    changeMessagesByPatchSet.put(psId, changeMessage);
    allChangeMessages.add(changeMessage);
    return changeMessage;
  }

  private void parseNotes()
      throws IOException, ConfigInvalidException {
    ObjectReader reader = walk.getObjectReader();
    RevCommit tipCommit = walk.parseCommit(tip);
    noteMap = NoteMap.read(reader, tipCommit);

    for (Note note : noteMap) {
      RevisionNote rn = new RevisionNote(id, reader, note.getData());
      RevId revId = new RevId(note.name());
      revisionNotes.put(revId, rn);
      for (PatchLineComment plc : rn.comments) {
        comments.put(revId, plc);
      }
    }

    for (PatchSet ps : patchSets.values()) {
      RevisionNote rn = revisionNotes.get(ps.getRevision());
      if (rn != null && rn.pushCert != null) {
        ps.setPushCertificate(rn.pushCert);
      }
    }
  }

  private void parseApproval(PatchSet.Id psId, Account.Id accountId,
      Timestamp ts, String line) throws ConfigInvalidException {
    if (accountId == null) {
      throw parseException(
          "patch set %s requires an identified user as uploader", psId.get());
    }
    if (line.startsWith("-")) {
      parseRemoveApproval(psId, accountId, line);
    } else {
      parseAddApproval(psId, accountId, ts, line);
    }
  }

  private void parseAddApproval(PatchSet.Id psId, Account.Id committerId,
      Timestamp ts, String line) throws ConfigInvalidException {
    Account.Id accountId;
    String labelVoteStr;
    int s = line.indexOf(' ');
    if (s > 0) {
      labelVoteStr = line.substring(0, s);
      PersonIdent ident = RawParseUtils.parsePersonIdent(line.substring(s + 1));
      checkFooter(ident != null, FOOTER_LABEL, line);
      accountId = parseIdent(ident);
    } else {
      labelVoteStr = line;
      accountId = committerId;
    }

    LabelVote l;
    try {
      l = LabelVote.parseWithEquals(labelVoteStr);
    } catch (IllegalArgumentException e) {
      ConfigInvalidException pe =
          parseException("invalid %s: %s", FOOTER_LABEL, line);
      pe.initCause(e);
      throw pe;
    }

    Table<Account.Id, String, Optional<PatchSetApproval>> curr =
        getApprovalsTableIfNoVotePresent(psId, accountId, l.label());
    if (curr != null) {
      curr.put(accountId, l.label(), Optional.of(new PatchSetApproval(
          new PatchSetApproval.Key(
              psId,
              accountId,
              new LabelId(l.label())),
          l.value(),
          ts)));
    }
  }

  private void parseRemoveApproval(PatchSet.Id psId, Account.Id committerId,
      String line) throws ConfigInvalidException {
    Account.Id accountId;
    String label;
    int s = line.indexOf(' ');
    if (s > 0) {
      label = line.substring(1, s);
      PersonIdent ident = RawParseUtils.parsePersonIdent(line.substring(s + 1));
      checkFooter(ident != null, FOOTER_LABEL, line);
      accountId = parseIdent(ident);
    } else {
      label = line.substring(1);
      accountId = committerId;
    }

    try {
      LabelType.checkNameInternal(label);
    } catch (IllegalArgumentException e) {
      ConfigInvalidException pe =
          parseException("invalid %s: %s", FOOTER_LABEL, line);
      pe.initCause(e);
      throw pe;
    }

    Table<Account.Id, String, Optional<PatchSetApproval>> curr =
        getApprovalsTableIfNoVotePresent(psId, accountId, label);
    if (curr != null) {
      curr.put(accountId, label, Optional.<PatchSetApproval> absent());
    }
  }

  private Table<Account.Id, String, Optional<PatchSetApproval>>
      getApprovalsTableIfNoVotePresent(PatchSet.Id psId, Account.Id accountId,
        String label) {

    Table<Account.Id, String, Optional<PatchSetApproval>> curr =
        approvals.get(psId);
    if (curr != null) {
      if (curr.contains(accountId, label)) {
        return null;
      }
    } else {
      curr = Tables.newCustomTable(
          Maps.<Account.Id, Map<String, Optional<PatchSetApproval>>>
              newHashMapWithExpectedSize(2),
          new Supplier<Map<String, Optional<PatchSetApproval>>>() {
            @Override
            public Map<String, Optional<PatchSetApproval>> get() {
              return Maps.newLinkedHashMap();
            }
          });
      approvals.put(psId, curr);
    }
    return curr;
  }

  private void parseSubmitRecords(List<String> lines)
      throws ConfigInvalidException {
    SubmitRecord rec = null;

    for (String line : lines) {
      int c = line.indexOf(": ");
      if (c < 0) {
        rec = new SubmitRecord();
        submitRecords.add(rec);
        int s = line.indexOf(' ');
        String statusStr = s >= 0 ? line.substring(0, s) : line;
        Optional<SubmitRecord.Status> status =
            Enums.getIfPresent(SubmitRecord.Status.class, statusStr);
        checkFooter(status.isPresent(), FOOTER_SUBMITTED_WITH, line);
        rec.status = status.get();
        if (s >= 0) {
          rec.errorMessage = line.substring(s);
        }
      } else {
        checkFooter(rec != null, FOOTER_SUBMITTED_WITH, line);
        SubmitRecord.Label label = new SubmitRecord.Label();
        if (rec.labels == null) {
          rec.labels = Lists.newArrayList();
        }
        rec.labels.add(label);

        Optional<SubmitRecord.Label.Status> status = Enums.getIfPresent(
            SubmitRecord.Label.Status.class, line.substring(0, c));
        checkFooter(status.isPresent(), FOOTER_SUBMITTED_WITH, line);
        label.status = status.get();
        int c2 = line.indexOf(": ", c + 2);
        if (c2 >= 0) {
          label.label = line.substring(c + 2, c2);
          PersonIdent ident =
              RawParseUtils.parsePersonIdent(line.substring(c2 + 2));
          checkFooter(ident != null, FOOTER_SUBMITTED_WITH, line);
          label.appliedBy = parseIdent(ident);
        } else {
          label.label = line.substring(c + 2);
        }
      }
    }
  }

  private Account.Id parseIdent(RevCommit commit)
      throws ConfigInvalidException {
    // Check if the author name/email is the same as the committer name/email,
    // i.e. was the server ident at the time this commit was made.
    PersonIdent a = commit.getAuthorIdent();
    PersonIdent c = commit.getCommitterIdent();
    if (a.getName().equals(c.getName())
        && a.getEmailAddress().equals(c.getEmailAddress())) {
      return null;
    }
    return parseIdent(commit.getAuthorIdent());
  }

  private Account.Id parseIdent(PersonIdent ident)
      throws ConfigInvalidException {
    Account.Id id = ChangeNoteUtil.parseIdent(ident);
    if (id == null) {
      throw parseException("invalid identity, expected <id>@%s: %s",
          GERRIT_PLACEHOLDER_HOST, ident.getEmailAddress());
    }
    return id;
  }

  private void parseReviewer(ReviewerStateInternal state, String line)
      throws ConfigInvalidException {
    PersonIdent ident = RawParseUtils.parsePersonIdent(line);
    if (ident == null) {
      throw invalidFooter(state.getFooterKey(), line);
    }
    Account.Id accountId = parseIdent(ident);
    if (!reviewers.containsKey(accountId)) {
      reviewers.put(accountId, state);
    }
  }

  private void pruneReviewers() {
    Iterator<Map.Entry<Account.Id, ReviewerStateInternal>> rit =
        reviewers.entrySet().iterator();
    while (rit.hasNext()) {
      Map.Entry<Account.Id, ReviewerStateInternal> e = rit.next();
      if (e.getValue() == ReviewerStateInternal.REMOVED) {
        rit.remove();
        for (Table<Account.Id, ?, ?> curr : approvals.values()) {
          curr.rowKeySet().remove(e.getKey());
        }
      }
    }
  }

  private void updatePatchSetStates() throws ConfigInvalidException {
    for (PatchSet ps : patchSets.values()) {
      if (ps.getRevision() == PARTIAL_PATCH_SET) {
        throw parseException("No %s found for patch set %s",
            FOOTER_COMMIT, ps.getPatchSetId());
      }
    }
    if (patchSetStates.isEmpty()) {
      return;
    }

    boolean deleted = false;
    for (Map.Entry<PatchSet.Id, PatchSetState> e : patchSetStates.entrySet()) {
      switch (e.getValue()) {
        case PUBLISHED:
        default:
          break;

        case DELETED:
          deleted = true;
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
    if (!deleted) {
      return;
    }

    // Post-process other collections to remove items corresponding to deleted
    // patch sets. This is safer than trying to prevent insertion, as it will
    // also filter out items racily added after the patch set was deleted.
    NavigableSet<PatchSet.Id> all = patchSets.navigableKeySet();
    if (!all.isEmpty()) {
      currentPatchSetId = all.last();
    } else {
      currentPatchSetId = null;
    }
    approvals.keySet().retainAll(all);
    changeMessagesByPatchSet.keys().retainAll(all);

    for (Iterator<ChangeMessage> it = allChangeMessages.iterator();
        it.hasNext();) {
      if (!all.contains(it.next().getPatchSetId())) {
        it.remove();
      }
    }
    for (Iterator<PatchLineComment> it = comments.values().iterator();
        it.hasNext();) {
      PatchSet.Id psId = it.next().getKey().getParentKey().getParentKey();
      if (!all.contains(psId)) {
        it.remove();
      }
    }
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
      throw parseException("Missing footers: " + Joiner.on(", ")
          .join(Lists.transform(missing, new Function<FooterKey, String>() {
            @Override
            public String apply(FooterKey input) {
              return input.getName();
            }
          })));
    }
  }

  private ConfigInvalidException expectedOneFooter(FooterKey footer,
      List<String> actual) {
    return parseException("missing or multiple %s: %s",
        footer.getName(), actual);
  }

  private ConfigInvalidException invalidFooter(FooterKey footer,
      String actual) {
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
