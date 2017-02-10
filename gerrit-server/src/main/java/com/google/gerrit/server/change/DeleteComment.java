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
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.notedb.ChangeCommentUpdate;
import com.google.gerrit.server.notedb.NoteDbChangeState;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Collections;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class DeleteComment implements RestModifyView<CommentResource, DeleteCommentInput> {
  private final ChangeCommentUpdate.Factory commentUpdateFactory;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeIndexer indexer;
  private final GitRepositoryManager repoManager;
  private final NotesMigration notesMigration;
  private final Provider<CurrentUser> currentUserProvider;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  public DeleteComment(
      ChangeCommentUpdate.Factory commentUpdateFactory,
      ChangeData.Factory changeDataFactory,
      ChangeIndexer indexer,
      GitRepositoryManager repoManager,
      NotesMigration notesMigration,
      Provider<CurrentUser> currentUserProvider,
      Provider<ReviewDb> dbProvider) {
    this.commentUpdateFactory = commentUpdateFactory;
    this.changeDataFactory = changeDataFactory;
    this.indexer = indexer;
    this.repoManager = repoManager;
    this.notesMigration = notesMigration;
    this.currentUserProvider = currentUserProvider;
    this.dbProvider = dbProvider;
  }

  @Override
  public Response<?> apply(CommentResource rsrc, DeleteCommentInput input)
      throws RestApiException, IOException, ConfigInvalidException, OrmException {
    if (!currentUserProvider.get().getCapabilities().canAdministrateServer()) {
      throw new AuthException("not allowed to delete comment");
    }

    if (input == null) {
      input = new DeleteCommentInput();
      input.reason = "";
    }

    if (input.reason == null) {
      input.reason = "";
    }

    String newMsg =
        "Comment removed by: "
            + currentUserProvider.get().getUserName()
            + (input.reason.isEmpty() ? "" : ("; Reason: " + input.reason))
            + ".";
    ReviewDb db = ReviewDbUtil.unwrapDb(dbProvider.get());
    Change change = rsrc.getRevisionResource().getChange();

    if (PrimaryStorage.of(change).equals(PrimaryStorage.REVIEW_DB)) {
      deleteFromReviewDb(db, rsrc, newMsg);
    }

    if (notesMigration.writeChanges()) {
      deleteFromNoteDb(db, rsrc, change, newMsg);
    }

    // Update the change index
    indexer.index(db, change);
    return Response.none();
  }

  private void deleteFromReviewDb(ReviewDb db, CommentResource rsrc, String newMsg)
      throws OrmException, ResourceNotFoundException {
    PatchLineComment.Key key =
        new PatchLineComment.Key(
            new Patch.Key(rsrc.getPatchSet().getId(), rsrc.getComment().key.filename),
            rsrc.getComment().key.uuid);
    PatchLineComment patchLineComment = db.patchComments().get(key);

    if (patchLineComment == null) {
      throw new ResourceNotFoundException("comment not found: " + key);
    }

    patchLineComment.setMessage(newMsg);
    db.patchComments().upsert(Collections.singleton(patchLineComment));
  }

  private void deleteFromNoteDb(ReviewDb db, CommentResource rsrc, Change change, String newMsg)
      throws ResourceNotFoundException, ConfigInvalidException, IOException, OrmException {
    ChangeData cd = changeDataFactory.create(db, change);
    if (!cd.notes().containsCommentPublished(rsrc.getComment())) {
      throw new ResourceNotFoundException("comment not found: " + rsrc.getComment().key);
    }

    ChangeCommentUpdate commentUpdate =
        commentUpdateFactory.create(currentUserProvider.get(), change);

    try (Repository repo = repoManager.openRepository(rsrc.getRevisionResource().getProject())) {
      String metaRefStr = RefNames.changeMetaRef(change.getId());
      Ref metaRef = repo.exactRef(metaRefStr);
      if (metaRef == null) {
        throw new ResourceNotFoundException("branch not found: " + metaRefStr);
      }
      NoteDbChangeState state =
          commentUpdate.updateComment(repo, metaRef, rsrc.getComment(), newMsg);

      if (!PrimaryStorage.of(change).equals(PrimaryStorage.NOTE_DB) && (state != null)) {
        change.setNoteDbState(state.toString());
        db.changes().update(Collections.singleton(change));
      }
    }
  }
}
