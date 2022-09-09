package com.google.gerrit.server.update;

import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate.ChangeUpdateListener;

/**
 * Interface for listening change updates during batch update execution.
 *
 * <p>This interface shouldn't be used for critical operation, because postUpdate operation might
 * not be called if a process crashed.
 *
 * <p>For each BatchUpdate operation, a new BatchChangeUpdateListener should be created/injected.
 * Implementation can be non thread-safe.
 */
public interface BatchChangeUpdateListener {
  BatchChangeUpdateListener EMPTY = new BatchChangeUpdateListener() {};

  /** Returns a ChangeUpdateListener which will receive notifications about change updates. */
  default ChangeUpdateListener getChangeUpdateListener(ChangeNotes changeNotes) {
    return ChangeUpdateListener.EMPTY;
  }
  /** Called when all change updates are applied and stored. */
  default void postUpdate(PostUpdateContext ctx) {}
}
