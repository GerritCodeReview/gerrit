package com.google.gerrit.extensions.validators;

import com.google.auto.value.AutoValue;

/** A comment or review message was rejected by a {@link CommentValidationListener}. */
@AutoValue
public abstract class CommentValidationFailure {
  static CommentValidationFailure create(
      CommentForValidation commentForValidation, String message) {
    return new AutoValue_CommentValidationFailure(commentForValidation, message);
  }

  /** Returns the offending comment. */
  public abstract CommentForValidation getComment();

  /** A friendly message set by the {@link CommentValidationListener}. */
  public abstract String getMessage();
}
