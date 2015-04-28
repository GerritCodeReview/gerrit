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

import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PatchLineCommentsUtil;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;

@Singleton
public class CreateDraftComment implements RestModifyView<RevisionResource, DraftInput> {
  private final Provider<ReviewDb> db;
  private final ChangeUpdate.Factory updateFactory;
  private final Provider<CommentJson> commentJson;
  private final PatchLineCommentsUtil plcUtil;
  private final PatchListCache patchListCache;

  @Inject
  CreateDraftComment(Provider<ReviewDb> db,
      ChangeUpdate.Factory updateFactory,
      Provider<CommentJson> commentJson,
      PatchLineCommentsUtil plcUtil,
      PatchListCache patchListCache) {
    this.db = db;
    this.updateFactory = updateFactory;
    this.commentJson = commentJson;
    this.plcUtil = plcUtil;
    this.patchListCache = patchListCache;
  }

  @Override
  public Response<CommentInfo> apply(RevisionResource rsrc, DraftInput in)
      throws BadRequestException, OrmException, IOException {
    if (Strings.isNullOrEmpty(in.path)) {
      throw new BadRequestException("path must be non-empty");
    } else if (in.message == null || in.message.trim().isEmpty()) {
      throw new BadRequestException("message must be non-empty");
    } else if (in.line != null && in.line <= 0) {
      throw new BadRequestException("line must be > 0");
    } else if (in.line != null && in.range != null && in.line != in.range.endLine) {
      throw new BadRequestException("range endLine must be on the same line as the comment");
    }

    int line = in.line != null
        ? in.line
        : in.range != null ? in.range.endLine : 0;

    Timestamp now = TimeUtil.nowTs();
    ChangeUpdate update = updateFactory.create(rsrc.getControl(), now);

    PatchLineComment c = new PatchLineComment(
        new PatchLineComment.Key(
            new Patch.Key(rsrc.getPatchSet().getId(), in.path),
            ChangeUtil.messageUUID(db.get())),
        line, rsrc.getAccountId(), Url.decode(in.inReplyTo), now);
    c.setSide(in.side == Side.PARENT ? (short) 0 : (short) 1);
    c.setMessage(in.message.trim());
    c.setRange(in.range);
    setCommentRevId(c, patchListCache, rsrc.getChange(), rsrc.getPatchSet());
    plcUtil.insertComments(db.get(), update, Collections.singleton(c));
    update.commit();
    return Response.created(commentJson.get().setFillAccounts(false).format(c));
  }
}
