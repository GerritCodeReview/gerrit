package com.google.gerrit.server.git.validators;

// XXX Should other limits be moved here as well?
/** Defaults for various limits that can be set in the server config. */
public class DefaultLimits {
  /**
   * The maximum number of comments (regular + robot) per change. New comments are rejected when
   * this limit would be exceeded.
   */
  public static final int MAX_NUM_COMMENTS_PER_CHANGE = 5_000;

  private DefaultLimits() {}
}
