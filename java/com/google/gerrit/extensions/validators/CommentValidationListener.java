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
