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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.entities.RefNames.refsDraftComments;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Project;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;

/** View of the draft comments for a single {@link Change} based on the log of its drafts branch. */
class DraftCommentNotes extends AbstractChangeNotes<DraftCommentNotes> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  interface Factory {
    DraftCommentNotes create(Change.Id changeId, Account.Id accountId);
  }

  private final Account.Id author;
  private final Ref ref;

  private ImmutableListMultimap<ObjectId, HumanComment> comments;
  private RevisionNoteMap<ChangeRevisionNote> revisionNoteMap;

  @AssistedInject
  DraftCommentNotes(Args args, @Assisted Change.Id changeId, @Assisted Account.Id author) {
    this(args, changeId, author, null);
  }

  DraftCommentNotes(Args args, Change.Id changeId, Account.Id author, @Nullable Ref ref) {
    super(args, changeId, null);
    this.author = requireNonNull(author);
    this.ref = ref;
    if (ref != null) {
      checkArgument(
          ref.getName().equals(getRefName()),
          "draft ref not for change %s and account %s: %s",
          getChangeId(),
          author,
          ref.getName());
    }
  }

  RevisionNoteMap<ChangeRevisionNote> getRevisionNoteMap() {
    return revisionNoteMap;
  }

  public Account.Id getAuthor() {
    return author;
  }

  public ImmutableList<HumanComment> getComments() {
    return comments.values().asList();
  }

  public boolean containsComment(HumanComment c) {
    for (HumanComment existing : comments.values()) {
      if (c.key.equals(existing.key)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected String getRefName() {
    return refsDraftComments(getChangeId(), author);
  }

  @Override
  protected ObjectId readRef(Repository repo) throws IOException {
    if (ref != null) {
      return ref.getObjectId();
    }
    return super.readRef(repo);
  }

  @Override
  protected void onLoad(LoadHandle handle) throws IOException, ConfigInvalidException {
    ObjectId rev = handle.id();
    if (rev == null) {
      loadDefaults();
      return;
    }

    logger.atFine().log(
        "Load draft comment notes for change %s of project %s", getChangeId(), getProjectName());
    RevCommit tipCommit = handle.walk().parseCommit(rev);
    ObjectReader reader = handle.walk().getObjectReader();
    revisionNoteMap =
        RevisionNoteMap.parse(
            args.changeNoteJson,
            reader,
            NoteMap.read(reader, tipCommit),
            HumanComment.Status.DRAFT);
    ImmutableListMultimap.Builder<ObjectId, HumanComment> cs = ImmutableListMultimap.builder();
    for (ChangeRevisionNote rn : revisionNoteMap.revisionNotes.values()) {
      for (HumanComment c : rn.getEntities()) {
        cs.put(c.getCommitId(), c);
      }
    }
    comments = cs.build();
  }

  @Override
  protected void loadDefaults() {
    comments = ImmutableListMultimap.of();
  }

  @Override
  public Project.NameKey getProjectName() {
    return args.allUsers;
  }

  @Nullable
  @VisibleForTesting
  NoteMap getNoteMap() {
    return revisionNoteMap != null ? revisionNoteMap.noteMap : null;
  }
}
