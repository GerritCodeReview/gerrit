package com.google.gerrit.server.update;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import java.util.Collection;

/** Thrown when comment validation rejected a comment, preventing it from being published. */
public class CommentsRejectedException extends Exception {
  private static final long serialVersionUID = 1L;

  private final ImmutableList<CommentValidationFailure> commentValidationFailures;

  public CommentsRejectedException(Collection<CommentValidationFailure> commentValidationFailures) {
    this.commentValidationFailures = ImmutableList.copyOf(commentValidationFailures);
  }

  /**
   * Returns the validation failures that caused this exception. By contract this list is never
   * empty.
   */
  public ImmutableList<CommentValidationFailure> getCommentValidationFailures() {
    return commentValidationFailures;
  }
}
