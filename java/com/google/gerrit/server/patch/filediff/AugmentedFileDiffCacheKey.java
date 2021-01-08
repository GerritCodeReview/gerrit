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
import org.eclipse.jgit.lib.ObjectId;

/**
 * A wrapper entity to the {@link FileDiffCacheKey} that also includes the old parent commit ID, the
 * new parent commit ID and if we should ignore computing the rebase edits for that key.
 */
@AutoValue
abstract class AugmentedFileDiffCacheKey {
  abstract FileDiffCacheKey key();

  abstract boolean ignoreRebase();

  abstract Optional<ObjectId> oldParentId();

  abstract Optional<ObjectId> newParentId();

  static Builder builder() {
    return new AutoValue_AugmentedFileDiffCacheKey.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder oldParentId(Optional<ObjectId> value);

    public abstract Builder newParentId(Optional<ObjectId> value);

    public abstract Builder ignoreRebase(boolean value);

    public abstract Builder key(FileDiffCacheKey value);

    public abstract AugmentedFileDiffCacheKey build();
  }
}
