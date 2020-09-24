package com.google.gerrit.server.update;

import com.google.gerrit.extensions.restapi.RestApiException;
import java.util.Collection;
import java.util.List;

public class SubmissionExecutor {

  /** Execute and complete the submission as there are no retries */
  public static void execute(
      Collection<BatchUpdate> updates, SubmissionListener onSubmission, boolean dryrun)
      throws RestApiException, UpdateException {
    onSubmission.setBatchUpdates(dryrun, updates);
    BatchUpdate.execute(updates, onSubmission.getBatchListener(), dryrun);
    onSubmission.completed();
  }

  public static void executeInRetry(
      List<BatchUpdate> updates,
      SubmissionListener onSubmission,
      BatchUpdateListener additionalBatchUpdateListener,
      boolean dryrun)
      throws RestApiException, UpdateException {
    onSubmission.setBatchUpdates(dryrun, updates);
    BatchUpdate.execute(
        updates,
        BatchUpdateListenerChain.of(onSubmission.getBatchListener(), additionalBatchUpdateListener),
        dryrun);
  }
}
