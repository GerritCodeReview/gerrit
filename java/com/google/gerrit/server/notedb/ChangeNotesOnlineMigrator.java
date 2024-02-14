// Copyright (C) 2024 The Android Open Source Project
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

import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class ChangeNotesOnlineMigrator extends AbstractChangeUpdate {
  public interface Factory {
    ChangeNotesOnlineMigrator create(ChangeNotes notes);
  }

  private final ChangeNotes notes;
  private final NoteDbUtil noteDbUtil;
  private final AbstractChangeNotes.Args args;
  private final GitRepositoryManager gitRepositoryManager;

  @Inject
  ChangeNotesOnlineMigrator(
      NoteDbUtil noteDbUtil,
      ChangeNoteUtil noteUtil,
      AbstractChangeNotes.Args args,
      GitRepositoryManager gitRepositoryManager,
      InternalUser internalUser,
      @GerritPersonIdent PersonIdent serverIdent,
      @Assisted ChangeNotes notes) {
    super(notes, internalUser, serverIdent, noteUtil, Instant.now());
    this.args = args;
    this.notes = notes;
    this.noteDbUtil = noteDbUtil;
    this.gitRepositoryManager = gitRepositoryManager;
  }

  @Override
  public boolean isEmpty() {
    return !notes.isMutated();
  }

  @Override
  protected Project.NameKey getProjectName() {
    return notes.getProjectName();
  }

  @Override
  protected String getRefName() {
    return notes.getRefName();
  }

  @Override
  protected CommitBuilder applyImpl(RevWalk rw, ObjectInserter ins, ObjectId curr)
      throws IOException {
    RevisionNoteMap<ChangeRevisionNote> migratedRevisionNoteMap = getRevisionNoteMap(curr);

    RevisionNoteBuilder.Cache cache = new RevisionNoteBuilder.Cache(migratedRevisionNoteMap);
    Map<ObjectId, RevisionNoteBuilder> builders = cache.getBuilders();
    for (HumanComment c : notes.getHumanComments().values()) {
      cache.get(c.getCommitId()).putComment(c);
    }
    for (Map.Entry<ObjectId, RevisionNoteBuilder> e : builders.entrySet()) {
      ObjectId data = ins.insert(Constants.OBJ_BLOB, e.getValue().build(args.changeNoteJson));
      migratedRevisionNoteMap.noteMap.set(e.getKey(), data);
    }

    CommitBuilder cb = new CommitBuilder();
    cb.setTreeId(migratedRevisionNoteMap.noteMap.writeTree(ins));
    cb.setMessage("Persist mutations to notes");

    return cb;
  }

  private RevisionNoteMap<ChangeRevisionNote> getRevisionNoteMap(ObjectId tipId)
      throws IOException {
    try (Repository repo = gitRepositoryManager.openRepository(notes.getProjectName());
        ChangeNotesCommit.ChangeNotesRevWalk changeNotesRevWalk =
            ChangeNotesCommit.newRevWalk(repo)) {
      ChangeNotesParser parser =
          new ChangeNotesParser(
              notes.getChangeId(),
              tipId,
              changeNotesRevWalk,
              args.changeNoteJson,
              args.metrics,
              noteDbUtil);
      parser.parseNotes();

      return parser.getRevisionNoteMap();
    } catch (ConfigInvalidException e) {
      throw new StorageException(e);
    }
  }
}
