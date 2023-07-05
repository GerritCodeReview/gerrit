// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.server.notedb.ChangeNotes;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DraftCommentsReader {
  /**
   * Returns a single draft of the provided change, that was written by {@code author} and has the
   * given {@code key}, or {@code Optional::empty} if there is no such comment.
   */
  Optional<HumanComment> getDraftComment(ChangeNotes notes, IdentifiedUser author, Comment.Key key);

  /**
   * Returns all drafts of the provided change, that were written by {@code author}. The comments
   * are sorted by {@link CommentsUtil#COMMENT_ORDER}.
   */
  List<HumanComment> getDraftsByChangeAndDraftAuthor(ChangeNotes notes, Account.Id author);

  /**
   * Returns all drafts of the provided change, that were written by {@code author}. The comments
   * are sorted by {@link CommentsUtil#COMMENT_ORDER}.
   *
   * <p>If you already have a ChangeNotes instance, consider using {@link
   * #getDraftsByChangeAndDraftAuthor(ChangeNotes, Account.Id)} instead.
   */
  List<HumanComment> getDraftsByChangeAndDraftAuthor(Change.Id changeId, Account.Id author);

  /**
   * Returns all drafts of the provided patch set, that were written by {@code author}. The comments
   * are sorted by {@link CommentsUtil#COMMENT_ORDER}.
   */
  List<HumanComment> getDraftsByPatchSetAndDraftAuthor(
      ChangeNotes notes, PatchSet.Id psId, Account.Id author);

  /**
   * Returns all drafts of the provided change, regardless of the author. The comments are sorted by
   * {@link CommentsUtil#COMMENT_ORDER}.
   */
  List<HumanComment> getDraftsByChangeForAllAuthors(ChangeNotes notes);

  /** Returns all users that have any draft comments on the provided change. */
  Set<Account.Id> getUsersWithDrafts(ChangeNotes changeNotes);

  /** Returns all changes that contain draft comments of {@code author}. */
  Set<Change.Id> getChangesWithDrafts(Account.Id author);
}
