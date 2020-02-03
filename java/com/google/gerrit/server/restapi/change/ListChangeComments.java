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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Comparator;
import java.util.List;

@Singleton
public class ListChangeComments extends ListChangeDrafts {
  private final ChangeMessagesUtil changeMessagesUtil;

  @Inject
  ListChangeComments(
      ChangeData.Factory changeDataFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil,
      ChangeMessagesUtil changeMessagesUtil) {
    super(changeDataFactory, commentJson, commentsUtil);
    this.changeMessagesUtil = changeMessagesUtil;
  }

  @Override
  protected Iterable<Comment> listComments(ChangeResource rsrc) {
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    return commentsUtil.publishedByChange(cd.notes());
  }

  @Override
  protected boolean includeAuthorInfo() {
    return true;
  }

  @Override
  public boolean requireAuthentication() {
    return false;
  }

  @Override
  protected void postOp(ImmutableList<CommentInfo> commentInfos, ChangeResource rsrc) {
    linkCommentsToChangeMessages(Lists.newArrayList(commentInfos), rsrc);
  }

  /**
   * This method populates the "changeMessageID" field of the comments parameter based on timestamp
   * matching. The comments parameter will be modified. We assume that both lists are sorted
   *
   * <p>Each comment will be matched to the nearest next change message in timestamp
   *
   * @param comments the list of comments
   * @param rsrc the change resource
   */
  private void linkCommentsToChangeMessages(List<CommentInfo> comments, ChangeResource rsrc) {
    List<ChangeMessage> changeMessages =
        Lists.newArrayList(changeMessagesUtil.byChange(rsrc.getNotes()));
    changeMessages.sort(Comparator.comparing(ChangeMessage::getWrittenOn));
    comments.sort(Comparator.comparing(c -> c.updated));
    int cmItr = 0;
    for (CommentInfo comment : comments) {
      while (cmItr < changeMessages.size()
          && comment.updated.after(changeMessages.get(cmItr).getWrittenOn())) {
        cmItr += 1;
      }
      if (cmItr < changeMessages.size()) {
        comment.changeMessageId = changeMessages.get(cmItr).getKey().uuid();
      }
    }
  }
}
