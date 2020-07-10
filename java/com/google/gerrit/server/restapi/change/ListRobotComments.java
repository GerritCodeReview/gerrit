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

package com.google.gerrit.server.restapi.change;

import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentContextException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.restapi.change.CommentJson.RobotCommentFormatter;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ListRobotComments implements RestReadView<RevisionResource> {
  protected final Provider<CommentJson> commentJson;
  protected final CommentsUtil commentsUtil;
  protected final ChangeMessagesUtil changeMessagesUtil;

  @Inject
  ListRobotComments(
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil,
      ChangeMessagesUtil changeMessagesUtil) {
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
    this.changeMessagesUtil = changeMessagesUtil;
  }

  @Override
  public Response<Map<String, List<RobotCommentInfo>>> apply(RevisionResource rsrc)
      throws PermissionBackendException, CommentContextException {
    return Response.ok(getAsMap(listComments(rsrc), rsrc));
  }

  public ImmutableList<RobotCommentInfo> getComments(RevisionResource rsrc)
      throws PermissionBackendException, CommentContextException {
    return getAsList(listComments(rsrc), rsrc);
  }

  private ImmutableList<RobotCommentInfo> getAsList(
      Iterable<RobotComment> comments, RevisionResource rsrc)
      throws PermissionBackendException, CommentContextException {
    ImmutableList<RobotCommentInfo> commentInfos = getCommentFormatter().formatAsList(comments);
    List<ChangeMessage> changeMessages = changeMessagesUtil.byChange(rsrc.getNotes());
    CommentsUtil.linkCommentsToChangeMessages(commentInfos, changeMessages, false);
    return commentInfos;
  }

  private Map<String, List<RobotCommentInfo>> getAsMap(
      Iterable<RobotComment> comments, RevisionResource rsrc)
      throws PermissionBackendException, CommentContextException {
    Map<String, List<RobotCommentInfo>> commentInfosMap = getCommentFormatter().format(comments);
    List<RobotCommentInfo> commentInfos =
        commentInfosMap.values().stream().flatMap(List::stream).collect(toList());
    List<ChangeMessage> changeMessages = changeMessagesUtil.byChange(rsrc.getNotes());
    CommentsUtil.linkCommentsToChangeMessages(commentInfos, changeMessages, false);
    return commentInfosMap;
  }

  private Iterable<RobotComment> listComments(RevisionResource rsrc) {
    return commentsUtil.robotCommentsByPatchSet(rsrc.getNotes(), rsrc.getPatchSet().id());
  }

  private RobotCommentFormatter getCommentFormatter() {
    return commentJson.get().setFillAccounts(true).newRobotCommentFormatter();
  }
}
