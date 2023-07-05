// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;

import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.DraftCommentsReader;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.DraftCommentResource;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Optional;

@Singleton
public class DeleteDraftComment implements RestModifyView<DraftCommentResource, Input> {
  private final BatchUpdate.Factory updateFactory;
  private final CommentsUtil commentsUtil;
  private final DraftCommentsReader draftCommentsReader;

  private final PatchSetUtil psUtil;

  @Inject
  DeleteDraftComment(
      BatchUpdate.Factory updateFactory,
      CommentsUtil commentsUtil,
      DraftCommentsReader draftCommentsReader,
      PatchSetUtil psUtil) {
    this.updateFactory = updateFactory;
    this.commentsUtil = commentsUtil;
    this.draftCommentsReader = draftCommentsReader;
    this.psUtil = psUtil;
  }

  @Override
  public Response<CommentInfo> apply(DraftCommentResource rsrc, Input input)
      throws RestApiException, UpdateException {
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      try (BatchUpdate bu =
          updateFactory.create(rsrc.getChange().getProject(), rsrc.getUser(), TimeUtil.now())) {
        Op op = new Op(rsrc.getComment().key);
        bu.addOp(rsrc.getChange().getId(), op);
        bu.execute();
      }
    }
    return Response.none();
  }

  private class Op implements BatchUpdateOp {
    private final Comment.Key key;

    private Op(Comment.Key key) {
      this.key = key;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws ResourceNotFoundException {
      Optional<HumanComment> maybeComment =
          draftCommentsReader.getDraftComment(ctx.getNotes(), ctx.getIdentifiedUser(), key);
      if (!maybeComment.isPresent()) {
        return false; // Nothing to do.
      }
      PatchSet.Id psId = PatchSet.id(ctx.getChange().getId(), key.patchSetId);
      PatchSet ps = psUtil.get(ctx.getNotes(), psId);
      if (ps == null) {
        throw new ResourceNotFoundException("patch set not found: " + psId);
      }
      HumanComment c = maybeComment.get();
      commentsUtil.setCommentCommitId(c, ctx.getChange(), ps);
      commentsUtil.deleteHumanComments(ctx.getUpdate(psId), Collections.singleton(c));
      return true;
    }
  }
}
