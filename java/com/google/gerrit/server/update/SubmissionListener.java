// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.update;

import java.util.Collection;
import java.util.Optional;

/**
 * Status and progress of a submission.
 *
 * <p>{@link SubmissionExecutor} reports the progress of the submission through this interface. An
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
   * Implementations must store intermediate results if needed on {@link #afterSubmission()}.
   *
   * @param dryrun will the final BatchRefUpdate be executed on the db.
   * @param updates updates to execute in this try of the submission. Implementations should not
   *     modify them.
   */
  void beforeBatchUpdates(boolean dryrun, Collection<BatchUpdate> updates);

  /**
   * Submission completed (either success or giving up retrying).
   *
   * <p>This is called after all (successfull) updates have been committed to storage and there
   * won't be more retries.
   */
  void afterSubmission();

  /**
   * If the submission needs to know more about the BatchUpdate execution, it can provide a {@link
   * BatchUpdateListener}.
   *
   * @return a BatchUpdateListener
   */
  Optional<BatchUpdateListener> listenToBatchUpdates();
}
