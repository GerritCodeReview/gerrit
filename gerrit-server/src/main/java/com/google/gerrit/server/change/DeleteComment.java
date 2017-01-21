// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.api.changes.DeleteCommentInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.DisabledChangesReviewDbWrapper;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeCommentUpdate;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.util.Collections;

public class DeleteComment implements
    RestModifyView<CommentResource, DeleteCommentInput> {
  private final ChangeIndexer indexer;
  private final ChangeCommentUpdate.Factory commentUpdateFactory;
  private final Provider<CurrentUser> currentUserProvider;
  private final NotesMigration notesMigration;
  private final Provider<ReviewDb> dbProvider;
  private NoteDbChangeState state;

  @Inject
  public DeleteComment(ChangeIndexer indexer,
      ChangeCommentUpdate.Factory commentUpdateFactory,
      Provider<CurrentUser> currentUserProvider,
      NotesMigration notesMigration,
      Provider<ReviewDb> dbProvider) {
    this.indexer = indexer;
    this.commentUpdateFactory = commentUpdateFactory;
    this.currentUserProvider = currentUserProvider;
    this.dbProvider = dbProvider;
    this.notesMigration = notesMigration;
  }

  @Override
  public Response<?> apply(CommentResource rsrc, DeleteCommentInput input)
      throws RestApiException, IOException, ConfigInvalidException,
      OrmException{
    if (!currentUserProvider.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allow to delete comment");
    }

    if (input == null) {
      input = new DeleteCommentInput();
      input.reason = "";
      input.removeAllData = false;
    }

    if (input.reason == null) {
      input.reason = "";
    }

    if (input.removeAllData == null) {
      input.removeAllData = false;
    }

    Comment comment = rsrc.getComment();
    String removedCommentMsg = "Comment removed by: "
        + currentUserProvider.get().getUserName()
        + "; Reason: " + input.reason;

    if (!(dbProvider.get() instanceof DisabledChangesReviewDbWrapper)) {
      // Delete from ReviewDb
      PatchLineComment.Key key = new PatchLineComment.Key(
          new Patch.Key(rsrc.getPatchSet().getId(), comment.key.filename),
          comment.key.uuid);
      PatchLineComment patchLineComment =
          ReviewDbUtil.unwrapDb(dbProvider.get())
              .patchComments().get(key);

      if (patchLineComment == null) {
        throw new ResourceNotFoundException("comment not found: " + key);
      }

      if (input.removeAllData) {
        ReviewDbUtil.unwrapDb(dbProvider.get()).patchComments()
            .deleteKeys(Collections.singleton(key));
      } else {
        patchLineComment.setMessage(removedCommentMsg);
        ReviewDbUtil.unwrapDb(dbProvider.get()).patchComments()
            .upsert(Collections.singleton(patchLineComment));
      }
    }

    Change change = rsrc.getRevisionResource().getChange();
    Project.NameKey projectKey = rsrc.getRevisionResource().getProject();
    if (notesMigration.readChanges()) {
      // Delete from NoteDb
      ChangeCommentUpdate commentUpdate = commentUpdateFactory.create(
          currentUserProvider.get(), projectKey, change);
      // Update the change note state in ReviewDb
      NoteDbChangeState state = commentUpdate.updateComment(comment,
          removedCommentMsg, input.removeAllData);
      change.setNoteDbState(state.toString());
      ReviewDbUtil.unwrapDb(dbProvider.get()).changes()
          .update(Collections.singleton(change));
    }

    // Update the change index
    indexer.indexAsync(projectKey, change.getId());

    return Response.none();
  }

}
