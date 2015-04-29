// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

class DraftCommentNotesParser implements AutoCloseable {
  final Multimap<RevId, PatchLineComment> comments;
  NoteMap noteMap;

  private final Change.Id changeId;
  private final ObjectId tip;
  private final RevWalk walk;
  private final Repository repo;
  private final Account.Id author;

  DraftCommentNotesParser(Change.Id changeId, RevWalk walk, ObjectId tip,
      GitRepositoryManager repoManager, AllUsersName draftsProject,
      Account.Id author) throws RepositoryNotFoundException, IOException {
    this.changeId = changeId;
    this.walk = walk;
    this.tip = tip;
    this.repo = repoManager.openMetadataRepository(draftsProject);
    this.author = author;

    comments = ArrayListMultimap.create();
  }

  @Override
  public void close() {
    repo.close();
  }

  void parseDraftComments() throws IOException, ConfigInvalidException {
    walk.markStart(walk.parseCommit(tip));
    noteMap = CommentsInNotesUtil.parseCommentsFromNotes(repo,
        RefNames.refsDraftComments(author, changeId),
        walk, changeId, comments, PatchLineComment.Status.DRAFT);
  }
}
