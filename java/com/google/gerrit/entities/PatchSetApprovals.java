// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;

/** All approvals of a change by patch set. */
@AutoValue
public abstract class PatchSetApprovals {
  /**
   * Returns all approvals by patch set, including copied approvals
   *
   * <p>Approvals that have been copied from a previous patch set are returned as part of the
   * result. These approvals can be identified by looking at {@link PatchSetApproval#copied()}.
   */
  public abstract ImmutableListMultimap<PatchSet.Id, PatchSetApproval> all();

  /**
   * Returns non-copied approvals by patch set.
   *
   * <p>Approvals that have been copied from a previous patch set are filtered out.
   */
  @Memoized
  public ImmutableListMultimap<PatchSet.Id, PatchSetApproval> onlyNonCopied() {
    return ImmutableListMultimap.copyOf(
        Multimaps.filterEntries(all(), entry -> !entry.getValue().copied()));
  }

  /**
   * Returns copied approvals by patch set.
   *
   * <p>Approvals that have not been copied from a previous patch set are filtered out.
   */
  @Memoized
  public ImmutableListMultimap<PatchSet.Id, PatchSetApproval> onlyCopied() {
    return ImmutableListMultimap.copyOf(
        Multimaps.filterEntries(all(), entry -> entry.getValue().copied()));
  }

  public static PatchSetApprovals create(
      ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvalsByPatchSet) {
    return new AutoValue_PatchSetApprovals(approvalsByPatchSet);
  }
}
