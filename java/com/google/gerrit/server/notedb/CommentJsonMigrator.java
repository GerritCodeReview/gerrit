// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.server.notedb.RevisionNote.MAX_NOTE_SZ;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.MutableInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CommentJsonMigrator {
  private static final Logger log = LoggerFactory.getLogger(CommentJsonMigrator.class);

  private final ChangeNoteUtil noteUtil;

  @Inject
  CommentJsonMigrator(ChangeNoteUtil noteUtil) {
    this.noteUtil = noteUtil;
  }

  public boolean migrateChanges(
      Project.NameKey project, Repository repo, RevWalk rw, ObjectInserter ins, BatchRefUpdate bru)
      throws IOException {
    boolean ok = true;
    for (Ref ref : repo.getRefDatabase().getRefs(RefNames.REFS_CHANGES).values()) {
      String refName = ref.getName();
      Change.Id changeId = Change.Id.fromRef(refName);
      if (changeId == null || !refName.equals(RefNames.changeMetaRef(changeId))) {
        continue;
      }
      ok &=
          migrateOne(
              project,
              rw,
              ins,
              bru,
              PatchLineComment.Status.PUBLISHED,
              refName,
              changeId,
              ref.getObjectId());
    }
    return ok;
  }

  public boolean migrateDrafts(
      Project.NameKey allUsers,
      Repository allUsersRepo,
      RevWalk rw,
      ObjectInserter ins,
      BatchRefUpdate bru)
      throws IOException {
    boolean ok = true;
    for (Ref ref : allUsersRepo.getRefDatabase().getRefs(RefNames.REFS_DRAFT_COMMENTS).values()) {
      Change.Id changeId = Change.Id.fromAllUsersRef(ref.getName());
      if (changeId == null) {
        continue;
      }
      ok &=
          migrateOne(
              allUsers,
              rw,
              ins,
              bru,
              PatchLineComment.Status.DRAFT,
              ref.getName(),
              changeId,
              ref.getObjectId());
    }
    return ok;
  }

  private boolean migrateOne(
      Project.NameKey project,
      RevWalk rw,
      ObjectInserter ins,
      BatchRefUpdate bru,
      PatchLineComment.Status status,
      String refName,
      Change.Id changeId,
      ObjectId oldId) {
    try {
      if (!hasAnyLegacyComments(rw, oldId)) {
        return true;
      }
    } catch (IOException e) {
      log.info(
          String.format(
              "Error reading change %s in %s; attempting migration anyway", changeId, project),
          e);
    }

    try {
      reset(rw, oldId);

      ObjectReader reader = rw.getObjectReader();
      ObjectId newId = null;
      RevCommit c;
      while ((c = rw.next()) != null) {
        CommitBuilder cb = new CommitBuilder();
        cb.setAuthor(c.getAuthorIdent());
        cb.setCommitter(c.getCommitterIdent());
        cb.setMessage(c.getFullMessage());
        cb.setEncoding(c.getEncoding());
        if (newId != null) {
          cb.setParentId(newId);
        }

        // Read/write using the low-level RevisionNote API, which works regardless of NotesMigration
        // state.
        NoteMap noteMap = NoteMap.read(reader, c);
        RevisionNoteMap<ChangeRevisionNote> revNoteMap =
            RevisionNoteMap.parse(noteUtil, changeId, reader, noteMap, status);
        RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(revNoteMap);

        for (RevId revId : revNoteMap.revisionNotes.keySet()) {
          // Call cache.get on each known RevId to read the old note in whichever format, then write
          // the note in JSON format.
          byte[] data = cache.get(revId).build(noteUtil, true);
          noteMap.set(ObjectId.fromString(revId.get()), ins.insert(OBJ_BLOB, data));
        }
        cb.setTreeId(noteMap.writeTree(ins));
        newId = ins.insert(cb);
      }

      bru.addCommand(new ReceiveCommand(oldId, newId, refName));
      return true;
    } catch (ConfigInvalidException | IOException e) {
      log.info(String.format("Error migrating change %s in %s", changeId, project), e);
      return false;
    }
  }

  private static boolean hasAnyLegacyComments(RevWalk rw, ObjectId id) throws IOException {
    ObjectReader reader = rw.getObjectReader();
    reset(rw, id);

    // Check the note map at each commit, not just the tip. It's possible that the server switched
    // from legacy to JSON partway through its history, which would have mixed legacy/JSON comments
    // in its history. Although the tip commit would continue to parse once we remove the legacy
    // parser, our goal is really to expunge all vestiges of the old format, which implies rewriting
    // history (i.e. returning true) in this case.
    RevCommit c;
    while ((c = rw.next()) != null) {
      NoteMap noteMap = NoteMap.read(reader, c);
      for (Note note : noteMap) {
        // Match pre-parsing logic in RevisionNote#parse().
        byte[] raw = reader.open(note.getData(), OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
        MutableInteger p = new MutableInteger();
        RevisionNote.trimLeadingEmptyLines(raw, p);
        if (!ChangeRevisionNote.isJson(raw, p.value)) {
          return true;
        }
      }
    }
    return false;
  }

  private static void reset(RevWalk rw, ObjectId id) throws IOException {
    rw.reset();
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.REVERSE);
    rw.markStart(rw.parseCommit(id));
  }
}
