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

package com.google.gerrit.extensions.validators;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.InlineMe;

/**
 * A comment or review message was rejected by a {@link CommentValidator}.
 *
 * @param comment Returns the offending comment.
 * @param message A friendly message set by the {@link CommentValidator}.
 */
public record CommentValidationFailure(CommentForValidation comment, String message) {
  public CommentValidationFailure {
    requireNonNull(comment, "comment");
    requireNonNull(message, "message");
  }

  @InlineMe(replacement = "this.comment()")
  public CommentForValidation getComment() {
    return comment();
  }

  @InlineMe(replacement = "this.message()")
  public String getMessage() {
    return message();
  }

  static CommentValidationFailure create(
      CommentForValidation commentForValidation, String message) {
    return new CommentValidationFailure(commentForValidation, message);
  }

}
