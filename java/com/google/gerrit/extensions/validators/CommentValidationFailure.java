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

import com.google.auto.value.AutoValue;

/** A comment or review message was rejected by a {@link CommentValidator}. */
@AutoValue
public abstract class CommentValidationFailure {
  static CommentValidationFailure create(
      CommentForValidation commentForValidation, String message) {
    return new AutoValue_CommentValidationFailure(commentForValidation, message);
  }

  /** Returns the offending comment. */
  public abstract CommentForValidation getComment();

  /** A friendly message set by the {@link CommentValidator}. */
  public abstract String getMessage();
}
