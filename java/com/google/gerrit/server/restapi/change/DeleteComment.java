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

package com.google.gerrit.server.restapi.change;

import com.google.common.base.Strings;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.DeleteCommentInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.change.CommentResource;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class DeleteComment
    extends RetryingRestModifyView<CommentResource, DeleteCommentInput, CommentInfo> {

  private final Provider<CurrentUser> userProvider;
  private final PermissionBackend permissionBackend;
  private final CommentsUtil commentsUtil;
  private final Provider<CommentJson> commentJson;
  private final ChangeNotes.Factory notesFactory;

  @Inject
  public DeleteComment(
      Provider<CurrentUser> userProvider,
      PermissionBackend permissionBackend,
      RetryHelper retryHelper,
      CommentsUtil commentsUtil,
      Provider<CommentJson> commentJson,
      ChangeNotes.Factory notesFactory) {
    super(retryHelper);
    this.userProvider = userProvider;
    this.permissionBackend = permissionBackend;
    this.commentsUtil = commentsUtil;
    this.commentJson = commentJson;
    this.notesFactory = notesFactory;
  }

  @Override
  public CommentInfo applyImpl(
      BatchUpdate.Factory batchUpdateFactory, CommentResource rsrc, DeleteCommentInput input)
      throws RestApiException, IOException, ConfigInvalidException, StorageException,
          PermissionBackendException, UpdateException {
    CurrentUser user = userProvider.get();
    permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);

    if (input == null) {
      input = new DeleteCommentInput();
    }

    String newMessage = getCommentNewMessage(user.asIdentifiedUser().getName(), input.reason);
    DeleteCommentOp deleteCommentOp = new DeleteCommentOp(rsrc, newMessage);
    try (BatchUpdate batchUpdate =
        batchUpdateFactory.create(
            rsrc.getRevisionResource().getProject(), user, TimeUtil.nowTs())) {
      batchUpdate.addOp(rsrc.getRevisionResource().getChange().getId(), deleteCommentOp).execute();
    }

    ChangeNotes updatedNotes =
        notesFactory.createChecked(rsrc.getRevisionResource().getChange().getId());
    List<Comment> changeComments = commentsUtil.publishedByChange(updatedNotes);
    Optional<Comment> updatedComment =
        changeComments.stream().filter(c -> c.key.equals(rsrc.getComment().key)).findFirst();
    if (!updatedComment.isPresent()) {
      // This should not happen as this endpoint should not remove the whole comment.
      throw new ResourceNotFoundException("comment not found: " + rsrc.getComment().key);
    }

    return commentJson.get().newCommentFormatter().format(updatedComment.get());
  }

  private static String getCommentNewMessage(String name, String reason) {
    StringBuilder stringBuilder = new StringBuilder("Comment removed by: ").append(name);
    if (!Strings.isNullOrEmpty(reason)) {
      stringBuilder.append("; Reason: ").append(reason);
    }
    return stringBuilder.toString();
  }

  private class DeleteCommentOp implements BatchUpdateOp {
    private final CommentResource rsrc;
    private final String newMessage;

    DeleteCommentOp(CommentResource rsrc, String newMessage) {
      this.rsrc = rsrc;
      this.newMessage = newMessage;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws ResourceConflictException, ResourceNotFoundException {
      PatchSet.Id psId = ctx.getChange().currentPatchSetId();
      commentsUtil.deleteCommentByRewritingHistory(
          ctx.getUpdate(psId), rsrc.getComment().key, newMessage);
      return true;
    }
  }
}
