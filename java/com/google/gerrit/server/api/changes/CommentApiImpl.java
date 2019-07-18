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

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.changes.CommentApi;
import com.google.gerrit.extensions.api.changes.DeleteCommentInput;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.change.CommentResource;
import com.google.gerrit.server.restapi.change.DeleteComment;
import com.google.gerrit.server.restapi.change.GetComment;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

class CommentApiImpl implements CommentApi {
  interface Factory {
    CommentApiImpl create(CommentResource c);
  }

  private final GetComment getComment;
  private final DeleteComment deleteComment;
  private final CommentResource comment;

  @Inject
  CommentApiImpl(
      GetComment getComment, DeleteComment deleteComment, @Assisted CommentResource comment) {
    this.getComment = getComment;
    this.deleteComment = deleteComment;
    this.comment = comment;
  }

  @Override
  public CommentInfo get() throws RestApiException {
    try {
      return getComment.apply(comment).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot retrieve comment", e);
    }
  }

  @Override
  public CommentInfo delete(DeleteCommentInput input) throws RestApiException {
    try {
      return deleteComment.apply(comment, input).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot delete comment", e);
    }
  }
}
