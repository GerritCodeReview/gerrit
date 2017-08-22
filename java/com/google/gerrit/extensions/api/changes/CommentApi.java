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

package com.google.gerrit.extensions.api.changes;

import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.restapi.NotImplementedException;
import com.google.gerrit.extensions.restapi.RestApiException;

public interface CommentApi {
  CommentInfo get() throws RestApiException;

  /**
   * Deletes a published comment of a revision. For NoteDb, it deletes the comment by rewriting the
   * commit history.
   *
   * <p>Note instead of deleting the whole comment, this endpoint just replaces the comment's
   * message.
   *
   * @return the comment with its message updated.
   */
  CommentInfo delete(DeleteCommentInput input) throws RestApiException;

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements CommentApi {
    @Override
    public CommentInfo get() throws RestApiException {
      throw new NotImplementedException();
    }

    @Override
    public CommentInfo delete(DeleteCommentInput input) throws RestApiException {
      throw new NotImplementedException();
    }
  }
}
