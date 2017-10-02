package com.google.gerrit.server.patch;

/**
 * Exception thrown when the PatchList could not be computed because previous attempts failed with
 * {@code LargeObjectException}. This is not thrown on the first computation.
 */
public class PatchListObjectTooLargeException extends PatchListNotAvailableException {

  public PatchListObjectTooLargeException(String message) {
    super(message);
  }
}
