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

package com.google.gerrit.server.change;

import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.RobotComment;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CommentsUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public class ListRobotComments implements RestReadView<RevisionResource> {
  protected final Provider<ReviewDb> db;
  protected final Provider<CommentJson> commentJson;
  protected final CommentsUtil commentsUtil;

  @Inject
  ListRobotComments(
      Provider<ReviewDb> db, Provider<CommentJson> commentJson, CommentsUtil commentsUtil) {
    this.db = db;
    this.commentJson = commentJson;
    this.commentsUtil = commentsUtil;
  }

  @Override
  public Map<String, List<RobotCommentInfo>> apply(RevisionResource rsrc) throws OrmException {
    return commentJson
        .get()
        .setFillAccounts(true)
        .newRobotCommentFormatter()
        .format(listComments(rsrc));
  }

  public List<RobotCommentInfo> getComments(RevisionResource rsrc) throws OrmException {
    return commentJson
        .get()
        .setFillAccounts(true)
        .newRobotCommentFormatter()
        .formatAsList(listComments(rsrc));
  }

  private Iterable<RobotComment> listComments(RevisionResource rsrc) throws OrmException {
    return commentsUtil.robotCommentsByPatchSet(rsrc.getNotes(), rsrc.getPatchSet().getId());
  }
}
