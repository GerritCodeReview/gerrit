package com.google.gerrit.server.update;

import java.util.Collection;

/**
 * Status and progress of a submission.
 *
 * <p>{@link MultibatchExecutor} reports the progress of the submission through this interface. An
 * instance should be reused between retries but not for different submissions.
 */
public interface SubmissionListener {

  /**
   * Submission will execute these updates.
   *
   * <p>The BatchUpdates haven't execute anything yet.
   *
   * <p>This method is called once per submission try. The retry calls can have only a subset of the
   * BatchUpdates (what failed in the previous attempt). On retries the BatchUpdates are not reused.
   * Implementations must store intermediate results if needed on {@link #completed()}.
   *
   * @param dryrun will the final BatchRefUpdate be executed on the db.
   * @param updates set of updates to execute in this try of the submission
   */
  void setBatchUpdates(boolean dryrun, Collection<BatchUpdate> updates);

  /** Submission completed successfully (either success or giving up retrying). */
  void completed();

  /**
   * If the submission needs to know more about the BatchUpdate execution, it can provide a {@link
   * BatchUpdateListener}.
   *
   * @return a BatchUpdateListener
   */
  BatchUpdateListener getBatchListener();
}
