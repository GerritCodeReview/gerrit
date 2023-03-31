// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.gerrit.metrics.Counter3;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/** Metrics for the rebase REST endpoints ({@link Rebase} and {@link RebaseChain}). */
@Singleton
public class RebaseMetrics {
  private final Counter3<Boolean, Boolean, Boolean> countRebases;

  @Inject
  public RebaseMetrics(MetricMaker metricMaker) {
    this.countRebases =
        metricMaker.newCounter(
            "change/count_rebases",
            new Description("Total number of rebases").setRate(),
            Field.ofBoolean("on_behalf_of_uploader", (metadataBuilder, isOnBehalfOfUploader) -> {})
                .description("Whether the rebase was done on behalf of the uploader.")
                .build(),
            Field.ofBoolean("rebase_chain", (metadataBuilder, isRebaseChain) -> {})
                .description("Whether a chain was rebased.")
                .build(),
            Field.ofBoolean("allow_conflicts", (metadataBuilder, allow_conflicts) -> {})
                .description("Whether the rebase was done with allowing conflicts.")
                .build());
  }

  public void countRebase(boolean isOnBehalfOfUploader, boolean allowConflicts) {
    countRebase(isOnBehalfOfUploader, /* isRebaseChain= */ false, allowConflicts);
  }

  public void countRebaseChain(boolean isOnBehalfOfUploader, boolean allowConflicts) {
    countRebase(isOnBehalfOfUploader, /* isRebaseChain= */ true, allowConflicts);
  }

  private void countRebase(
      boolean isOnBehalfOfUploader, boolean isRebaseChain, boolean allowConflicts) {
    countRebases.increment(
        /* field1= */ isOnBehalfOfUploader,
        /* field2= */ isRebaseChain,
        /* field3= */ allowConflicts);
  }
}
