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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class SubmissionExecutor {

  private final ImmutableList<SubmissionListener> submissionListeners;
  private final boolean dryrun;
  private ImmutableList<BatchUpdateListener> additionalListeners = ImmutableList.of();

  public SubmissionExecutor(boolean dryrun, ImmutableList<SubmissionListener> submissionListeners) {
    this.dryrun = dryrun;
    this.submissionListeners = submissionListeners;
    if (dryrun) {
      submissionListeners.forEach(SubmissionListener::setDryrun);
    }
  }

  /**
   * Set additional listeners. These can be set again in each try (or will be reused if not
   * overwritten).
   */
  public void setAdditionalBatchUpdateListeners(
      ImmutableList<BatchUpdateListener> additionalListeners) {
    this.additionalListeners = additionalListeners;
  }

  /** Execute the batch updates, reporting to all the Submission and BatchUpdateListeners. */
  public void execute(Collection<BatchUpdate> updates) throws RestApiException, UpdateException {
    submissionListeners.forEach(l -> l.beforeBatchUpdates(updates));

    ImmutableList<BatchUpdateListener> listeners =
        new ImmutableList.Builder<BatchUpdateListener>()
            .addAll(additionalListeners)
            .addAll(
                submissionListeners.stream()
                    .map(l -> l.listensToBatchUpdates())
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList()))
            .build();
    BatchUpdate.execute(updates, listeners, dryrun);
  }

  /**
   * Caller invokes this when done with the submission (either because everything succeeded or gave
   * up retrying).
   */
  public void afterExecutions(MergeOpRepoManager orm) {
    submissionListeners.forEach(l -> l.afterSubmission(orm));
  }
}
