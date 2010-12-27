package com.google.gerrit.server.git;


/**
 * Thrown when creation of a code review note fails.
 */
public class CodeReviewNoteCreationException extends Exception {
  private static final long serialVersionUID = 1L;

  public CodeReviewNoteCreationException(final String msg) {
    super(msg);
  }

  public CodeReviewNoteCreationException(final Throwable why) {
    super(why);
  }

  public CodeReviewNoteCreationException(final CodeReviewCommit commit,
      final Throwable cause) {
    super("Couldn't create code review note for the following commit: "
        + commit, cause);
  }
}
