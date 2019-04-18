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

import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.RestReadView;
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

  @Inject
  ListChangeRobotComments(
      ChangeData.Factory changeDataFactory,
      Provider<CommentJson> commentJson,
      CommentsUtil commentsUtil) {
    this.changeDataFactory = changeDataFactory;
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
  }

  @Override
  public Map<String, List<RobotCommentInfo>> apply(ChangeResource rsrc)
      throws AuthException, PermissionBackendException {
    ChangeData cd = changeDataFactory.create(rsrc.getNotes());
    return commentJson
        .get()
        .setFillAccounts(true)
        .setFillPatchSet(true)
        .newRobotCommentFormatter()
        .format(commentsUtil.robotCommentsByChange(cd.notes()));
  }
}
