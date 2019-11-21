package com.google.gerrit.server.notedb;

import com.google.gerrit.exceptions.StorageException;

/**
 * A write operation was rejected because a limit would be exceeded. Limits are currently imposed
 * on:
 *
 * <ul>
 *   <li>The number of NoteDB updates per change.</li>
 *   <li>The number of patch sets per change.
 * </ul>
 */
public class LimitExceededException extends StorageException {
  private static final long serialVersionUID = 1L; // ö likely not needed

  LimitExceededException(String message) {
    super(message);
  }
}
