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

package com.google.gerrit.server.api.changes;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.changes.RobotCommentApi;
import com.google.gerrit.extensions.common.RobotCommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.RobotCommentResource;
import com.google.gerrit.server.restapi.change.GetRobotComment;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class RobotCommentApiImpl implements RobotCommentApi {
  interface Factory {
    RobotCommentApiImpl create(RobotCommentResource c);
  }

  private final GetRobotComment getComment;
  private final RobotCommentResource comment;

  @Inject
  RobotCommentApiImpl(GetRobotComment getComment, @Assisted RobotCommentResource comment) {
    this.getComment = getComment;
    this.comment = comment;
  }

  @Override
  public RobotCommentInfo get() throws RestApiException {
    try {
      return getComment.apply(comment).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve robot comment", e);
    }
  }
}
