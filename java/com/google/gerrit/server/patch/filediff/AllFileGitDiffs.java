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

package com.google.gerrit.server.patch.filediff;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import java.util.Optional;

/**
 * An entity containing the four git diffs for a {@link FileDiffCacheKey}:
 *
 * <ol>
 *   <li>The old vs. new commit
 *   <li>The old commit vs. the old parent
 *   <li>The new commit vs. the new parent
 *   <li>The old parent vs. the new parent
 * </ol>
 */
record AllFileGitDiffs(
    AugmentedFileDiffCacheKey augmentedKey,
    GitDiffEntity mainDiff,
    Optional<GitDiffEntity> oldVsParentDiff,
    Optional<GitDiffEntity> newVsParentDiff,
    Optional<GitDiffEntity> parentVsParentDiff) {
  AllFileGitDiffs {
    requireNonNull(augmentedKey, "augmentedKey");
    requireNonNull(mainDiff, "mainDiff");
    requireNonNull(oldVsParentDiff, "oldVsParentDiff");
    requireNonNull(newVsParentDiff, "newVsParentDiff");
    requireNonNull(parentVsParentDiff, "parentVsParentDiff");
  }

  static AllFileGitDiffs.Builder builder() {
    return new AutoBuilder_AllFileGitDiffs_Builder();
  }

  @AutoBuilder
  public abstract static class Builder {

    public abstract Builder augmentedKey(AugmentedFileDiffCacheKey value);

    public abstract Builder mainDiff(GitDiffEntity value);

    public abstract Builder oldVsParentDiff(Optional<GitDiffEntity> value);

    public abstract Builder newVsParentDiff(Optional<GitDiffEntity> value);

    public abstract Builder parentVsParentDiff(Optional<GitDiffEntity> value);

    public abstract AllFileGitDiffs build();
  }
}
