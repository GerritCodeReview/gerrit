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

package com.google.gerrit.server.notedb;

import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_LABEL;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_PATCH_SET;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_STATUS;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.FOOTER_SUBMITTED_WITH;
import static com.google.gerrit.server.notedb.ChangeNoteUtil.GERRIT_PLACEHOLDER_HOST;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Enums;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.google.common.primitives.Ints;
import com.google.gerrit.client.diff.CommentRange;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetApproval.LabelId;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gerrit.server.util.LabelVote;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.GitDateParser;
import org.eclipse.jgit.util.MutableInteger;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.lib.Constants;

import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** View of a single {@link Change} based on the log of its notes branch. */
public class ChangeNotes extends VersionedMetaData {
  private static final Ordering<PatchSetApproval> PSA_BY_TIME =
      Ordering.natural().onResultOf(
        new Function<PatchSetApproval, Timestamp>() {
          @Override
          public Timestamp apply(PatchSetApproval input) {
            return input.getGranted();
          }
        });

  public static final Ordering<ChangeMessage> MESSAGE_BY_TIME =
      Ordering.natural().onResultOf(
        new Function<ChangeMessage, Timestamp>() {
          @Override
          public Timestamp apply(ChangeMessage input) {
            return input.getWrittenOn();
          }
        });

  private static Comparator<PatchLineComment> PatchLineCommentComparator =
      new Comparator<PatchLineComment>() {
    public int compare(PatchLineComment c1, PatchLineComment c2) {
      //first sort on filename
      String filename1 = c1.getKey().getParentKey().get();
      String filename2 = c2.getKey().getParentKey().get();
      int nameCmp = filename1.compareTo(filename2);
      if (nameCmp != 0) {
        return nameCmp;
      }

      //next sort on comment range
      int rangeCmp = c1.getRange().compareTo(c2.getRange());
      if (rangeCmp != 0) {
        return rangeCmp;
      }

      //next sort on timestamp
      int timeCmp = c1.getWrittenOn().compareTo(c2.getWrittenOn());
      if (timeCmp != 0) {
        return timeCmp;
      }
      return 0;
    }
  };

  public static final Ordering<PatchLineComment> COMMENT_ORDERING =
      Ordering.from(PatchLineCommentComparator);

  @Singleton
  public static class Factory {
    private final GitRepositoryManager repoManager;

    @VisibleForTesting
    @Inject
    public Factory(GitRepositoryManager repoManager) {
      this.repoManager = repoManager;
    }

    public ChangeNotes create(Change change) {
      return new ChangeNotes(repoManager, change);
    }
  }

  private static class Parser {
    private final Change.Id changeId;
    private final ObjectId tip;
    private final RevWalk walk;
    private final Repository repo;
    private final Map<PatchSet.Id,
        Table<Account.Id, String, Optional<PatchSetApproval>>> approvals;
    private final Map<Account.Id, ReviewerState> reviewers;
    private final List<SubmitRecord> submitRecords;
    private final Multimap<PatchSet.Id, ChangeMessage> changeMessages;
    private final Multimap<PatchSet.Id, PatchLineComment> publishedComments;
    private NoteMap publishedCommentNoteMap;
    private Change.Status status;

    private Parser(Change change, ObjectId tip, RevWalk walk,
        GitRepositoryManager repoManager) throws RepositoryNotFoundException,
        IOException {
      this.changeId = change.getId();
      this.tip = tip;
      this.walk = walk;
      this.repo = repoManager.openRepository(change.getProject());
      approvals = Maps.newHashMap();
      reviewers = Maps.newLinkedHashMap();
      submitRecords = Lists.newArrayListWithExpectedSize(1);
      changeMessages = LinkedListMultimap.create();
      publishedComments = LinkedListMultimap.create();
    }

    private void parseAll() throws ConfigInvalidException, IOException, ParseException {
      walk.markStart(walk.parseCommit(tip));
      for (RevCommit commit : walk) {
        parse(commit);
      }
      parseComments(walk.parseCommit(tip));
      pruneReviewers();
    }

    private ImmutableListMultimap<PatchSet.Id, PatchSetApproval>
        buildApprovals() {
      Multimap<PatchSet.Id, PatchSetApproval> result =
          ArrayListMultimap.create(approvals.keySet().size(), 3);
      for (Table<?, ?, Optional<PatchSetApproval>> curr
          : approvals.values()) {
        for (PatchSetApproval psa : Optional.presentInstances(curr.values())) {
          result.put(psa.getPatchSetId(), psa);
        }
      }
      for (Collection<PatchSetApproval> v : result.asMap().values()) {
        Collections.sort((List<PatchSetApproval>) v, PSA_BY_TIME);
      }
      return ImmutableListMultimap.copyOf(result);
    }

    private ImmutableListMultimap<PatchSet.Id, ChangeMessage> buildMessages() {
      for (Collection<ChangeMessage> v : changeMessages.asMap().values()) {
        Collections.sort((List<ChangeMessage>) v, MESSAGE_BY_TIME);
      }
      return ImmutableListMultimap.copyOf(changeMessages);
    }

    private void parse(RevCommit commit) throws ConfigInvalidException, IOException {
      if (status == null) {
        status = parseStatus(commit);
      }
      PatchSet.Id psId = parsePatchSetId(commit);
      Account.Id accountId = parseIdent(commit);
      parseChangeMessage(psId, accountId, commit);


      if (submitRecords.isEmpty()) {
        // Only parse the most recent set of submit records; any older ones are
        // still there, but not currently used.
        parseSubmitRecords(commit.getFooterLines(FOOTER_SUBMITTED_WITH));
      }

      for (String line : commit.getFooterLines(FOOTER_LABEL)) {
        parseApproval(psId, accountId, commit, line);
      }

      for (ReviewerState state : ReviewerState.values()) {
        for (String line : commit.getFooterLines(state.getFooterKey())) {
          parseReviewer(state, line);
        }
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
      List<String> psIdLines = commit.getFooterLines(FOOTER_PATCH_SET);
      if (psIdLines.size() != 1) {
        throw expectedOneFooter(FOOTER_PATCH_SET, psIdLines);
      }
      Integer psId = Ints.tryParse(psIdLines.get(0));
      if (psId == null) {
        throw invalidFooter(FOOTER_PATCH_SET, psIdLines.get(0));
      }
      return new PatchSet.Id(changeId, psId);
    }

    private void parseChangeMessage(PatchSet.Id psId, Account.Id accountId,
        RevCommit commit) {
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
        return;
      }

      String changeMsgString = RawParseUtils.decode(enc, raw,
          changeMessageStart, changeMessageEnd + 1);
      ChangeMessage changeMessage = new ChangeMessage(
          new ChangeMessage.Key(psId.getParentKey(), commit.name()),
          accountId,
          new Timestamp(commit.getCommitterIdent().getWhen().getTime()),
          psId);
      changeMessage.setMessage(changeMsgString);
      changeMessages.put(psId, changeMessage);
    }

    private CommentRange parseCommentRange(byte[] note, int curr) {
      MutableInteger m = new MutableInteger();
      m.value = curr;

      int startLine = RawParseUtils.parseBase10(note, m.value, m);
      if (startLine == 0)
        return null;
      m.value += 1;

      int startChar = RawParseUtils.parseBase10(note, m.value, m);
      if (startChar == 0)
        return null;
      m.value += 1;

      int endLine = RawParseUtils.parseBase10(note, m.value, m);
      if (endLine == 0)
        return null;
      m.value += 1;

      int endChar = RawParseUtils.parseBase10(note, m.value, m);
      if (endChar == 0)
        return null;
      m.value += 1;

      CommentRange result =
          CommentRange.create(startLine, startChar, endLine, endChar);
      return result;
    }

    private String parseParent(byte[] note , int curr, Charset enc) {
      int nextNewLine = RawParseUtils.nextLF(note, curr);
      String nextHeader = RawParseUtils.decode(enc, note, curr, curr + 6);
      boolean hasParent = nextHeader.startsWith("Parent");
      if (hasParent) {
        int startOfParent = RawParseUtils.endOfFooterLineKey(note, curr) + 1;
        return RawParseUtils.decode(enc, note, startOfParent, nextNewLine - 1);
      } else {
        return null;
      }
    }

    private List<PatchLineComment> parseNote(byte[] note)
        throws ConfigInvalidException {
      List<PatchLineComment> result = Lists.newArrayList();
      int sizeOfNote = note.length;
      Charset enc = RawParseUtils.parseEncoding(note);
      String currentFileName = null;
      int curr = 0;
      int endOfLine;

      //parse patchset id
      int startOfPSId =
          RawParseUtils.endOfFooterLineKey(note, curr) + 1;
      int patchSetId =
          RawParseUtils.parseBase10(note, startOfPSId, null);
      checkResult(patchSetId, "patchset id");
      curr = RawParseUtils.nextLF(note, curr);
      PatchSet.Id psId = new PatchSet.Id(changeId, patchSetId);

      while (curr < sizeOfNote) {
        //check to see if new file
        String start = RawParseUtils.decode(enc, note, curr, curr + 5);
        if (start.startsWith("File:") || currentFileName == null) {
          //parse new file name
          int startOfFileName =
              RawParseUtils.endOfFooterLineKey(note, curr) + 1;
          endOfLine = RawParseUtils.nextLF(note, curr);
          currentFileName =
              RawParseUtils.decode(enc, note, startOfFileName, endOfLine - 1);
          curr = RawParseUtils.nextLF(note, endOfLine);
        }

        CommentRange range = parseCommentRange(note, curr);
        int commentLine;
        if (range == null) {
          commentLine = RawParseUtils.parseBase10(note, curr, null);
        } else {
          commentLine = range.end_line();
        }


        checkResult(commentLine, "comment range");
        curr = RawParseUtils.nextLF(note, curr);

        //parse timestamp
        endOfLine = RawParseUtils.nextLF(note, curr);
        String dateString =
            RawParseUtils.decode(enc, note, curr, endOfLine - 1);
        Timestamp commentTime;
        try {
          commentTime = (Timestamp) GitDateParser.parse(dateString, null);
        } catch (ParseException e) {
          return null;
        }
        checkResult(commentTime, "comment timestamp");
        curr = RawParseUtils.nextLF(note, curr);


        //parse number of bytes
        int startOfLength =
            RawParseUtils.endOfFooterLineKey(note, curr) + 1;
        int commentLength =
            RawParseUtils.parseBase10(note, startOfLength, null);
        checkResult(commentLength, "comment length");
        curr = RawParseUtils.nextLF(note, curr);

        //parse author
        int startOfAuthor =
            RawParseUtils.endOfFooterLineKey(note, curr) + 1;
        PersonIdent author =
            RawParseUtils.parsePersonIdent(note, startOfAuthor);
        Account.Id aId = parseIdent(author);
        checkResult(author, "comment author");

        //parse parent if present
        String parentUUID = parseParent(note, curr, enc);
        if (parentUUID != null) {
          curr = RawParseUtils.nextLF(note, curr);
        }

        //parse UUID
        int startOfUUID = RawParseUtils.endOfFooterLineKey(note, curr) + 1;
        endOfLine = RawParseUtils.nextLF(note, curr);
        String UUID = RawParseUtils.decode(
            enc, note, startOfUUID, endOfLine - 1);
        checkResult(commentLength, "UUID");
        curr = endOfLine;


        String message = RawParseUtils.decode(
            enc, note, curr, curr + commentLength);
        checkResult(message, "message contents");


        PatchLineComment plc = new PatchLineComment(
            new PatchLineComment.Key(new Patch.Key(psId, currentFileName), UUID),
            commentLine, aId, parentUUID, commentTime);
        plc.setMessage(message);
        result.add(plc);
        curr = RawParseUtils.nextLF(note, curr + commentLength);
        curr = RawParseUtils.nextLF(note, curr);
      }
      return result;
    }

    private void parseComments(RevCommit commit)
        throws IOException, ConfigInvalidException, ParseException {
      Ref sharedMeta = repo.getRef(ChangeNoteUtil.changeRefName(changeId));
      if (sharedMeta != null) {
        RevCommit sharedBaseCommit = walk.parseCommit(sharedMeta.getObjectId());
        publishedCommentNoteMap = NoteMap.read(walk.getObjectReader(), sharedBaseCommit);
      }
      Iterator<Note> notes = publishedCommentNoteMap.iterator();
      while (notes.hasNext()) {
        Note next = notes.next();
        byte[] bytes = walk.getObjectReader().open(
            next.getData(), Constants.OBJ_BLOB).getBytes();
        List<PatchLineComment> result = parseNote(bytes);
        if (result == null) {
          continue;
        }
        PatchSet.Id psId = result.get(0).getKey().getParentKey().getParentKey();
        publishedComments.putAll(psId, result);
      }
    }

    private void parseApproval(PatchSet.Id psId, Account.Id accountId,
        RevCommit commit, String line) throws ConfigInvalidException {
      Table<Account.Id, String, Optional<PatchSetApproval>> curr =
          approvals.get(psId);
      if (curr == null) {
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

      if (line.startsWith("-")) {
        String label = line.substring(1);
        if (!curr.contains(accountId, label)) {
          curr.put(accountId, label, Optional.<PatchSetApproval> absent());
        }
      } else {
        LabelVote l;
        try {
          l = LabelVote.parseWithEquals(line);
        } catch (IllegalArgumentException e) {
          ConfigInvalidException pe =
              parseException("invalid %s: %s", FOOTER_LABEL, line);
          pe.initCause(e);
          throw pe;
        }
        if (!curr.contains(accountId, l.getLabel())) {
          curr.put(accountId, l.getLabel(), Optional.of(new PatchSetApproval(
              new PatchSetApproval.Key(
                  psId,
                  accountId,
                  new LabelId(l.getLabel())),
              l.getValue(),
              new Timestamp(commit.getCommitterIdent().getWhen().getTime()))));
        }
      }
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
      return parseIdent(commit.getAuthorIdent());
    }

    private Account.Id parseIdent(PersonIdent ident)
        throws ConfigInvalidException {
      String email = ident.getEmailAddress();
      int at = email.indexOf('@');
      if (at >= 0) {
        String host = email.substring(at + 1, email.length());
        Integer id = Ints.tryParse(email.substring(0, at));
        if (id != null && host.equals(GERRIT_PLACEHOLDER_HOST)) {
          return new Account.Id(id);
        }
      }
      throw parseException("invalid identity, expected <id>@%s: %s",
        GERRIT_PLACEHOLDER_HOST, email);
    }

    private void parseReviewer(ReviewerState state, String line)
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
      Iterator<Map.Entry<Account.Id, ReviewerState>> rit =
          reviewers.entrySet().iterator();
      while (rit.hasNext()) {
        Map.Entry<Account.Id, ReviewerState> e = rit.next();
        if (e.getValue() == ReviewerState.REMOVED) {
          rit.remove();
          for (Table<Account.Id, ?, ?> curr : approvals.values()) {
            curr.rowKeySet().remove(e.getKey());
          }
        }
      }
    }

    private ConfigInvalidException parseException(String fmt, Object... args) {
      return new ConfigInvalidException("Change " + changeId + ": "
          + String.format(fmt, args));
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

    private void checkResult(Object o, String fieldName) throws ConfigInvalidException {
      if (o instanceof Integer) {
        if (((Integer) o).intValue() < 0) {
          throw parseException("could not parse ", fieldName);
        }
      }
      if (o == null) {
        throw parseException("could not parse ", fieldName);
      }
    }
  }

  private final GitRepositoryManager repoManager;
  private final Change change;
  private boolean loaded;
  private ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals;
  private ImmutableSetMultimap<ReviewerState, Account.Id> reviewers;
  private ImmutableList<SubmitRecord> submitRecords;
  private ImmutableListMultimap<PatchSet.Id, ChangeMessage> changeMessages;
  private ImmutableListMultimap<PatchSet.Id, PatchLineComment> publishedComments;
  NoteMap noteMap;

  @VisibleForTesting
  ChangeNotes(GitRepositoryManager repoManager, Change change) {
    this.repoManager = repoManager;
    this.change = new Change(change);
  }

  // TODO(dborowitz): Wrap fewer exceptions if/when we kill gwtorm.
  public ChangeNotes load() throws OrmException {
    if (!loaded) {
      Repository repo;
      try {
        repo = repoManager.openRepository(change.getProject());
      } catch (IOException e) {
        throw new OrmException(e);
      }
      try {
        load(repo);
        loaded = true;
      } catch (ConfigInvalidException | IOException e) {
        throw new OrmException(e);
      } finally {
        repo.close();
      }
    }
    return this;
  }

  public Change.Id getChangeId() {
    return change.getId();
  }

  public Change getChange() {
    return change;
  }

  public ImmutableListMultimap<PatchSet.Id, PatchSetApproval> getApprovals() {
    return approvals;
  }

  public ImmutableSetMultimap<ReviewerState, Account.Id> getReviewers() {
    return reviewers;
  }

  /**
   * @return submit records stored during the most recent submit; only for
   *     changes that were actually submitted.
   */
  public ImmutableList<SubmitRecord> getSubmitRecords() {
    return submitRecords;
  }

  /** @return change messages by patch set, in chronological order. */
  public ImmutableListMultimap<PatchSet.Id, ChangeMessage> getChangeMessages() {
    return changeMessages;
  }

  /** @return all published inline comments on the current change*/
  public ImmutableListMultimap<PatchSet.Id, PatchLineComment> getPublishedComments() {
    return publishedComments;
  }

  /** @return the NoteMap */
  public NoteMap getNoteMap() {
    return noteMap;
  }

  @Override
  protected String getRefName() {
    return ChangeNoteUtil.changeRefName(change.getId());
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    ObjectId rev = getRevision();
    if (rev == null) {
      loadDefaults();
      return;
    }
    RevWalk walk = new RevWalk(reader);
    try {
      Parser parser = new Parser(change, rev, walk, repoManager);
      parser.parseAll();

      if (parser.status != null) {
        change.setStatus(parser.status);
      }
      approvals = parser.buildApprovals();
      changeMessages = parser.buildMessages();
      publishedComments =
          ImmutableListMultimap.copyOf(parser.publishedComments);
      noteMap = parser.publishedCommentNoteMap;

      ImmutableSetMultimap.Builder<ReviewerState, Account.Id> reviewers =
          ImmutableSetMultimap.builder();
      for (Map.Entry<Account.Id, ReviewerState> e
          : parser.reviewers.entrySet()) {
        reviewers.put(e.getValue(), e.getKey());
      }
      this.reviewers = reviewers.build();

      submitRecords = ImmutableList.copyOf(parser.submitRecords);
    } catch (ParseException e1) {
      // TODO(yyonas): figure out how to handle this exception
    } finally {
      walk.release();
    }
  }

  private void loadDefaults() {
    approvals = ImmutableListMultimap.of();
    reviewers = ImmutableSetMultimap.of();
    submitRecords = ImmutableList.of();
    changeMessages = ImmutableListMultimap.of();
    publishedComments = ImmutableListMultimap.of();
  }

  @Override
  protected boolean onSave(CommitBuilder commit) {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " is read-only");
  }
}
