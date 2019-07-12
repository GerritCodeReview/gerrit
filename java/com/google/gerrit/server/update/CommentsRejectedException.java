// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.update;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import java.util.Collection;
import java.util.stream.Collectors;

/** Thrown when comment validation rejected a comment, preventing it from being published. */
public class CommentsRejectedException extends Exception {
  private static final long serialVersionUID = 1L;

  private final ImmutableList<CommentValidationFailure> commentValidationFailures;

  public CommentsRejectedException(Collection<CommentValidationFailure> commentValidationFailures) {
    this.commentValidationFailures = ImmutableList.copyOf(commentValidationFailures);
  }

  @Override
  public String getMessage() {
    return "One or more comments were rejected in validation: "
        + commentValidationFailures
            .stream()
            .map(CommentValidationFailure::getMessage)
            .collect(Collectors.joining("; "));
  }

  /**
   * Returns the validation failures that caused this exception. By contract this list is never
   * empty.
   */
  public ImmutableList<CommentValidationFailure> getCommentValidationFailures() {
    return commentValidationFailures;
  }
}
