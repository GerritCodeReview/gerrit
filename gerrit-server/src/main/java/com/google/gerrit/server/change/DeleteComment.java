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

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.DeleteCommentInput;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

public class DeleteComment implements RestModifyView<CommentResource, DeleteCommentInput> {
  private final Provider<CurrentUser> userProvider;
  private final Provider<ReviewDb> dbProvider;
  private final PermissionBackend permissionBackend;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final CommentsUtil commentsUtil;
  private final PatchSetUtil psUtil;
  private final BatchUpdate.Factory updateFactory;
  private final PatchListCache patchListCache;

  @Inject
  public DeleteComment(
      Provider<CurrentUser> userProvider,
      Provider<ReviewDb> dbProvider,
      PermissionBackend permissionBackend,
      BatchUpdate.Factory batchUpdateFactory,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      BatchUpdate.Factory updateFactory,
      PatchListCache patchListCache) {
    this.userProvider = userProvider;
    this.dbProvider = dbProvider;
    this.permissionBackend = permissionBackend;
    this.batchUpdateFactory = batchUpdateFactory;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.updateFactory = updateFactory;
    this.patchListCache = patchListCache;
  }

  @Override
  public Response<?> apply(CommentResource rsrc, DeleteCommentInput input)
      throws RestApiException, IOException, ConfigInvalidException, OrmException,
          PermissionBackendException, UpdateException {
    CurrentUser user = userProvider.get();
    permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);

    String newMessage = getCommentNewMessage(user.asIdentifiedUser().getName(), input.reason);
    DeleteCommentOp deleteCommentOp = new DeleteCommentOp(rsrc, newMessage);
    try (BatchUpdate batchUpdate =
        batchUpdateFactory.create(
            dbProvider.get(), rsrc.getRevisionResource().getProject(), user, TimeUtil.nowTs())) {
      batchUpdate.addOp(rsrc.getRevisionResource().getChange().getId(), deleteCommentOp).execute();
    }

    return Response.none();
  }

  private static String getCommentNewMessage(String name, String reason) {
    return "Comment removed by: "
        + name
        + (Strings.isNullOrEmpty(reason) ? "" : ("; Reason: " + reason));
  }

  /** Delete a comment by rewriting the commit history. */
  private class DeleteCommentOp implements BatchUpdateOp {
    private final CommentResource rsrc;
    private final String newMessage;

    DeleteCommentOp(CommentResource rsrc, String newMessage) {
      this.rsrc = rsrc;
      this.newMessage = newMessage;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws ResourceConflictException, OrmException, ResourceNotFoundException {
      Comment.Key commentKey = rsrc.getComment().key;
      Optional<Comment> maybeComment = commentsUtil.get(ctx.getDb(), ctx.getNotes(), commentKey);
      if (!maybeComment.isPresent()) {
        return false; // TODO - may throw out an exception instead.
      }
      PatchSet.Id psId = ctx.getChange().currentPatchSetId();
      commentsUtil.deleteCommentByRewritingHistory(
          ctx.getDb(), ctx.getUpdate(psId), commentKey, rsrc.getPatchSet().getId(), newMessage);
      ctx.rewriteHistory();
      return true;
    }
  }
}
