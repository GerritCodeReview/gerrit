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

import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.DefaultNoteMerger;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.notes.NoteMapMerger;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.List;

/**
 * This class create code review notes for given {@link CodeReviewCommit}s.
 * <p>
 * After the {@link #create(List, PersonIdent)} method is invoked once this
 * instance must not be reused. Create a new instance of this class if needed.
 */
public class CreateCodeReviewNotes {
  public interface Factory {
    CreateCodeReviewNotes create(Repository db);
  }

  static final String REFS_NOTES_REVIEW = "refs/notes/review";
  private static final int MAX_LOCK_FAILURE_CALLS = 10;
  private static final int SLEEP_ON_LOCK_FAILURE_MS = 25;
  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private final ReviewDb schema;
  private final PersonIdent gerritIdent;
  private final AccountCache accountCache;
  private final ApprovalTypes approvalTypes;
  private final String canonicalWebUrl;
  private final Repository db;
  private final RevWalk revWalk;
  private final ObjectInserter inserter;
  private final ObjectReader reader;

  private RevCommit baseCommit;
  private NoteMap base;

  private RevCommit oursCommit;
  private NoteMap ours;

  private List<CodeReviewCommit> commits;
  private PersonIdent author;

  @Inject
  CreateCodeReviewNotes(final ReviewDb reviewDb,
      @GerritPersonIdent final PersonIdent gerritIdent,
      final AccountCache accountCache,
      final ApprovalTypes approvalTypes,
      @CanonicalWebUrl final String canonicalWebUrl,
      @Assisted final Repository db) {
    schema = reviewDb;
    this.gerritIdent = gerritIdent;
    this.accountCache = accountCache;
    this.approvalTypes = approvalTypes;
    this.canonicalWebUrl = canonicalWebUrl;
    this.db = db;

    revWalk = new RevWalk(db);
    inserter = db.newObjectInserter();
    reader = db.newObjectReader();
  }

  public void create(List<CodeReviewCommit> commits, PersonIdent author)
      throws CodeReviewNoteCreationException {
    try {
      this.commits = commits;
      this.author = author;
      setBase();
      setOurs();

      int remainingLockFailureCalls = MAX_LOCK_FAILURE_CALLS;
      RefUpdate refUpdate = createRefUpdate(oursCommit, baseCommit);

      for (;;) {
        Result result = refUpdate.update();

        if (result == Result.LOCK_FAILURE) {
          if (--remainingLockFailureCalls > 0) {
            Thread.sleep(SLEEP_ON_LOCK_FAILURE_MS);
          } else {
            throw new CodeReviewNoteCreationException(
                "Failed to lock the ref: " + REFS_NOTES_REVIEW);
          }

        } else if (result == Result.REJECTED) {
          RevCommit theirsCommit =
              revWalk.parseCommit(refUpdate.getOldObjectId());
          NoteMap theirs =
              NoteMap.read(revWalk.getObjectReader(), theirsCommit);
          NoteMapMerger merger = new NoteMapMerger(db);
          NoteMap merged = merger.merge(base, ours, theirs);
          RevCommit mergeCommit =
              createCommit(merged, gerritIdent, "Merged note commits\n",
                  theirsCommit, oursCommit);
          refUpdate = createRefUpdate(mergeCommit, theirsCommit);
          remainingLockFailureCalls = MAX_LOCK_FAILURE_CALLS;

        } else if (result == Result.IO_FAILURE) {
          throw new CodeReviewNoteCreationException(
              "Couldn't create code review notes because of IO_FAILURE");
        } else {
          break;
        }
      }

    } catch (IOException e) {
      throw new CodeReviewNoteCreationException(e);
    } catch (InterruptedException e) {
      throw new CodeReviewNoteCreationException(e);
    } finally {
      reader.release();
      inserter.release();
      revWalk.release();
    }
  }

  private void setBase() throws IOException {
    Ref notesBranch = db.getRef(REFS_NOTES_REVIEW);
    if (notesBranch != null) {
      baseCommit = revWalk.parseCommit(notesBranch.getObjectId());
      base = NoteMap.read(revWalk.getObjectReader(), baseCommit);
    }
  }

  private void setOurs() throws IOException, CodeReviewNoteCreationException {
    if (baseCommit != null) {
      ours = NoteMap.read(db.newObjectReader(), baseCommit);
    } else {
      ours = NoteMap.newEmptyMap();
    }

    StringBuilder message =
        new StringBuilder("Update notes for submitted changes\n\n");
    for (CodeReviewCommit c : commits) {
      ObjectId noteContent = createNoteContent(c);
      if (ours.contains(c)) {
        // merge the existing and the new note as if they are both new
        // means: base == null
        // there is not really a common ancestry for these two note revisions
        // use the same NoteMerger that is used from the NoteMapMerger
        DefaultNoteMerger noteMerger = new DefaultNoteMerger();
        Note newNote = new Note(c, noteContent);
        noteContent =
            noteMerger.merge(null, newNote, base.getNote(c), reader, inserter)
                .getData();
      }
      ours.set(c, noteContent);

      message.append("* ").append(c.getShortMessage()).append("\n");
    }

    if (baseCommit != null) {
      oursCommit = createCommit(ours, author, message.toString(), baseCommit);
    } else {
      oursCommit = createCommit(ours, author, message.toString());
    }
  }

  private ObjectId createNoteContent(CodeReviewCommit commit)
      throws CodeReviewNoteCreationException, IOException {
    try {
      ReviewNoteHeaderFormatter formatter =
        new ReviewNoteHeaderFormatter(author.getTimeZone());
      final List<String> idList = commit.getFooterLines(CHANGE_ID);
      if (idList.isEmpty())
        formatter.appendChangeId(commit.change.getKey());
      ResultSet<PatchSetApproval> approvals =
        schema.patchSetApprovals().byPatchSet(commit.patchsetId);
      PatchSetApproval submit = null;
      for (PatchSetApproval a : approvals) {
        if (ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
          submit = a;
        } else {
          formatter.appendApproval(
              approvalTypes.getApprovalType(a.getCategoryId()).getCategory(),
              a.getValue(),
              accountCache.get(a.getAccountId()).getAccount());
        }
      }

      formatter.appendSubmittedBy(accountCache.get(submit.getAccountId()).getAccount());
      formatter.appendSubmittedAt(submit.getGranted());

      formatter.appendReviewedOn(canonicalWebUrl, commit.change.getId());
      formatter.appendProject(commit.change.getProject().get());
      formatter.appendBranch(commit.change.getDest());
      return inserter.insert(Constants.OBJ_BLOB, formatter.toString().getBytes("UTF-8"));
    } catch (OrmException e) {
      throw new CodeReviewNoteCreationException(commit, e);
    }
  }

  private RevCommit createCommit(NoteMap map, PersonIdent author,
      String message, RevCommit... parents) throws IOException {
    CommitBuilder b = new CommitBuilder();
    b.setTreeId(map.writeTree(inserter));
    b.setAuthor(author);
    b.setCommitter(gerritIdent);
    if (parents.length > 0) {
      b.setParentIds(parents);
    }
    b.setMessage(message);
    ObjectId commitId = inserter.insert(b);
    inserter.flush();
    return revWalk.parseCommit(commitId);
  }


  private RefUpdate createRefUpdate(ObjectId newObjectId, ObjectId expectedOldObjectId) throws IOException {
    RefUpdate refUpdate = db.updateRef(REFS_NOTES_REVIEW);
    refUpdate.setNewObjectId(newObjectId);
    if (expectedOldObjectId == null) {
      refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
    } else {
      refUpdate.setExpectedOldObjectId(expectedOldObjectId);
    }
    return refUpdate;
  }
}
