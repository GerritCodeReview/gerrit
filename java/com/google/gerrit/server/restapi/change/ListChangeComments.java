// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class ListChangeComments extends ListChangeDrafts {
  public static int MAX_COMMENTS_IN_LIST = 1000;
  public static int MAX_COMMENT_SIZE = 32;

  @Inject
  ListChangeComments(
      ChangeData.Factory changeDataFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil) {
    super(changeDataFactory, commentJson, commentsUtil);
  }

  @Override
  protected Iterable<Comment> listComments(ChangeResource rsrc) {
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    List<Comment> publishedComments = commentsUtil.publishedByChange(cd.notes());

    if (publishedComments.size() < MAX_COMMENTS_IN_LIST) {
      return publishedComments;
    }

    return publishedComments.stream()
        .map(ListChangeComments::clearComment)
        .collect(Collectors.toList());
  }

  private static Comment clearComment(Comment comment) {
    if (comment.message != null && comment.message.length() > MAX_COMMENT_SIZE) {
      comment.message = comment.message.substring(0, MAX_COMMENT_SIZE - 3) + "...";
    }
    return comment;
  }

  @Override
  protected boolean includeAuthorInfo() {
    return true;
  }

  @Override
  public boolean requireAuthentication() {
    return false;
  }
}
