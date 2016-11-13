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
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Optional;

@Singleton
public class PutDraftComment implements RestModifyView<DraftCommentResource, DraftInput> {

  private final Provider<ReviewDb> db;
  private final DeleteDraftComment delete;
  private final CommentsUtil commentsUtil;
  private final PatchSetUtil psUtil;
  private final BatchUpdate.Factory updateFactory;
  private final Provider<CommentJson> commentJson;
  private final PatchListCache patchListCache;

  @Inject
  PutDraftComment(
      Provider<ReviewDb> db,
      DeleteDraftComment delete,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      BatchUpdate.Factory updateFactory,
      Provider<CommentJson> commentJson,
      PatchListCache patchListCache) {
    this.db = db;
    this.delete = delete;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.updateFactory = updateFactory;
    this.commentJson = commentJson;
    this.patchListCache = patchListCache;
  }

  @Override
  public Response<CommentInfo> apply(DraftCommentResource rsrc, DraftInput in)
      throws RestApiException, UpdateException, OrmException {
    if (in == null || in.message == null || in.message.trim().isEmpty()) {
      return delete.apply(rsrc, null);
    } else if (in.id != null && !rsrc.getId().equals(in.id)) {
      throw new BadRequestException("id must match URL");
    } else if (in.line != null && in.line < 0) {
      throw new BadRequestException("line must be >= 0");
    } else if (in.line != null && in.range != null && in.line != in.range.endLine) {
      throw new BadRequestException("range endLine must be on the same line as the comment");
    }

    try (BatchUpdate bu =
        updateFactory.create(
            db.get(),
            rsrc.getChange().getProject(),
            rsrc.getControl().getUser(),
            TimeUtil.nowTs())) {
      Op op = new Op(rsrc.getComment().key, in);
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
      return Response.ok(
          commentJson.get().setFillAccounts(false).newCommentFormatter().format(op.comment));
    }
  }

  private class Op extends BatchUpdate.Op {
    private final Comment.Key key;
    private final DraftInput in;

    private Comment comment;

    private Op(Comment.Key key, DraftInput in) {
      this.key = key;
      this.in = in;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws ResourceNotFoundException, OrmException {
      Optional<Comment> maybeComment = commentsUtil.get(ctx.getDb(), ctx.getNotes(), key);
      if (!maybeComment.isPresent()) {
        // Disappeared out from under us. Can't easily fall back to insert,
        // because the input might be missing required fields. Just give up.
        throw new ResourceNotFoundException("comment not found: " + key);
      }
      Comment origComment = maybeComment.get();
      comment = new Comment(origComment);
      // Copy constructor preserved old real author; replace with current real
      // user.
      ctx.getUser().updateRealAccountId(comment::setRealAuthor);

      PatchSet.Id psId = new PatchSet.Id(ctx.getChange().getId(), origComment.key.patchSetId);
      ChangeUpdate update = ctx.getUpdate(psId);

      PatchSet ps = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
      if (ps == null) {
        throw new ResourceNotFoundException("patch set not found: " + psId);
      }
      if (in.path != null && !in.path.equals(origComment.key.filename)) {
        // Updating the path alters the primary key, which isn't possible.
        // Delete then recreate the comment instead of an update.

        commentsUtil.deleteComments(ctx.getDb(), update, Collections.singleton(origComment));
        comment.key.filename = in.path;
      }
      setCommentRevId(comment, patchListCache, ctx.getChange(), ps);
      commentsUtil.putComments(
          ctx.getDb(),
          update,
          Status.DRAFT,
          Collections.singleton(update(comment, in, ctx.getWhen())));
      ctx.bumpLastUpdatedOn(false);
      return true;
    }
  }

  private static Comment update(Comment e, DraftInput in, Timestamp when) {
    if (in.side != null) {
      e.side = in.side();
    }
    if (in.inReplyTo != null) {
      e.parentUuid = Url.decode(in.inReplyTo);
    }
    e.setLineNbrAndRange(in.line, in.range);
    e.message = in.message.trim();
    e.writtenOn = when;
    if (in.tag != null) {
      // TODO(dborowitz): Can we support changing tags via PUT?
      e.tag = in.tag;
    }
    return e;
  }
}
