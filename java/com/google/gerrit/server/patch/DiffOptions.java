// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class DiffOptions {
  public static final DiffOptions DEFAULTS =
      DiffOptions.builder()
          .skipFilesWithAllEditsDueToRebase(true)
          .skipRebaseFiltering(false)
          .skipDiffStat(false)
          .build();

  public abstract boolean skipFilesWithAllEditsDueToRebase();

  /**
   * Whether to skip the rebase-filtering algorithm in ModifiedFilesLoader.
   *
   * <p>If true, the full list of files changed between the two commits will be returned, even if
   * they are not parent-child or do not share a common parent (e.g. general repository-level
   * diffs).
   */
  public abstract boolean skipRebaseFiltering();

  /**
   * Whether to skip computing full per-file text diffs and diffstats (insertions, deletions, size,
   * sizeDelta) and instead populate lightweight {@link
   * com.google.gerrit.server.patch.filediff.FileDiffOutput} entries containing only file paths,
   * change types, file modes, and blob SHAs.
   */
  public abstract boolean skipDiffStat();

  public abstract Builder toBuilder();

  public static DiffOptions.Builder builder() {
    return new AutoValue_DiffOptions.Builder().skipRebaseFiltering(false).skipDiffStat(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder skipFilesWithAllEditsDueToRebase(boolean value);

    public abstract Builder skipRebaseFiltering(boolean value);

    public abstract Builder skipDiffStat(boolean value);

    public abstract DiffOptions build();
  }
}
