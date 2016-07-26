package com.google.gerrit.server.git;

/**
 * Indicates that the change or commit is already in the source tree.
 */
public class ChangeAlreadyMergedException extends MergeIdenticalTreeException {
  private static final long serialVersionUID = 1L;

  /** @param msg message to return to the client describing the error. */
  public ChangeAlreadyMergedException(String msg) {
    super(msg);
  }
}
