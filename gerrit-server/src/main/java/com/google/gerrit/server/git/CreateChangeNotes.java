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

package com.google.gerrit.server.git;

import static com.google.gerrit.server.git.GitRepositoryManager.REFS_NOTES_CHANGES;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

public class CreateChangeNotes {
  static final Logger log = LoggerFactory.getLogger(CreateChangeNotes.class);

  public interface Factory {
    CreateChangeNotes create(Repository db);
  }

  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private final Repository repository;

  private RevWalk revWalk;
  private ObjectInserter inserter;

  private final NotesBranchUtil.Factory notesBranchUtilFactory;

  @Inject
  CreateChangeNotes(
      @GerritPersonIdent final PersonIdent gerritIdent,
      final @Nullable @CanonicalWebUrl String canonicalWebUrl,
      final @AnonymousCowardName String anonymousCowardName,
      final NotesBranchUtil.Factory notesBranchUtilFactory,
      final @Assisted  Repository repository) {
    this.notesBranchUtilFactory = notesBranchUtilFactory;
    this.repository = repository;
  }

  public String create(ObjectId commitId, Change change, PersonIdent author)
      throws ChangeNoteCreationException {
    String notesRef;
    try {
     revWalk = new RevWalk(repository);
     inserter = repository.newObjectInserter();

      NoteMap notes = NoteMap.newEmptyMap();

      String splitPatchset[] = change.currentPatchSetId().toString().split(",");
      StringBuilder message = new StringBuilder("[source]");
      message.append("\n  change=").append(change.getChangeId());
      message.append("\n  patch-set=").append(splitPatchset[1]);

      notes.set(commitId, createNoteContent(change, commitId));

      NotesBranchUtil notesBranchUtil = notesBranchUtilFactory
          .create(change.getProject(), repository, inserter);

      notesRef = REFS_NOTES_CHANGES + "/" + change.getChangeId() % 10 +
          "/" + change.getChangeId() + "/" + commitId.name();

      notesBranchUtil.commitAllNotes(notes, notesRef, author,
          message.toString());
      inserter.flush();

    } catch (IOException e) {
      throw new ChangeNoteCreationException(e);
    } catch (ConcurrentRefUpdateException e) {
      throw new ChangeNoteCreationException(e);
    } finally {
      revWalk.release();
      inserter.release();
    }
    return notesRef;
  }

  private ObjectId createNoteContent(Change change, ObjectId commit)
      throws ChangeNoteCreationException, IOException  {
    if (!(commit instanceof RevCommit)) {
      commit = revWalk.parseCommit(commit);
    }
    return createNoteContent(change, (RevCommit) commit);
  }

  private ObjectId createNoteContent(Change change, RevCommit commit)
      throws ChangeNoteCreationException, IOException {
    StringBuilder sb = new StringBuilder();

    final List<String> idList = commit.getFooterLines(CHANGE_ID);
    if (idList.isEmpty()) {
      sb.append("Change-Id: ").append(change.getKey().get()).append("\n");
    }

    sb.append("Project: ").append(change.getProject().get()).append("\n");
    sb.append("Branch: ").append(change.getDest().get()).append("\n");

    return inserter.insert(Constants.OBJ_BLOB, sb.toString().getBytes("UTF-8"));
  }
}
