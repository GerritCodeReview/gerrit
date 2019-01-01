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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerIdProvider;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.PackInserter;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
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

@Singleton
public class CommentJsonMigrator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class ProjectMigrationResult {
    public int skipped;
    public boolean ok;
    public List<String> refsUpdated;
  }

  private final LegacyChangeNoteRead legacyChangeNoteRead;
  private final ChangeNoteJson changeNoteJson;
  private final AllUsersName allUsers;

  @Inject
  CommentJsonMigrator(
      ChangeNoteJson changeNoteJson,
      GerritServerIdProvider gerritServerIdProvider,
      AllUsersName allUsers) {
    this.changeNoteJson = changeNoteJson;
    this.allUsers = allUsers;
    this.legacyChangeNoteRead = new LegacyChangeNoteRead(gerritServerIdProvider.get());
  }

  CommentJsonMigrator(ChangeNoteJson changeNoteJson, String serverId, AllUsersName allUsers) {
    this.changeNoteJson = changeNoteJson;
    this.legacyChangeNoteRead = new LegacyChangeNoteRead(serverId);
    this.allUsers = allUsers;
  }

  public ProjectMigrationResult migrateProject(
      Project.NameKey project, Repository repo, boolean dryRun) {
    ProjectMigrationResult progress = new ProjectMigrationResult();
    progress.ok = true;
    progress.skipped = 0;
    progress.refsUpdated = ImmutableList.of();
    try (RevWalk rw = new RevWalk(repo);
        ObjectInserter ins = newPackInserter(repo)) {
      BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
      bru.setAllowNonFastForwards(true);
      progress.ok &= migrateChanges(project, repo, rw, ins, bru);
      if (project.equals(allUsers)) {
        progress.ok &= migrateDrafts(allUsers, repo, rw, ins, bru);
      }

      progress.refsUpdated =
          bru.getCommands().stream().map(c -> c.getRefName()).collect(toImmutableList());
      if (!bru.getCommands().isEmpty()) {
        if (!dryRun) {
          ins.flush();
          RefUpdateUtil.executeChecked(bru, rw);
        }
      } else {
        progress.skipped++;
      }
    } catch (IOException e) {
      progress.ok = false;
    }

    return progress;
  }

  private boolean migrateChanges(
      Project.NameKey project, Repository repo, RevWalk rw, ObjectInserter ins, BatchRefUpdate bru)
      throws IOException {
    boolean ok = true;
    for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES)) {
      Change.Id changeId = Change.Id.fromRef(ref.getName());
      if (changeId == null || !ref.getName().equals(RefNames.changeMetaRef(changeId))) {
        continue;
      }
      ok &= migrateOne(project, rw, ins, bru, Status.PUBLISHED, changeId, ref);
    }
    return ok;
  }

  private boolean migrateDrafts(
      Project.NameKey allUsers,
      Repository allUsersRepo,
      RevWalk rw,
      ObjectInserter ins,
      BatchRefUpdate bru)
      throws IOException {
    boolean ok = true;
    for (Ref ref : allUsersRepo.getRefDatabase().getRefsByPrefix(RefNames.REFS_DRAFT_COMMENTS)) {
      Change.Id changeId = Change.Id.fromAllUsersRef(ref.getName());
      if (changeId == null) {
        continue;
      }
      ok &= migrateOne(allUsers, rw, ins, bru, Status.DRAFT, changeId, ref);
    }
    return ok;
  }

  private boolean migrateOne(
      Project.NameKey project,
      RevWalk rw,
      ObjectInserter ins,
      BatchRefUpdate bru,
      Status status,
      Change.Id changeId,
      Ref ref) {
    ObjectId oldId = ref.getObjectId();
    try {
      if (!hasAnyLegacyComments(rw, oldId)) {
        return true;
      }
    } catch (IOException e) {
      logger.atInfo().log(
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
            RevisionNoteMap.parse(
                changeNoteJson, legacyChangeNoteRead, changeId, reader, noteMap, status);
        RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(revNoteMap);

        for (RevId revId : revNoteMap.revisionNotes.keySet()) {
          // Call cache.get on each known RevId to read the old note in whichever format, then write
          // the note in JSON format.
          byte[] data = cache.get(revId).build(changeNoteJson);
          noteMap.set(ObjectId.fromString(revId.get()), ins.insert(OBJ_BLOB, data));
        }
        cb.setTreeId(noteMap.writeTree(ins));
        newId = ins.insert(cb);
      }

      bru.addCommand(new ReceiveCommand(oldId, newId, ref.getName()));
      return true;
    } catch (ConfigInvalidException | IOException | LargeObjectException e) {
      logger.atInfo().log(String.format("Error migrating change %s in %s", changeId, project), e);
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
    // history (and thus returning true) in this case.
    RevCommit c;
    while ((c = rw.next()) != null) {
      NoteMap noteMap = NoteMap.read(reader, c);
      for (Note note : noteMap) {
        // Match pre-parsing logic in RevisionNote#parse().
        ObjectLoader objectLoader = reader.open(note.getData(), OBJ_BLOB);
        if (objectLoader.isLarge()) {
          throw new IOException(String.format("Comment note %s is too large", note.name()));
        }
        byte[] raw = objectLoader.getCachedBytes();
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

  private static ObjectInserter newPackInserter(Repository repo) {
    if (!(repo instanceof FileRepository)) {
      return repo.newObjectInserter();
    }
    PackInserter ins = ((FileRepository) repo).getObjectDatabase().newPackInserter();
    ins.checkExisting(false);
    return ins;
  }
}
