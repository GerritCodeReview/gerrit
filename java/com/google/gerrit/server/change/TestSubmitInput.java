package com.google.gerrit.server.change;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import java.util.Queue;

/**
 * Subclass of {@link SubmitInput} with special bits that may be flipped for testing purposes only.
 */
@VisibleForTesting
public class TestSubmitInput extends SubmitInput {
  public boolean failAfterRefUpdates;

  /**
   * For each change being submitted, an element is removed from this queue and, if the value is
   * true, a bogus ref update is added to the batch, in order to generate a lock failure during
   * execution.
   */
  public Queue<Boolean> generateLockFailures;
}
