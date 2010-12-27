package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;

import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.notes.NoteMapMerger;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

class CreateCodeReviewNotes {
  static final String REFS_NOTES_SUBMITTED = "refs/notes/submitted";
  private static final int MAX_LOCK_FAILURE_CALLS = 10;
  private static final int SLEEP_ON_LOCK_FAILURE_MS = 25;

  private final ReviewDb schema;
  private final Repository db;
  private final PersonIdent gerritIdent;
  private final RevWalk revWalk;
  private final ObjectInserter inserter;

  private RevCommit baseCommit;
  private NoteMap base;

  private RevCommit oursCommit;
  private NoteMap ours;

  private List<CodeReviewCommit> commits;
  private PersonIdent author;

  CreateCodeReviewNotes(ReviewDb reviewDb, Repository db,
      PersonIdent gerritIdent) {
    schema = reviewDb;
    this.db = db;
    this.gerritIdent = gerritIdent;
    revWalk = new RevWalk(db);
    inserter = db.newObjectInserter();
  }

  void create(List<CodeReviewCommit> commits, PersonIdent author)
      throws MergeException {
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
            throw new MergeException(
                "Couldn't create code review notes. Failed to lock the ref: "
                    + REFS_NOTES_SUBMITTED);
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
          throw new MergeException(
              "Couldn't create code review notes because of IO_FAILURE");

        } else {
          break;
        }
      }

    } catch (IOException e) {
      throw new MergeException("Couldn't create code review notes", e);
    } catch (InterruptedException e) {
      throw new MergeException("Couldn't create code review notes", e);
    }
  }

  private void setBase() throws IOException {
    Ref notesBranch = db.getRef(REFS_NOTES_SUBMITTED);
    if (notesBranch != null) {
      baseCommit = revWalk.parseCommit(notesBranch.getObjectId());
      base = NoteMap.read(revWalk.getObjectReader(), baseCommit);
    }
  }

  private void setOurs() throws IOException {
    if (baseCommit != null) {
      ours = NoteMap.read(db.newObjectReader(), baseCommit);
    } else {
      ours = NoteMap.newEmptyMap();
    }

    StringBuilder message =
        new StringBuilder("Submitted the following changes:");
    for (CodeReviewCommit c : commits) {
      if (ours.contains(c)) {
        throw new IllegalStateException(
            "Review summary note already exists for commit: " + c
                + ". Merging of notes not yet supported");
      }
      ObjectId id = inserter.insert(Constants.OBJ_BLOB, createNoteContent(c));
      inserter.flush();
      RevBlob blob = revWalk.lookupBlob(id);
      ours.set(c, blob);

      message.append(" ");
      message.append(c.change.getId());
    }
    message.append("\n");

    if (baseCommit != null) {
      oursCommit = createCommit(ours, author, message.toString(), baseCommit);
    } else {
      oursCommit = createCommit(ours, author, message.toString());
    }
  }

  private byte[] createNoteContent(CodeReviewCommit commit)
      throws UnsupportedEncodingException {
    try {
      ReviewNoteHeaderFormatter formatter = new ReviewNoteHeaderFormatter();
      formatter.appendChangeId(commit.change.getId());
      ResultSet<PatchSetApproval> approvals =
          schema.patchSetApprovals().byPatchSet(commit.patchsetId);
      PatchSetApproval submit = null;
      for (PatchSetApproval a : approvals) {
        if (ApprovalCategory.SUBMIT.equals(a.getCategoryId())) {
          submit = a;
        } else {
          formatter.appendApproval(
              schema.approvalCategories().get(a.getCategoryId()), a.getValue(),
              schema.accounts().get(a.getAccountId()));
        }
      }
      formatter.appendBranch(schema.projects().get(commit.change.getProject()),
          commit.change.getDest());
      formatter.appendSubmittedBy(schema.accounts().get(submit.getAccountId()));
      formatter.appendSubmittedOn(submit.getGranted());
      return formatter.toString().getBytes("UTF-8");
    } catch (OrmException e) {
      throw new RuntimeException(e);
    }
  }

  private RevCommit createCommit(NoteMap map, PersonIdent author,
      String message, RevCommit... parents) throws IOException {
    ObjectInserter inserter = db.newObjectInserter();
    CommitBuilder b = new CommitBuilder();
    b.setTreeId(map.writeTree(inserter));
    b.setAuthor(author);
    b.setCommitter(gerritIdent);
    if (parents.length > 0) {
      b.setParentIds(parents);
    }
    b.setMessage(message);
    return revWalk.parseCommit(inserter.insert(b));
  }


  private RefUpdate createRefUpdate(ObjectId newObjectId, ObjectId expectedOldObjectId) throws IOException {
    RefUpdate refUpdate = db.updateRef(REFS_NOTES_SUBMITTED);
    refUpdate.setNewObjectId(newObjectId);
    if (expectedOldObjectId == null) {
      refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
    } else {
      refUpdate.setExpectedOldObjectId(expectedOldObjectId);
    }
    return refUpdate;
  }
}
