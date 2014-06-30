// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.api.changes;

import com.google.common.base.Function;
import com.google.gerrit.extensions.common.Comment;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.server.api.accounts.AccountInfoMapper;

import javax.annotation.Nullable;

public class CommentInfoMapper
    implements Function<com.google.gerrit.server.change.CommentInfo, CommentInfo> {

  public static final CommentInfoMapper INSTANCE = new CommentInfoMapper();

  private CommentInfoMapper() {
  }

  @Nullable
  @Override
  public CommentInfo apply(@Nullable com.google.gerrit.server.change.CommentInfo input) {
    return fromCommentInfo(input);
  }

  public static CommentInfo fromCommentInfo(
      com.google.gerrit.server.change.CommentInfo i) {
    if (i == null) {
      return null;
    }
    CommentInfo ci = new CommentInfo();
    fromCommentInfo(i, ci);
    return ci;
  }

  public static void fromCommentInfo(
      com.google.gerrit.server.change.CommentInfo i, CommentInfo ci) {
    ci.id = i.id;
    ci.path = i.path;
    ci.side = i.side != null ? Comment.Side.valueOf(i.side.name()) : null;
    ci.line = i.line;
    ci.range = RangeMapper.fromCommentRange(i.range);
    ci.inReplyTo = i.inReplyTo;
    ci.updated = i.updated;
    ci.message = i.message;
    ci.author = AccountInfoMapper.fromAcountInfo(i.author);
  }

  public static class RangeMapper {

    private RangeMapper() {
    }

    public static Comment.Range fromCommentRange(CommentRange r) {
      if (r == null) {
        return null;
      }
      Comment.Range ri = new Comment.Range();
      fromCommentRange(r, ri);
      return ri;
    }

    public static void fromCommentRange(CommentRange r, Comment.Range ri) {
      ri.startLine = r.getStartLine();
      ri.startCharacter = r.getStartCharacter();
      ri.endLine = r.getEndLine();
      ri.endCharacter = r.getEndCharacter();
    }
  }
}
