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
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.DeleteDraftComment.Input;
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
public class DeleteDraftComment
    implements RestModifyView<DraftCommentResource, Input> {
  static class Input {
  }

  private final Provider<ReviewDb> db;
  private final PatchLineCommentsUtil plcUtil;
  private final PatchSetUtil psUtil;
  private final BatchUpdate.Factory updateFactory;
  private final PatchListCache patchListCache;

  @Inject
  DeleteDraftComment(Provider<ReviewDb> db,
      PatchLineCommentsUtil plcUtil,
      PatchSetUtil psUtil,
      BatchUpdate.Factory updateFactory,
      PatchListCache patchListCache) {
    this.db = db;
    this.plcUtil = plcUtil;
    this.psUtil = psUtil;
    this.updateFactory = updateFactory;
    this.patchListCache = patchListCache;
  }

  @Override
  public Response<CommentInfo> apply(DraftCommentResource rsrc, Input input)
      throws RestApiException, UpdateException {
    try (BatchUpdate bu = updateFactory.create(
        db.get(), rsrc.getChange().getProject(), rsrc.getControl().getUser(),
        TimeUtil.nowTs())) {
      Op op = new Op(rsrc.getComment().getKey());
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
    }
    return Response.none();
  }

  private class Op extends BatchUpdate.Op {
    private final PatchLineComment.Key key;

    private Op(PatchLineComment.Key key) {
      this.key = key;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws ResourceNotFoundException, OrmException {
      Optional<PatchLineComment> maybeComment =
          plcUtil.get(ctx.getDb(), ctx.getNotes(), key);
      if (!maybeComment.isPresent()) {
        return false; // Nothing to do.
      }
      PatchSet.Id psId = key.getParentKey().getParentKey();
      PatchSet ps = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
      if (ps == null) {
        throw new ResourceNotFoundException("patch set not found: " + psId);
      }
      PatchLineComment c = maybeComment.get();
      setCommentRevId(c, patchListCache, ctx.getChange(), ps);
      plcUtil.deleteComments(
          ctx.getDb(), ctx.getUpdate(psId), Collections.singleton(c));
      ctx.bumpLastUpdatedOn(false);
      return true;
    }
  }
}
