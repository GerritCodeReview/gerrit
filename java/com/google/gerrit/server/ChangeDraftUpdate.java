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

package com.google.gerrit.server;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.server.notedb.ChangeNotes;
import java.time.Instant;
import java.util.List;
import org.eclipse.jgit.lib.PersonIdent;

/** In interface for updating draft comments. */
public interface ChangeDraftUpdate {

  interface ChangeDraftUpdateFactory {
    ChangeDraftUpdate create(
        ChangeNotes notes,
        Account.Id accountId,
        Account.Id realAccountId,
        PersonIdent authorIdent,
        Instant when);

    ChangeDraftUpdate create(
        Change change,
        Account.Id accountId,
        Account.Id realAccountId,
        PersonIdent authorIdent,
        Instant when);
  }

  /** Creates a draft comment. */
  void putDraftComment(HumanComment c);

  /**
   * Marks a comment for deletion. Called when the comment is deleted because the user published it.
   *
   * <p>NOTE for implementers: The actual deletion of a published draft should only happen after the
   * published comment is successfully updated. For more context, see {@link
   * com.google.gerrit.server.notedb.NoteDbUpdateManager#execute(boolean)}.
   *
   * <p>TODO(nitzan) - add generalized support for the above sync issue. The implementation should
   * support deletion of published drafts from multiple ChangeDraftUpdateFactory instances.
   */
  void markDraftCommentAsPublished(HumanComment c);

  /**
   * Marks a comment for deletion. Called when the comment is deleted because the user removed it.
   */
  void addDraftCommentForDeletion(HumanComment c);

  /**
   * Marks all comments for deletion. Called when there are inconsistencies between the published
   * comments storage and the drafts one.
   */
  void addAllDraftCommentsForDeletion(List<Comment> comments);
}
