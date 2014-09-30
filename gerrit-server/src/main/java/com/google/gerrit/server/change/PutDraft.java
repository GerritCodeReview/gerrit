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

import com.google.gerrit.common.changes.Side;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.DefaultInput;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.change.PutDraft.Input;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;

@Singleton
class PutDraft implements RestModifyView<DraftResource, Input> {
  static class Input {
    String id;
    String path;
    Side side;
    Integer line;
    String inReplyTo;
    Timestamp updated; // Accepted but ignored.
    CommentRange range;

    @DefaultInput
    String message;
  }

  private final Provider<ReviewDb> db;
  private final DeleteDraft delete;
  private final PatchLineCommentsUtil plcUtil;
  private final ChangeUpdate.Factory updateFactory;
  private final PatchListCache patchListCache;

  @Inject
  PutDraft(Provider<ReviewDb> db,
      DeleteDraft delete,
      PatchLineCommentsUtil plcUtil,
      ChangeUpdate.Factory updateFactory,
      PatchListCache patchListCache) {
    this.db = db;
    this.delete = delete;
    this.plcUtil = plcUtil;
    this.updateFactory = updateFactory;
    this.patchListCache = patchListCache;
  }

  @Override
  public Response<CommentInfo> apply(DraftResource rsrc, Input in) throws
      BadRequestException, OrmException, IOException {
    PatchLineComment c = rsrc.getComment();
    ChangeUpdate update = updateFactory.create(rsrc.getControl());
    if (in == null || in.message == null || in.message.trim().isEmpty()) {
      return delete.apply(rsrc, null);
    } else if (in.id != null && !rsrc.getId().equals(in.id)) {
      throw new BadRequestException("id must match URL");
    } else if (in.line != null && in.line < 0) {
      throw new BadRequestException("line must be >= 0");
    } else if (in.line != null && in.range != null && in.line != in.range.getEndLine()) {
      throw new BadRequestException("range endLine must be on the same line as the comment");
    }

    if (in.path != null
        && !in.path.equals(c.getKey().getParentKey().getFileName())) {
      // Updating the path alters the primary key, which isn't possible.
      // Delete then recreate the comment instead of an update.

      plcUtil.deleteComments(db.get(), update, Collections.singleton(c));
      c = new PatchLineComment(
          new PatchLineComment.Key(
              new Patch.Key(rsrc.getPatchSet().getId(), in.path),
              c.getKey().get()),
          c.getLine(),
          rsrc.getAuthorId(),
          c.getParentUuid(), TimeUtil.nowTs());
      setCommentRevId(c, patchListCache, rsrc.getChange(), rsrc.getPatchSet());
      plcUtil.insertComments(db.get(), update,
          Collections.singleton(update(c, in)));
    } else {
      if (c.getRevId() == null) {
        setCommentRevId(c, patchListCache, rsrc.getChange(),
            rsrc.getPatchSet());
      }
      plcUtil.updateComments(db.get(), update,
          Collections.singleton(update(c, in)));
    }
    update.commit();
    return Response.ok(new CommentInfo(c, null));
  }

  private PatchLineComment update(PatchLineComment e, Input in) {
    if (in.side != null) {
      e.setSide(in.side == Side.PARENT ? (short) 0 : (short) 1);
    }
    if (in.inReplyTo != null) {
      e.setParentUuid(Url.decode(in.inReplyTo));
    }
    e.setMessage(in.message.trim());
    if (in.range != null || in.line != null) {
      e.setRange(in.range);
      e.setLine(in.range != null ? in.range.getEndLine() : in.line);
    }
    e.setWrittenOn(TimeUtil.nowTs());
    return e;
  }
}
