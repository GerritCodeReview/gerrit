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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.annotations.ExtensionPoint;

/**
 * Validates review comments and messages. Rejecting any comment/message will prevent all comments
 * from being published.
 */
@ExtensionPoint
public interface CommentValidationListener {

  /** The type of comment. */
  enum CommentType {
    /** A regular (inline or file) comment. */
    INLINE_OR_FILE_COMMENT,
    /** A change message. */
    CHANGE_MESSAGE,
    /** A comment or message received via email. */
    EMAIL_COMMENT_OR_MESSAGE
  }

  /**
   * Validate the specified commits.
   *
   * @return An empty list if all commits are valid, or else a list of validation failures.
   */
  ImmutableList<CommentValidationFailure> validateComments(
      ImmutableList<CommentForValidation> comments);
}
