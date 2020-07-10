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

import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.CommentContextException;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.List;
import java.util.Map;

public class ListChangeRobotComments implements RestReadView<ChangeResource> {
  private final ChangeData.Factory changeDataFactory;
  private final Provider<CommentJson> commentJson;
  private final CommentsUtil commentsUtil;
  private final ChangeMessagesUtil changeMessagesUtil;

  @Inject
  ListChangeRobotComments(
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
  public Response<Map<String, List<RobotCommentInfo>>> apply(ChangeResource rsrc)
      throws AuthException, PermissionBackendException, CommentContextException {
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    Map<String, List<RobotCommentInfo>> robotCommentsMap =
        commentJson
            .get()
            .setFillAccounts(true)
            .setFillPatchSet(true)
            .newRobotCommentFormatter()
            .format(commentsUtil.robotCommentsByChange(cd.notes()));
    List<RobotCommentInfo> commentInfos =
        robotCommentsMap.values().stream().flatMap(List::stream).collect(toList());
    List<ChangeMessage> changeMessages = changeMessagesUtil.byChange(rsrc.getNotes());
    CommentsUtil.linkCommentsToChangeMessages(commentInfos, changeMessages, false);
    return Response.ok(robotCommentsMap);
  }
}
