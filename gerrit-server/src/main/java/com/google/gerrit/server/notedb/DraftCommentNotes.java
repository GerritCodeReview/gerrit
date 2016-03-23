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
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/**
 * View of the draft comments for a single {@link Change} based on the log of
 * its drafts branch.
 */
public class DraftCommentNotes extends AbstractChangeNotes<DraftCommentNotes> {
  public interface Factory {
    DraftCommentNotes create(Change.Id changeId, Account.Id accountId);
  }

  private final Account.Id author;

  private ImmutableListMultimap<RevId, PatchLineComment> comments;
  private RevisionNoteMap revisionNoteMap;

  @AssistedInject
  DraftCommentNotes(
      Args args,
      @Assisted Change.Id changeId,
      @Assisted Account.Id author) {
    super(args, changeId);
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
  protected void onLoad(RevWalk walk)
      throws IOException, ConfigInvalidException {
    ObjectId rev = getRevision();
    if (rev == null) {
      loadDefaults();
      return;
    }

    RevCommit tipCommit = walk.parseCommit(rev);
    ObjectReader reader = walk.getObjectReader();
    revisionNoteMap = RevisionNoteMap.parse(
        args.noteUtil, getChangeId(), reader, NoteMap.read(reader, tipCommit),
        true);
    Multimap<RevId, PatchLineComment> cs = ArrayListMultimap.create();
    for (RevisionNote rn : revisionNoteMap.revisionNotes.values()) {
      for (PatchLineComment c : rn.comments) {
        cs.put(c.getRevId(), c);
      }
    }
    comments = ImmutableListMultimap.copyOf(cs);
  }

  @Override
  protected void loadDefaults() {
    comments = ImmutableListMultimap.of();
  }

  @Override
  public Project.NameKey getProjectName() {
    return args.allUsers;
  }

  @VisibleForTesting
  NoteMap getNoteMap() {
    return revisionNoteMap != null ? revisionNoteMap.noteMap : null;
  }
}
