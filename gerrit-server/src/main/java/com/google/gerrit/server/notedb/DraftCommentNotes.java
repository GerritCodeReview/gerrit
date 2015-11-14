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
import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.notes.NoteMap;
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
    private final NoteDbMetrics metrics;
    private final NotesMigration migration;
    private final AllUsersName draftsProject;

    @VisibleForTesting
    @Inject
    public Factory(GitRepositoryManager repoManager,
        NoteDbMetrics metrics,
        NotesMigration migration,
        AllUsersNameProvider allUsers) {
      this.repoManager = repoManager;
      this.metrics = metrics;
      this.migration = migration;
      this.draftsProject = allUsers.get();
    }

    public DraftCommentNotes create(Change.Id changeId, Account.Id accountId) {
      return new DraftCommentNotes(repoManager, metrics, migration,
          draftsProject, changeId, accountId);
    }
  }

  private final AllUsersName draftsProject;
  private final Account.Id author;

  private ImmutableListMultimap<RevId, PatchLineComment> comments;
  private NoteMap noteMap;

  DraftCommentNotes(GitRepositoryManager repoManager, NoteDbMetrics metrics,
      NotesMigration migration, AllUsersName draftsProject, Change.Id changeId,
      Account.Id author) {
    super(repoManager, metrics, migration, changeId);
    this.draftsProject = draftsProject;
    this.author = author;
  }

  public NoteMap getNoteMap() {
    return noteMap;
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

    try (RevWalk walk = new RevWalk(reader);
        DraftCommentNotesParser parser = new DraftCommentNotesParser(
          getChangeId(), walk, rev, repoManager, draftsProject, author)) {
      parser.parseDraftComments();

      comments = ImmutableListMultimap.copyOf(parser.comments);
      noteMap = parser.noteMap;
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
  protected Project.NameKey getProjectName() {
    return draftsProject;
  }
}
