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

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ListChangeComments implements RestReadView<ChangeResource> {
  private final ChangeMessagesUtil changeMessagesUtil;
  private final ChangeData.Factory changeDataFactory;
  private final Provider<CommentJson> commentJson;
  private final CommentsUtil commentsUtil;

  @Inject
  ListChangeComments(
      ChangeData.Factory changeDataFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil,
      ChangeMessagesUtil changeMessagesUtil) {
    this.changeDataFactory = changeDataFactory;
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
    this.changeMessagesUtil = changeMessagesUtil;
  }

  @Override
  public Response<Map<String, List<CommentInfo>>> apply(ChangeResource rsrc)
      throws AuthException, PermissionBackendException {
    return Response.ok(getAsMap(listComments(rsrc), rsrc));
  }

  public List<CommentInfo> getComments(ChangeResource rsrc) throws PermissionBackendException {
    return getAsList(listComments(rsrc), rsrc);
  }

  private Iterable<HumanComment> listComments(ChangeResource rsrc) {
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    return commentsUtil.publishedHumanCommentsByChange(cd.notes());
  }

  private ImmutableList<CommentInfo> getAsList(Iterable<HumanComment> comments, ChangeResource rsrc)
      throws PermissionBackendException {
    ImmutableList<CommentInfo> commentInfos = getCommentFormatter().formatAsList(comments);
    List<ChangeMessage> changeMessages = changeMessagesUtil.byChange(rsrc.getNotes());
    CommentsUtil.linkCommentsToChangeMessages(commentInfos, changeMessages, true);
    return commentInfos;
  }

  private Map<String, List<CommentInfo>> getAsMap(
      Iterable<HumanComment> comments, ChangeResource rsrc) throws PermissionBackendException {
    Map<String, List<CommentInfo>> commentInfosMap = getCommentFormatter().format(comments);
    List<CommentInfo> commentInfos =
        commentInfosMap.values().stream().flatMap(List::stream).collect(toList());
    List<ChangeMessage> changeMessages = changeMessagesUtil.byChange(rsrc.getNotes());
    CommentsUtil.linkCommentsToChangeMessages(commentInfos, changeMessages, true);
    return commentInfosMap;
  }

  private CommentJson.HumanCommentFormatter getCommentFormatter() {
    return commentJson.get().setFillAccounts(true).setFillPatchSet(true).newHumanCommentFormatter();
  }
}
