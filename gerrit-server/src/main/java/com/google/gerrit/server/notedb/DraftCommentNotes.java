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

import static com.google.gerrit.server.notedb.CommentsInNotesUtil.getCommentPsId;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
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
    private final NotesMigration migration;
    private final AllUsersName draftsProject;

    @VisibleForTesting
    @Inject
    public Factory(GitRepositoryManager repoManager,
        NotesMigration migration,
        AllUsersNameProvider allUsers) {
      this.repoManager = repoManager;
      this.migration = migration;
      this.draftsProject = allUsers.get();
    }

    public DraftCommentNotes create(Change.Id changeId, Account.Id accountId) {
      return new DraftCommentNotes(repoManager, migration, draftsProject,
          changeId, accountId);
    }
  }

  private final AllUsersName draftsProject;
  private final Account.Id author;

  private final Table<PatchSet.Id, String, PatchLineComment> draftBaseComments;
  private final Table<PatchSet.Id, String, PatchLineComment> draftPsComments;
  private NoteMap noteMap;

  DraftCommentNotes(GitRepositoryManager repoManager, NotesMigration migration,
      AllUsersName draftsProject, Change.Id changeId, Account.Id author) {
    super(repoManager, migration, changeId);
    this.draftsProject = draftsProject;
    this.author = author;

    this.draftBaseComments = HashBasedTable.create();
    this.draftPsComments = HashBasedTable.create();
  }

  public NoteMap getNoteMap() {
    return noteMap;
  }

  public Account.Id getAuthor() {
    return author;
  }

  /**
   * @return a defensive copy of the table containing all draft comments
   *    on this change with side == 0. The row key is the comment's PatchSet.Id,
   *    the column key is the comment's UUID, and the value is the comment.
   */
  public Table<PatchSet.Id, String, PatchLineComment>
      getDraftBaseComments() {
    return HashBasedTable.create(draftBaseComments);
  }

  /**
   * @return a defensive copy of the table containing all draft comments
   *    on this change with side == 1. The row key is the comment's PatchSet.Id,
   *    the column key is the comment's UUID, and the value is the comment.
   */
  public Table<PatchSet.Id, String, PatchLineComment>
      getDraftPsComments() {
    return HashBasedTable.create(draftPsComments);
  }

  public boolean containsComment(PatchLineComment c) {
    Table<PatchSet.Id, String, PatchLineComment> t =
        c.getSide() == (short) 0
        ? getDraftBaseComments()
        : getDraftPsComments();
    return t.contains(getCommentPsId(c), c.getKey().get());
  }

  @Override
  protected String getRefName() {
    return RefNames.refsDraftComments(author, getChangeId());
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    ObjectId rev = getRevision();
    if (rev == null) {
      return;
    }

    try (RevWalk walk = new RevWalk(reader);
        DraftCommentNotesParser parser = new DraftCommentNotesParser(
          getChangeId(), walk, rev, repoManager, draftsProject, author)) {
      parser.parseDraftComments();

      buildCommentTable(draftBaseComments, parser.draftBaseComments);
      buildCommentTable(draftPsComments, parser.draftPsComments);
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
    // Do nothing; tables are final and initialized in constructor.
  }

  @Override
  protected Project.NameKey getProjectName() {
    return draftsProject;
  }

  private void buildCommentTable(
      Table<PatchSet.Id, String, PatchLineComment> commentTable,
      Multimap<PatchSet.Id, PatchLineComment> allComments) {
    for (PatchLineComment c : allComments.values()) {
      commentTable.put(getCommentPsId(c), c.getKey().get(), c);
    }
  }

}
