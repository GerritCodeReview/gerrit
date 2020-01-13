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

package com.google.gerrit.server.change;

import org.eclipse.jgit.lib.Config;

/** State that is used to decide if {@code mergeable} should be included in the REST API or the change index. */
public enum MergeabilityComputationBehavior {
  NEVER_COMPUTE(false, false),
  COMPUTE_WHEN_INDEXING(true, false),
  ALWAYS_COMPUTE(true, true);

  private final boolean includeInIndex;
  private final boolean includeInApi;

  MergeabilityComputationBehavior(boolean includeInIndex, boolean includeInApi) {
    this.includeInIndex = includeInIndex;
    this.includeInApi = includeInApi;
  }

  /** Returns a {@link MergeabilityComputationBehavior} constructed from a Gerrit server config. */
  public static MergeabilityComputationBehavior fromConfig(Config cfg) {
    return cfg.getEnum(
        "change",
        null,
        "mergeabilityComputationBehavior",
        MergeabilityComputationBehavior.ALWAYS_COMPUTE);
  }

  /** Whether {@code mergeable} should be included in the change API.  */
  public boolean includeInApi() {
    return includeInApi;
  }

  /** Whether {@code mergeable} should be included in the change index.  */
  public boolean includeInIndex() {
    return includeInIndex;
  }
}
