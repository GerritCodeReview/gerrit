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
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.common.ChangeInfo;

/**
 * Validates review comments and messages. Rejecting any comment/message will prevent all comments
 * from being published.
 */
@ExtensionPoint
public interface CommentValidator {

  /**
   * Validate the specified comments.
   *
   * @return An empty list if all comments are valid, or else a list of validation failures.
   */
  default ImmutableList<CommentValidationFailure> validateComments(
      ImmutableList<CommentForValidation> comments) {
    return validateCommentsWithContext(comments, null);
  };

  /**
   * Validate the specified comments with additional context.
   *
   * @param ctx Nullable context used during comment validation.
   * @return An empty list if all comments are valid, or else a list of validation failures.
   */
  ImmutableList<CommentValidationFailure> validateCommentsWithContext(
      ImmutableList<CommentForValidation> comments, @Nullable CommentValidationContext ctx);
}
