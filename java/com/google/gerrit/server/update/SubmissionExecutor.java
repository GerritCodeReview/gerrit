package com.google.gerrit.server.update;

import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Collection;
import java.util.List;

public class SubmissionExecutor {

  /** Execute and complete the submission as there are no retries */
  public static void execute(
      Collection<BatchUpdate> updates, boolean dryrun, SubmissionListener onSubmission)
      throws RestApiException, UpdateException {
    onSubmission.setBatchUpdates(dryrun, updates);
    MultibatchExecutor.execute(updates, dryrun, onSubmission.getBatchListener());
    onSubmission.completed();
  }

  public static void executeInRetry(
      List<BatchUpdate> updates,
      boolean dryrun,
      SubmissionListener onSubmission,
      BatchUpdateListener additionalBatchUpdateListener)
      throws RestApiException, UpdateException {
    onSubmission.setBatchUpdates(dryrun, updates);
    MultibatchExecutor.execute(
        updates,
        dryrun,
        BatchUpdateListenerChain.of(
            onSubmission.getBatchListener(), additionalBatchUpdateListener));
  }
}
