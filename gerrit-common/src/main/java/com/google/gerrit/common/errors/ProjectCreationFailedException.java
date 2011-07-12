package com.google.gerrit.common.errors;

public class ProjectCreationFailedException extends Exception {
  private static final long serialVersionUID = 1L;

  public ProjectCreationFailedException(final String message) {
    this(message, null);
  }

  public ProjectCreationFailedException(final String message, final Throwable why) {
    super(message, why);
  }
}
