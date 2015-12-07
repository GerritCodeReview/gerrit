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

import static com.google.gerrit.server.PatchLineCommentsUtil.setCommentRevId;

import com.google.common.base.Optional;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchLineCommentsUtil;
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
public class PutDraftComment implements RestModifyView<DraftCommentResource, DraftInput> {

  private final Provider<ReviewDb> db;
  private final DeleteDraftComment delete;
  private final PatchLineCommentsUtil plcUtil;
  private final BatchUpdate.Factory updateFactory;
  private final Provider<CommentJson> commentJson;
  private final PatchListCache patchListCache;

  @Inject
  PutDraftComment(Provider<ReviewDb> db,
      DeleteDraftComment delete,
      PatchLineCommentsUtil plcUtil,
      BatchUpdate.Factory updateFactory,
      Provider<CommentJson> commentJson,
      PatchListCache patchListCache) {
    this.db = db;
    this.delete = delete;
    this.plcUtil = plcUtil;
    this.updateFactory = updateFactory;
    this.commentJson = commentJson;
    this.patchListCache = patchListCache;
  }

  @Override
  public Response<CommentInfo> apply(DraftCommentResource rsrc, DraftInput in) throws
      RestApiException, UpdateException, OrmException {
    if (in == null || in.message == null || in.message.trim().isEmpty()) {
      return delete.apply(rsrc, null);
    } else if (in.id != null && !rsrc.getId().equals(in.id)) {
      throw new BadRequestException("id must match URL");
    } else if (in.line != null && in.line < 0) {
      throw new BadRequestException("line must be >= 0");
    } else if (in.line != null && in.range != null && in.line != in.range.endLine) {
      throw new BadRequestException("range endLine must be on the same line as the comment");
    }

    try (BatchUpdate bu = updateFactory.create(
        db.get(), rsrc.getChange().getProject(), rsrc.getControl().getUser(),
        TimeUtil.nowTs())) {
      Op op = new Op(rsrc.getComment().getKey(), in);
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
      return Response.ok(
          commentJson.get().setFillAccounts(false).format(op.comment));
    }
  }

  private class Op extends BatchUpdate.Op {
    private final PatchLineComment.Key key;
    private final DraftInput in;

    private PatchLineComment comment;

    private Op(PatchLineComment.Key key, DraftInput in) {
      this.key = key;
      this.in = in;
    }

    @Override
    public void updateChange(ChangeContext ctx)
        throws ResourceNotFoundException, OrmException {
      Optional<PatchLineComment> maybeComment =
          plcUtil.get(ctx.getDb(), ctx.getChangeNotes(), key);
      if (!maybeComment.isPresent()) {
        // Disappeared out from under us. Can't easily fall back to insert,
        // because the input might be missing required fields. Just give up.
        throw new ResourceNotFoundException("comment not found: " + key);
      }
      comment = maybeComment.get();

      PatchSet.Id psId = comment.getKey().getParentKey().getParentKey();
      PatchSet ps = ctx.getDb().patchSets().get(psId);
      if (ps == null) {
        throw new ResourceNotFoundException("patch set not found: " + psId);
      }
      if (in.path != null
          && !in.path.equals(comment.getKey().getParentKey().getFileName())) {
        // Updating the path alters the primary key, which isn't possible.
        // Delete then recreate the comment instead of an update.

        plcUtil.deleteComments(
            ctx.getDb(), ctx.getChangeUpdate(), Collections.singleton(comment));
        comment = new PatchLineComment(
            new PatchLineComment.Key(
                new Patch.Key(psId, in.path),
                comment.getKey().get()),
            comment.getLine(),
            ctx.getUser().getAccountId(),
            comment.getParentUuid(), ctx.getWhen());
        setCommentRevId(comment, patchListCache, ctx.getChange(), ps);
        plcUtil.insertComments(ctx.getDb(), ctx.getChangeUpdate(),
            Collections.singleton(update(comment, in)));
      } else {
        if (comment.getRevId() == null) {
          setCommentRevId(
              comment, patchListCache, ctx.getChange(), ps);
        }
        plcUtil.updateComments(ctx.getDb(), ctx.getChangeUpdate(),
            Collections.singleton(update(comment, in)));
      }
    }
  }

  private static PatchLineComment update(PatchLineComment e, DraftInput in) {
    if (in.side != null) {
      e.setSide(in.side == Side.PARENT ? (short) 0 : (short) 1);
    }
    if (in.inReplyTo != null) {
      e.setParentUuid(Url.decode(in.inReplyTo));
    }
    e.setMessage(in.message.trim());
    if (in.range != null || in.line != null) {
      e.setRange(in.range);
      e.setLine(in.range != null ? in.range.endLine : in.line);
    }
    e.setWrittenOn(TimeUtil.nowTs());
    return e;
  }
}
