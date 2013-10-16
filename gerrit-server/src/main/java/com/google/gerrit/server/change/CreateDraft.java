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

import com.google.common.base.Strings;
import com.google.gerrit.common.changes.Side;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.PutDraft.Input;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

class CreateDraft implements RestModifyView<RevisionResource, Input> {
  private final Provider<ReviewDb> db;

  @Inject
  CreateDraft(Provider<ReviewDb> db) {
    this.db = db;
  }

  @Override
  public Response<CommentInfo> apply(RevisionResource rsrc, Input in)
      throws AuthException, BadRequestException, ResourceConflictException, OrmException {
    if (Strings.isNullOrEmpty(in.path)) {
      throw new BadRequestException("path must be non-empty");
    } else if (in.message == null || in.message.trim().isEmpty()) {
      throw new BadRequestException("message must be non-empty");
    } else if (in.line != null && in.line <= 0) {
      throw new BadRequestException("line must be > 0");
    } else if (in.line != null && in.range != null && in.line != in.range.getEndLine()) {
      throw new BadRequestException("range endLine must be on the same line as the comment");
    }

    int line = in.line != null
        ? in.line
        : in.range != null ? in.range.getEndLine() : 0;

    PatchLineComment c = new PatchLineComment(
        new PatchLineComment.Key(
            new Patch.Key(rsrc.getPatchSet().getId(), in.path),
            ChangeUtil.messageUUID(db.get())),
        line, rsrc.getAccountId(), Url.decode(in.inReplyTo), TimeUtil.nowTs());
    c.setSide(in.side == Side.PARENT ? (short) 0 : (short) 1);
    c.setMessage(in.message.trim());
    c.setRange(in.range);
    db.get().patchComments().insert(Collections.singleton(c));
    return Response.created(new CommentInfo(c, null));
  }
}
