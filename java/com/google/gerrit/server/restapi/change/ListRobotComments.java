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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.RobotComment;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ListRobotComments implements RestReadView<RevisionResource> {
  protected final Provider<CommentJson> commentJson;
  protected final CommentsUtil commentsUtil;

  @Inject
  ListRobotComments(Provider<CommentJson> commentJson, CommentsUtil commentsUtil) {
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
  }

  @Override
  public Response<Map<String, List<RobotCommentInfo>>> apply(RevisionResource rsrc)
      throws PermissionBackendException {
    return Response.ok(
        commentJson
            .get()
            .setFillAccounts(true)
            .newRobotCommentFormatter()
            .format(listComments(rsrc)));
  }

  public ImmutableList<RobotCommentInfo> getComments(RevisionResource rsrc)
      throws PermissionBackendException {
    return commentJson
        .get()
        .setFillAccounts(true)
        .newRobotCommentFormatter()
        .formatAsList(listComments(rsrc));
  }

  private Iterable<RobotComment> listComments(RevisionResource rsrc) {
    return commentsUtil.robotCommentsByPatchSet(rsrc.getNotes(), rsrc.getPatchSet().id());
  }
}
