package com.google.gerrit.server.notedb;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.Change;

/**
 * Exception indicating that the change has received too many updates. Further actions apart from
 * {@code abandon} or {@code submit} are blocked.
 */
public class TooManyUpdatesException extends StorageException {
  @VisibleForTesting
  public static String message(Change.Id id, int maxUpdates) {
    return "Change "
        + id
        + " may not exceed "
        + maxUpdates
        + " updates. It may still be abandoned or submitted. To continue working on this "
        + "change, recreate it with a new Change-Id, then abandon this one.";
  }

  private static final long serialVersionUID = 1L;

  TooManyUpdatesException(Change.Id id, int maxUpdates) {
    super(message(id, maxUpdates));
  }
}
