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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Information about the parent of a revision patch-set. The parent can either be a merged commit of
 * the target branch, or a patch-set of another gerrit change.
 */
@AutoValue
public abstract class ParentCommitData {

  /**
   * The name of the target branch into which the current commit should be merged. Set if the change
   * is based on a merged commit in the target branch.
   *
   * <p>This field is {@link Optional#empty()} if this information is not available for the current
   * commit, or if the parent commit belongs to a patch-set of another Gerrit change.
   */
  public abstract Optional<String> branchName();

  /**
   * The commit SHA-1 of the parent commit, or {@link Optional#empty} if there is no parent (i.e.
   * current commit is a root commit).
   */
  public abstract Optional<ObjectId> commitId();

  /** Whether the parent commit is merged in the target branch {@link #branchName()}. */
  public abstract Boolean isMergedInTargetBranch();

  /**
   * Change key of the parent commit. Only set if the parent commit is a patch-set of another gerrit
   * change.
   */
  public abstract Optional<Change.Key> changeKey();

  /**
   * Change number of the parent commit. Only set if the parent commit is a patch-set of another
   * gerrit change.
   */
  public abstract Optional<Integer> changeNumber();

  /**
   * patch-set number of the parent commit. Only set if the parent commit is a patch-set of another
   * gerrit change.
   */
  public abstract Optional<Integer> patchSetNumber();

  /**
   * Change status of the parent commit. Only set if the parent commit is a patch-set of another
   * gerrit change.
   */
  public abstract Optional<Change.Status> changeStatus();

  public static Builder builder() {
    return new AutoValue_ParentCommitData.Builder().isMergedInTargetBranch(false);
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder branchName(Optional<String> branchName);

    public abstract Builder commitId(Optional<ObjectId> commitId);

    public abstract Builder isMergedInTargetBranch(Boolean isMerged);

    public abstract Builder changeKey(Optional<Change.Key> changeKey);

    public abstract Builder changeNumber(Optional<Integer> changeNumber);

    public abstract Builder patchSetNumber(Optional<Integer> patchSetNumber);

    public abstract Builder changeStatus(Optional<Change.Status> changeStatus);

    public abstract ParentCommitData autoBuild();
  }
}
