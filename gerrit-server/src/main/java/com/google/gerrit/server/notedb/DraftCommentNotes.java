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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/**
 * View of the draft comments for a single {@link Change} based on the log of
 * its drafts branch.
 */
public class DraftCommentNotes extends AbstractChangeNotes<DraftCommentNotes> {
  @Singleton
  public static class Factory {
    private final GitRepositoryManager repoManager;
    private final NotesMigration migration;
    private final AllUsersName draftsProject;

    @VisibleForTesting
    @Inject
    public Factory(GitRepositoryManager repoManager,
        NotesMigration migration,
        AllUsersName allUsers) {
      this.repoManager = repoManager;
      this.migration = migration;
      this.draftsProject = allUsers;
    }

    public DraftCommentNotes create(Change.Id changeId, Account.Id accountId) {
      return new DraftCommentNotes(repoManager, migration, draftsProject,
          changeId, accountId);
    }
  }

  private final AllUsersName draftsProject;
  private final Account.Id author;

  private ImmutableListMultimap<RevId, PatchLineComment> comments;
  private RevisionNoteMap revisionNoteMap;

  DraftCommentNotes(GitRepositoryManager repoManager, NotesMigration migration,
      AllUsersName draftsProject, Change.Id changeId, Account.Id author) {
    super(repoManager, migration, changeId);
    this.draftsProject = draftsProject;
    this.author = author;
  }

  RevisionNoteMap getRevisionNoteMap() {
    return revisionNoteMap;
  }

  public Account.Id getAuthor() {
    return author;
  }

  public ImmutableListMultimap<RevId, PatchLineComment> getComments() {
    // TODO(dborowitz): Defensive copy?
    return comments;
  }

  public boolean containsComment(PatchLineComment c) {
    for (PatchLineComment existing : comments.values()) {
      if (c.getKey().equals(existing.getKey())) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected String getRefName() {
    return RefNames.refsDraftComments(author, getChangeId());
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    ObjectId rev = getRevision();
    if (rev == null) {
      loadDefaults();
      return;
    }

    try (RevWalk walk = new RevWalk(reader)) {
      RevCommit tipCommit = walk.parseCommit(rev);
      revisionNoteMap = RevisionNoteMap.parse(
          getChangeId(), reader, NoteMap.read(reader, tipCommit), true);
      Multimap<RevId, PatchLineComment> cs = ArrayListMultimap.create();
      for (RevisionNote rn : revisionNoteMap.revisionNotes.values()) {
        for (PatchLineComment c : rn.comments) {
          cs.put(c.getRevId(), c);
        }
      }
      comments = ImmutableListMultimap.copyOf(cs);
    }
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException,
      ConfigInvalidException {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " is read-only");
  }

  @Override
  protected void loadDefaults() {
    comments = ImmutableListMultimap.of();
  }

  @Override
  public Project.NameKey getProjectName() {
    return draftsProject;
  }

  @VisibleForTesting
  NoteMap getNoteMap() {
    return revisionNoteMap != null ? revisionNoteMap.noteMap : null;
  }
}
