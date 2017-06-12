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

package com.google.gerrit.server.change;

import static com.google.gerrit.server.CommentsUtil.setCommentRevId;

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collections;

@Singleton
public class CreateDraftComment implements RestModifyView<RevisionResource, DraftInput> {
  private final Provider<ReviewDb> db;
  private final BatchUpdate.Factory updateFactory;
  private final Provider<CommentJson> commentJson;
  private final CommentsUtil commentsUtil;
  private final PatchSetUtil psUtil;
  private final PatchListCache patchListCache;

  @Inject
  CreateDraftComment(
      Provider<ReviewDb> db,
      BatchUpdate.Factory updateFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      PatchListCache patchListCache) {
    this.db = db;
    this.updateFactory = updateFactory;
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
  }

  @Override
  public Response<CommentInfo> apply(RevisionResource rsrc, DraftInput in)
      throws RestApiException, UpdateException, OrmException {
    if (Strings.isNullOrEmpty(in.path)) {
      throw new BadRequestException("path must be non-empty");
    } else if (in.message == null || in.message.trim().isEmpty()) {
      throw new BadRequestException("message must be non-empty");
    } else if (in.line != null && in.line < 0) {
      throw new BadRequestException("line must be >= 0");
    } else if (in.line != null && in.range != null && in.line != in.range.endLine) {
      throw new BadRequestException("range endLine must be on the same line as the comment");
    }

    try (BatchUpdate bu =
        updateFactory.create(db.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Op op = new Op(rsrc.getPatchSet().getId(), in);
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
      return Response.created(
          commentJson.get().setFillAccounts(false).newCommentFormatter().format(op.comment));
    }
  }

  private class Op extends BatchUpdate.Op {
    private final PatchSet.Id psId;
    private final DraftInput in;

    private Comment comment;

    private Op(PatchSet.Id psId, DraftInput in) {
      this.psId = psId;
      this.in = in;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws ResourceNotFoundException, OrmException {
      PatchSet ps = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
      if (ps == null) {
        throw new ResourceNotFoundException("patch set not found: " + psId);
      }
      comment = commentsUtil.newComment(ctx, in.path, ps.getId(), in.side(), in.message.trim());
      comment.parentUuid = Url.decode(in.inReplyTo);
      comment.setLineNbrAndRange(in.line, in.range);
      comment.tag = in.tag;
      setCommentRevId(comment, patchListCache, ctx.getChange(), ps);

      commentsUtil.putComments(
          ctx.getDb(), ctx.getUpdate(psId), Status.DRAFT, Collections.singleton(comment));
      ctx.bumpLastUpdatedOn(false);
      return true;
    }
  }
}
