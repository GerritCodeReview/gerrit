// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.gerrit.server.git.GitRepositoryManager.REFS_NOTES_REVIEW;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

/**
 * This class create code review notes for given {@link CodeReviewCommit}s.
 * <p>
 * After the {@link #create(List, PersonIdent)} method is invoked once this
 * instance must not be reused. Create a new instance of this class if needed.
 */
public class CreateCodeReviewNotes {
  public interface Factory {
    CreateCodeReviewNotes create(ReviewDb reviewDb, Repository db);
  }

  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private final AccountCache accountCache;
  private final ApprovalTypes approvalTypes;
  private final String canonicalWebUrl;
  private final String anonymousCowardName;
  private final ReviewDb schema;
  private final Repository db;

  private PersonIdent author;

  private RevWalk revWalk;
  private ObjectInserter inserter;

  private final NotesBranchUtil.Factory notesBranchUtilFactory;

  private

  @Inject
  CreateCodeReviewNotes(
      @GerritPersonIdent final PersonIdent gerritIdent,
      final AccountCache accountCache,
      final ApprovalTypes approvalTypes,
      final @Nullable @CanonicalWebUrl String canonicalWebUrl,
      final @AnonymousCowardName String anonymousCowardName,
      final NotesBranchUtil.Factory notesBranchUtilFactory,
      final @Assisted  ReviewDb reviewDb,
      final @Assisted  Repository db) {
    this.author = gerritIdent;
    this.accountCache = accountCache;
    this.approvalTypes = approvalTypes;
    this.canonicalWebUrl = canonicalWebUrl;
    this.anonymousCowardName = anonymousCowardName;
    this.notesBranchUtilFactory = notesBranchUtilFactory;
    schema = reviewDb;
    this.db = db;
  }

  public void create(List<CodeReviewCommit> commits, PersonIdent author)
      throws CodeReviewNoteCreationException {
    try {
      revWalk = new RevWalk(db);
      inserter = db.newObjectInserter();
      if (author != null) {
        this.author = author;
      }

      NoteMap notes = NoteMap.newEmptyMap();
      StringBuilder message =
          new StringBuilder("Update notes for submitted changes\n\n");
      for (CodeReviewCommit c : commits) {
        notes.set(c, createNoteContent(c.change, c));
        message.append("* ").append(c.getShortMessage()).append("\n");
      }

      NotesBranchUtil notesBranchUtil = notesBranchUtilFactory.create(db);
      notesBranchUtil.commitAllNotes(notes, REFS_NOTES_REVIEW, author,
          message.toString());
    } catch (IOException e) {
      throw new CodeReviewNoteCreationException(e);
    } finally {
      revWalk.release();
      inserter.release();
    }
  }

  public void create(List<Change> changes, PersonIdent author,
      String commitMessage, ProgressMonitor monitor) throws OrmException,
      IOException, CodeReviewNoteCreationException {
    try {
      revWalk = new RevWalk(db);
      inserter = db.newObjectInserter();
      if (author != null) {
        this.author = author;
      }
      if (monitor == null) {
        monitor = NullProgressMonitor.INSTANCE;
      }

      NoteMap notes = NoteMap.newEmptyMap();
      for (Change c : changes) {
        monitor.update(1);
        PatchSet ps = schema.patchSets().get(c.currentPatchSetId());
        if (ps == null) {
          continue;
        }
        ObjectId commitId = ObjectId.fromString(ps.getRevision().get());
        notes.set(commitId, createNoteContent(c, commitId));
      }

      NotesBranchUtil notesBranchUtil = notesBranchUtilFactory.create(db);
      notesBranchUtil.commitAllNotes(notes, REFS_NOTES_REVIEW, author,
          commitMessage);
    } finally {
      revWalk.release();
      inserter.release();
    }
  }

  private ObjectId createNoteContent(Change change, ObjectId commit)
      throws CodeReviewNoteCreationException, IOException  {
    if (!(commit instanceof RevCommit)) {
      commit = revWalk.parseCommit(commit);
    }
    return createNoteContent(change, (RevCommit) commit);
  }

  private ObjectId createNoteContent(Change change, RevCommit commit)
      throws CodeReviewNoteCreationException, IOException {
    try {
      ReviewNoteHeaderFormatter formatter =
          new ReviewNoteHeaderFormatter(author.getTimeZone(),
              anonymousCowardName);
      final List<String> idList = commit.getFooterLines(CHANGE_ID);
      if (idList.isEmpty())
        formatter.appendChangeId(change.getKey());
      ResultSet<PatchSetApproval> approvals =
        schema.patchSetApprovals().byPatchSet(change.currentPatchSetId());
      PatchSetApproval submit = null;
      for (PatchSetApproval a : approvals) {
        if (a.getValue() == 0) {
          // Ignore 0 values.
        } else if (ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
          submit = a;
        } else {
          ApprovalType type = approvalTypes.byId(a.getCategoryId());
          if (type != null) {
            formatter.appendApproval(
                type.getCategory(),
                a.getValue(),
                accountCache.get(a.getAccountId()).getAccount());
          }
        }
      }

      if (submit != null) {
        formatter.appendSubmittedBy(accountCache.get(submit.getAccountId()).getAccount());
        formatter.appendSubmittedAt(submit.getGranted());
      }
      if (canonicalWebUrl != null) {
        formatter.appendReviewedOn(canonicalWebUrl, change.getId());
      }
      formatter.appendProject(change.getProject().get());
      formatter.appendBranch(change.getDest());
      return inserter.insert(Constants.OBJ_BLOB, formatter.toString().getBytes("UTF-8"));
    } catch (OrmException e) {
      throw new CodeReviewNoteCreationException(commit, e);
    }
  }
}
