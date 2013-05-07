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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.change.PutDraftComment.Input;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collections;

class CreateDraftComment implements RestModifyView<RevisionResource, Input> {
  private final Provider<ReviewDb> db;

  @Inject
  CreateDraftComment(Provider<ReviewDb> db) {
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
    }

    PatchLineComment c = new PatchLineComment(
        new PatchLineComment.Key(
            new Patch.Key(rsrc.getPatchSet().getId(), in.path),
            ChangeUtil.messageUUID(db.get())),
        in.line != null ? in.line : 0,
        rsrc.getAccountId(),
        Url.decode(in.inReplyTo));
    c.setStatus(Status.DRAFT);
    c.setSide(in.side == CommentInfo.Side.PARENT ? (short) 0 : (short) 1);
    c.setMessage(in.message.trim());
    db.get().patchComments().insert(Collections.singleton(c));
    return Response.created(new CommentInfo(c, null));
  }
}
