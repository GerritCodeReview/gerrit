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

import com.google.auto.value.AutoValue;
import java.util.Optional;

/**
 * An entity containing the 4 git diffs for a {@link FileDiffCacheKey}: 1) The old vs. new commit 2)
 * the old commit vs. the old parent 3) the new commit vs. the new parent 4) the old parent vs. the
 * new parent
 */
@AutoValue
abstract class AllFileGitDiffs {
  abstract WrappedKey wrappedKey();

  abstract GitDiffEntity mainDiff();

  abstract Optional<GitDiffEntity> oldVsParDiff();

  abstract Optional<GitDiffEntity> newVsParDiff();

  abstract Optional<GitDiffEntity> parVsParDiff();

  static AllFileGitDiffs.Builder builder() {
    return new AutoValue_AllFileGitDiffs.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder wrappedKey(WrappedKey value);

    public abstract Builder mainDiff(GitDiffEntity value);

    public abstract Builder oldVsParDiff(Optional<GitDiffEntity> value);

    public abstract Builder newVsParDiff(Optional<GitDiffEntity> value);

    public abstract Builder parVsParDiff(Optional<GitDiffEntity> value);

    public abstract AllFileGitDiffs build();
  }
}
