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

import static com.google.gerrit.entities.Patch.PATCHSET_LEVEL;
import static com.google.gerrit.server.CommentsUtil.setCommentCommitId;

import com.google.common.base.Strings;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.CommentContextException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collections;

@Singleton
public class CreateDraftComment implements RestModifyView<RevisionResource, DraftInput> {
  private final BatchUpdate.Factory updateFactory;
  private final Provider<CommentJson> commentJson;
  private final CommentsUtil commentsUtil;
  private final PatchSetUtil psUtil;
  private final PatchListCache patchListCache;

  @Inject
  CreateDraftComment(
      BatchUpdate.Factory updateFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil,
      PatchSetUtil psUtil,
      PatchListCache patchListCache) {
    this.updateFactory = updateFactory;
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
    this.psUtil = psUtil;
    this.patchListCache = patchListCache;
  }

  @Override
  public Response<CommentInfo> apply(RevisionResource rsrc, DraftInput in)
      throws RestApiException, UpdateException, PermissionBackendException,
          CommentContextException {
    if (Strings.isNullOrEmpty(in.path)) {
      throw new BadRequestException("path must be non-empty");
    } else if (in.message == null || in.message.trim().isEmpty()) {
      throw new BadRequestException("message must be non-empty");
    } else if (in.path.equals(PATCHSET_LEVEL)
        && (in.side != null || in.range != null || in.line != null)) {
      throw new BadRequestException("patchset-level comments can't have side, range, or line");
    } else if (in.line != null && in.line < 0) {
      throw new BadRequestException("line must be >= 0");
    } else if (in.line != null && in.range != null && in.line != in.range.endLine) {
      throw new BadRequestException("range endLine must be on the same line as the comment");
    }

    try (BatchUpdate bu =
        updateFactory.create(rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Op op = new Op(rsrc.getPatchSet().id(), in);
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
      return Response.created(
          commentJson.get().setFillAccounts(false).newHumanCommentFormatter().format(op.comment));
    }
  }

  private class Op implements BatchUpdateOp {
    private final PatchSet.Id psId;
    private final DraftInput in;

    private HumanComment comment;

    private Op(PatchSet.Id psId, DraftInput in) {
      this.psId = psId;
      this.in = in;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws ResourceNotFoundException, UnprocessableEntityException,
            PatchListNotAvailableException {
      PatchSet ps = psUtil.get(ctx.getNotes(), psId);
      if (ps == null) {
        throw new ResourceNotFoundException("patch set not found: " + psId);
      }
      String parentUuid = Url.decode(in.inReplyTo);

      comment =
          commentsUtil.newHumanComment(
              ctx, in.path, ps.id(), in.side(), in.message.trim(), in.unresolved, parentUuid);
      comment.setLineNbrAndRange(in.line, in.range);
      comment.tag = in.tag;

      setCommentCommitId(comment, patchListCache, ctx.getChange(), ps);

      commentsUtil.putHumanComments(
          ctx.getUpdate(psId), HumanComment.Status.DRAFT, Collections.singleton(comment));
      return true;
    }
  }
}
