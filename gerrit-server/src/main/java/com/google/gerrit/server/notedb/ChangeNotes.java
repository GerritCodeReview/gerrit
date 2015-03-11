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

import static com.google.gerrit.server.notedb.ChangeNoteUtil.GERRIT_PLACEHOLDER_HOST;
import static com.google.gerrit.server.notedb.CommentsInNotesUtil.getCommentPsId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Table;
import com.google.common.primitives.Ints;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** View of a single {@link Change} based on the log of its notes branch. */
public class ChangeNotes extends AbstractChangeNotes<ChangeNotes> {
  static final Ordering<PatchSetApproval> PSA_BY_TIME =
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

  public static Comparator<PatchLineComment> PatchLineCommentComparator =
      new Comparator<PatchLineComment>() {
    @Override
    public int compare(PatchLineComment c1, PatchLineComment c2) {
      String filename1 = c1.getKey().getParentKey().get();
      String filename2 = c2.getKey().getParentKey().get();
      return ComparisonChain.start()
          .compare(filename1, filename2)
          .compare(c1.getLine(), c2.getLine())
          .compare(c1.getWrittenOn(), c2.getWrittenOn())
          .result();
    }
  };

  public static ConfigInvalidException parseException(Change.Id changeId,
      String fmt, Object... args) {
    return new ConfigInvalidException("Change " + changeId + ": "
        + String.format(fmt, args));
  }

  public static Account.Id parseIdent(PersonIdent ident, Change.Id changeId)
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
    throw parseException(changeId, "invalid identity, expected <id>@%s: %s",
      GERRIT_PLACEHOLDER_HOST, email);
  }

  @Singleton
  public static class Factory {
    private final GitRepositoryManager repoManager;
    private final NotesMigration migration;
    private final AllUsersNameProvider allUsersProvider;

    @VisibleForTesting
    @Inject
    public Factory(GitRepositoryManager repoManager,
        NotesMigration migration,
        AllUsersNameProvider allUsersProvider) {
      this.repoManager = repoManager;
      this.migration = migration;
      this.allUsersProvider = allUsersProvider;
    }

    public ChangeNotes create(Change change) {
      return new ChangeNotes(repoManager, migration, allUsersProvider, change);
    }
  }

  private final Change change;
  private ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals;
  private ImmutableSetMultimap<ReviewerState, Account.Id> reviewers;
  private ImmutableList<Account.Id> allPastReviewers;
  private ImmutableList<SubmitRecord> submitRecords;
  private ImmutableListMultimap<PatchSet.Id, ChangeMessage> changeMessages;
  private ImmutableListMultimap<PatchSet.Id, PatchLineComment> commentsForBase;
  private ImmutableListMultimap<PatchSet.Id, PatchLineComment> commentsForPS;
  private ImmutableSet<String> hashtags;
  NoteMap noteMap;

  private final AllUsersName allUsers;
  private DraftCommentNotes draftCommentNotes;

  @VisibleForTesting
  public ChangeNotes(GitRepositoryManager repoManager, NotesMigration migration,
      AllUsersNameProvider allUsersProvider, Change change) {
    super(repoManager, migration, change.getId());
    this.allUsers = allUsersProvider.get();
    this.change = new Change(change);
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
   *
   * @return a ImmutableSet of all hashtags for this change sorted in alphabetical order.
   */
  public ImmutableSet<String> getHashtags() {
    return ImmutableSortedSet.copyOf(hashtags);
  }

  /**
   * @return a list of all users who have ever been a reviewer on this change.
   */
  public ImmutableList<Account.Id> getAllPastReviewers() {
    return allPastReviewers;
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

  /** @return inline comments on each patchset's base (side == 0). */
  public ImmutableListMultimap<PatchSet.Id, PatchLineComment>
      getBaseComments() {
    return commentsForBase;
  }

  /** @return inline comments on each patchset (side == 1). */
  public ImmutableListMultimap<PatchSet.Id, PatchLineComment>
      getPatchSetComments() {
    return commentsForPS;
  }

  public Table<PatchSet.Id, String, PatchLineComment> getDraftBaseComments(
      Account.Id author) throws OrmException {
    loadDraftComments(author);
    return draftCommentNotes.getDraftBaseComments();
  }

  public Table<PatchSet.Id, String, PatchLineComment> getDraftPsComments(
      Account.Id author) throws OrmException {
    loadDraftComments(author);
    return draftCommentNotes.getDraftPsComments();
  }

  /**
   * If draft comments have already been loaded for this author, then they will
   * not be reloaded. However, this method will load the comments if no draft
   * comments have been loaded or if the caller would like the drafts for
   * another author.
   */
  private void loadDraftComments(Account.Id author)
      throws OrmException {
    if (draftCommentNotes == null ||
        !author.equals(draftCommentNotes.getAuthor())) {
      draftCommentNotes = new DraftCommentNotes(repoManager, migration,
          allUsers, getChangeId(), author);
      draftCommentNotes.load();
    }
  }

  public boolean containsComment(PatchLineComment c) throws OrmException {
    if (containsCommentPublished(c)) {
      return true;
    }
    loadDraftComments(c.getAuthor());
    return draftCommentNotes.containsComment(c);
  }

  public boolean containsCommentPublished(PatchLineComment c) {
    PatchSet.Id psId = getCommentPsId(c);
    List<PatchLineComment> list = (c.getSide() == (short) 0)
        ? getBaseComments().get(psId)
        : getPatchSetComments().get(psId);
    for (PatchLineComment l : list) {
      if (c.getKey().equals(l.getKey())) {
        return true;
      }
    }
    return false;
  }

  /** @return the NoteMap */
  NoteMap getNoteMap() {
    return noteMap;
  }

  @Override
  protected String getRefName() {
    return ChangeNoteUtil.changeRefName(getChangeId());
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    ObjectId rev = getRevision();
    if (rev == null) {
      loadDefaults();
      return;
    }
    try (RevWalk walk = new RevWalk(reader);
        ChangeNotesParser parser =
          new ChangeNotesParser(change, rev, walk, repoManager)) {
      parser.parseAll();

      if (parser.status != null) {
        change.setStatus(parser.status);
      }
      approvals = parser.buildApprovals();
      changeMessages = parser.buildMessages();
      commentsForBase = ImmutableListMultimap.copyOf(parser.commentsForBase);
      commentsForPS = ImmutableListMultimap.copyOf(parser.commentsForPs);
      noteMap = parser.commentNoteMap;

      if (parser.hashtags != null) {
        hashtags = ImmutableSet.copyOf(parser.hashtags);
      } else {
        hashtags = ImmutableSet.of();
      }
      ImmutableSetMultimap.Builder<ReviewerState, Account.Id> reviewers =
          ImmutableSetMultimap.builder();
      for (Map.Entry<Account.Id, ReviewerState> e
          : parser.reviewers.entrySet()) {
        reviewers.put(e.getValue(), e.getKey());
      }
      this.reviewers = reviewers.build();
      this.allPastReviewers = ImmutableList.copyOf(parser.allPastReviewers);

      submitRecords = ImmutableList.copyOf(parser.submitRecords);
    }
  }

  @Override
  protected void loadDefaults() {
    approvals = ImmutableListMultimap.of();
    reviewers = ImmutableSetMultimap.of();
    submitRecords = ImmutableList.of();
    changeMessages = ImmutableListMultimap.of();
    commentsForBase = ImmutableListMultimap.of();
    commentsForPS = ImmutableListMultimap.of();
    hashtags = ImmutableSet.of();
  }

  @Override
  protected boolean onSave(CommitBuilder commit) {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " is read-only");
  }

  static Project.NameKey getProjectName(Change change) {
    return change.getProject();
  }

  @Override
  protected Project.NameKey getProjectName() {
    return getProjectName(getChange());
  }
}
