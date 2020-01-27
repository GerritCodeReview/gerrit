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

/**
 * Holds a comment's text and some metadata in order to pass it to a validation plugin.
 *
 * @see CommentValidator
 */
@AutoValue
public abstract class CommentForValidation {

  /** The creator of the comment. */
  public enum CommentSource {
    /** A regular user comment. */
    HUMAN,
    /** A robot comment. */
    ROBOT
  }

  /** The type of comment. */
  public enum CommentType {
    /** A regular (inline) comment. */
    INLINE_COMMENT,
    /** A file comment. */
    FILE_COMMENT,
    /** A change message. */
    CHANGE_MESSAGE
  }

  public static CommentForValidation create(
      CommentSource source, CommentType type, String text, long size) {
    return new AutoValue_CommentForValidation(source, type, text, size);
  }

  public abstract CommentSource getSource();

  public abstract CommentType getType();

  /**
   * Returns the comment text. Note that especially for robot comments the total size may be
   * significantly larger and should be determined by using {@link #getApproximateSize()}.
   */
  public abstract String getText();

  /**
   * Returns this instance's approximate size in bytes for the purpose of applying size limits. For
   * robot comments this may be significantly larger than the size of the comment text.
   */
  public abstract long getApproximateSize();

  public CommentValidationFailure failValidation(String message) {
    return CommentValidationFailure.create(this, message);
  }
}
