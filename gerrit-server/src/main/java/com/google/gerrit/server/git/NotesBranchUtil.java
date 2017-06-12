// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.notes.NoteMapMerger;
import org.eclipse.jgit.notes.NoteMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** A utility class for updating a notes branch with automatic merge of note trees. */
public class NotesBranchUtil {
  public interface Factory {
    NotesBranchUtil create(Project.NameKey project, Repository db, ObjectInserter inserter);
  }

  private static final int MAX_LOCK_FAILURE_CALLS = 10;
  private static final int SLEEP_ON_LOCK_FAILURE_MS = 25;

  private final PersonIdent gerritIdent;
  private final GitReferenceUpdated gitRefUpdated;
  private final Project.NameKey project;
  private final Repository db;
  private final ObjectInserter inserter;

  private RevCommit baseCommit;
  private NoteMap base;

  private RevCommit oursCommit;
  private NoteMap ours;

  private RevWalk revWalk;
  private ObjectReader reader;
  private boolean overwrite;

  private ReviewNoteMerger noteMerger;

  @Inject
  public NotesBranchUtil(
      @GerritPersonIdent final PersonIdent gerritIdent,
      final GitReferenceUpdated gitRefUpdated,
      @Assisted Project.NameKey project,
      @Assisted Repository db,
      @Assisted ObjectInserter inserter) {
    this.gerritIdent = gerritIdent;
    this.gitRefUpdated = gitRefUpdated;
    this.project = project;
    this.db = db;
    this.inserter = inserter;
  }

  /**
   * Create a new commit in the {@code notesBranch} by updating existing or creating new notes from
   * the {@code notes} map.
   *
   * @param notes map of notes
   * @param notesBranch notes branch to update
   * @param commitAuthor author of the commit in the notes branch
   * @param commitMessage for the commit in the notes branch
   * @throws IOException
   * @throws ConcurrentRefUpdateException
   */
  public final void commitAllNotes(
      NoteMap notes, String notesBranch, PersonIdent commitAuthor, String commitMessage)
      throws IOException, ConcurrentRefUpdateException {
    this.overwrite = true;
    commitNotes(notes, notesBranch, commitAuthor, commitMessage);
  }

  /**
   * Create a new commit in the {@code notesBranch} by creating not yet existing notes from the
   * {@code notes} map. The notes from the {@code notes} map which already exist in the note-tree of
   * the tip of the {@code notesBranch} will not be updated.
   *
   * @param notes map of notes
   * @param notesBranch notes branch to update
   * @param commitAuthor author of the commit in the notes branch
   * @param commitMessage for the commit in the notes branch
   * @return map with those notes from the {@code notes} that were newly created
   * @throws IOException
   * @throws ConcurrentRefUpdateException
   */
  public final NoteMap commitNewNotes(
      NoteMap notes, String notesBranch, PersonIdent commitAuthor, String commitMessage)
      throws IOException, ConcurrentRefUpdateException {
    this.overwrite = false;
    commitNotes(notes, notesBranch, commitAuthor, commitMessage);
    NoteMap newlyCreated = NoteMap.newEmptyMap();
    for (Note n : notes) {
      if (base == null || !base.contains(n)) {
        newlyCreated.set(n, n.getData());
      }
    }
    return newlyCreated;
  }

  private void commitNotes(
      NoteMap notes, String notesBranch, PersonIdent commitAuthor, String commitMessage)
      throws IOException, ConcurrentRefUpdateException {
    try {
      revWalk = new RevWalk(db);
      reader = db.newObjectReader();
      loadBase(notesBranch);
      if (overwrite) {
        addAllNotes(notes);
      } else {
        addNewNotes(notes);
      }
      if (base != null) {
        oursCommit = createCommit(ours, commitAuthor, commitMessage, baseCommit);
      } else {
        oursCommit = createCommit(ours, commitAuthor, commitMessage);
      }
      updateRef(notesBranch);
    } finally {
      revWalk.close();
      reader.close();
    }
  }

  private void addNewNotes(NoteMap notes) throws IOException {
    for (Note n : notes) {
      if (!ours.contains(n)) {
        ours.set(n, n.getData());
      }
    }
  }

  private void addAllNotes(NoteMap notes) throws IOException {
    for (Note n : notes) {
      if (ours.contains(n)) {
        // Merge the existing and the new note as if they are both new,
        // means: base == null
        // There is no really a common ancestry for these two note revisions
        ObjectId noteContent =
            getNoteMerger().merge(null, n, ours.getNote(n), reader, inserter).getData();
        ours.set(n, noteContent);
      } else {
        ours.set(n, n.getData());
      }
    }
  }

  private NoteMerger getNoteMerger() {
    if (noteMerger == null) {
      noteMerger = new ReviewNoteMerger();
    }
    return noteMerger;
  }

  private void loadBase(String notesBranch) throws IOException {
    Ref branch = db.getRefDatabase().exactRef(notesBranch);
    if (branch != null) {
      baseCommit = revWalk.parseCommit(branch.getObjectId());
      base = NoteMap.read(revWalk.getObjectReader(), baseCommit);
    }
    if (baseCommit != null) {
      ours = NoteMap.read(revWalk.getObjectReader(), baseCommit);
    } else {
      ours = NoteMap.newEmptyMap();
    }
  }

  private RevCommit createCommit(
      NoteMap map, PersonIdent author, String message, RevCommit... parents) throws IOException {
    CommitBuilder b = new CommitBuilder();
    b.setTreeId(map.writeTree(inserter));
    b.setAuthor(author != null ? author : gerritIdent);
    b.setCommitter(gerritIdent);
    if (parents.length > 0) {
      b.setParentIds(parents);
    }
    b.setMessage(message);
    ObjectId commitId = inserter.insert(b);
    inserter.flush();
    return revWalk.parseCommit(commitId);
  }

  private void updateRef(String notesBranch)
      throws IOException, MissingObjectException, IncorrectObjectTypeException,
          CorruptObjectException, ConcurrentRefUpdateException {
    if (baseCommit != null && oursCommit.getTree().equals(baseCommit.getTree())) {
      // If the trees are identical, there is no change in the notes.
      // Avoid saving this commit as it has no new information.
      return;
    }

    int remainingLockFailureCalls = MAX_LOCK_FAILURE_CALLS;
    RefUpdate refUpdate = createRefUpdate(notesBranch, oursCommit, baseCommit);

    for (; ; ) {
      Result result = refUpdate.update();

      if (result == Result.LOCK_FAILURE) {
        if (--remainingLockFailureCalls > 0) {
          try {
            Thread.sleep(SLEEP_ON_LOCK_FAILURE_MS);
          } catch (InterruptedException e) {
            // ignore
          }
        } else {
          throw new ConcurrentRefUpdateException(
              "Failed to lock the ref: " + notesBranch, refUpdate.getRef(), result);
        }

      } else if (result == Result.REJECTED) {
        RevCommit theirsCommit = revWalk.parseCommit(refUpdate.getOldObjectId());
        NoteMap theirs = NoteMap.read(revWalk.getObjectReader(), theirsCommit);
        NoteMapMerger merger = new NoteMapMerger(db, getNoteMerger(), MergeStrategy.RESOLVE);
        NoteMap merged = merger.merge(base, ours, theirs);
        RevCommit mergeCommit =
            createCommit(merged, gerritIdent, "Merged note commits\n", theirsCommit, oursCommit);
        refUpdate = createRefUpdate(notesBranch, mergeCommit, theirsCommit);
        remainingLockFailureCalls = MAX_LOCK_FAILURE_CALLS;

      } else if (result == Result.IO_FAILURE) {
        throw new IOException("Couldn't update " + notesBranch + ". " + result.name());
      } else {
        gitRefUpdated.fire(project, refUpdate, null);
        break;
      }
    }
  }

  private RefUpdate createRefUpdate(
      String notesBranch, ObjectId newObjectId, ObjectId expectedOldObjectId) throws IOException {
    RefUpdate refUpdate = db.updateRef(notesBranch);
    refUpdate.setNewObjectId(newObjectId);
    if (expectedOldObjectId == null) {
      refUpdate.setExpectedOldObjectId(ObjectId.zeroId());
    } else {
      refUpdate.setExpectedOldObjectId(expectedOldObjectId);
    }
    return refUpdate;
  }
}
