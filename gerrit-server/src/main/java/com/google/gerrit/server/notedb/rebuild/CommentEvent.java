// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.notedb.rebuild;

import static com.google.gerrit.server.CommentsUtil.setCommentRevId;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gwtorm.server.OrmException;

class CommentEvent extends Event {
  public final Comment c;
  private final Change change;
  private final PatchSet ps;
  private final PatchListCache cache;

  CommentEvent(Comment c, Change change, PatchSet ps, PatchListCache cache) {
    super(
        CommentsUtil.getCommentPsId(change.getId(), c),
        c.author.getId(),
        c.getRealAuthor().getId(),
        c.writtenOn,
        change.getCreatedOn(),
        c.tag);
    this.c = c;
    this.change = change;
    this.ps = ps;
    this.cache = cache;
  }

  @Override
  boolean uniquePerUpdate() {
    return false;
  }

  @Override
  void apply(ChangeUpdate update) throws OrmException {
    checkUpdate(update);
    if (c.revId == null) {
      setCommentRevId(c, cache, change, ps);
    }
    update.putComment(PatchLineComment.Status.PUBLISHED, c);
  }
}
