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

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Information about the parent of a revision patch-set. The parent can either be a merged commit of
 * the target branch, or a patch-set of another gerrit change.
 *
 * @param branchName The name of the target branch into which the current commit should be merged.
 *     Set if the change is based on a merged commit in the target branch.
 *     <p>This field is {@link Optional#empty()} if this information is not available for the
 *     current commit, or if the parent commit belongs to a patch-set of another Gerrit change.
 * @param commitId The commit SHA-1 of the parent commit, or {@link Optional#empty} if there is no
 *     parent (i.e. current commit is a root commit).
 * @param isMergedInTargetBranch Whether the parent commit is merged in the target branch {@link
 *     #branchName()}.
 * @param changeKey Change key of the parent commit. Only set if the parent commit is a patch-set of
 *     another gerrit change.
 * @param changeNumber Change number of the parent commit. Only set if the parent commit is a
 *     patch-set of another gerrit change.
 * @param patchSetNumber patch-set number of the parent commit. Only set if the parent commit is a
 *     patch-set of another gerrit change.
 * @param changeStatus Change status of the parent commit. Only set if the parent commit is a
 *     patch-set of another gerrit change.
 */
public record ParentCommitData(
    Optional<String> branchName,
    Optional<ObjectId> commitId,
    boolean isMergedInTargetBranch,
    Optional<Change.Key> changeKey,
    Optional<Integer> changeNumber,
    Optional<Integer> patchSetNumber,
    Optional<Change.Status> changeStatus) {
  public ParentCommitData {
    requireNonNull(branchName, "branchName");
    requireNonNull(commitId, "commitId");
    requireNonNull(changeKey, "changeKey");
    requireNonNull(changeNumber, "changeNumber");
    requireNonNull(patchSetNumber, "patchSetNumber");
    requireNonNull(changeStatus, "changeStatus");
  }

  public static Builder builder() {
    return new AutoBuilder_ParentCommitData_Builder().isMergedInTargetBranch(false);
  }

  public Builder toBuilder() {
    return new AutoBuilder_ParentCommitData_Builder(this);
  }

  @AutoBuilder
  public abstract static class Builder {
    public abstract Builder branchName(Optional<String> branchName);

    public abstract Builder commitId(Optional<ObjectId> commitId);

    public abstract Builder isMergedInTargetBranch(boolean isMerged);

    public abstract Builder changeKey(Optional<Change.Key> changeKey);

    public abstract Builder changeNumber(Optional<Integer> changeNumber);

    public abstract Builder patchSetNumber(Optional<Integer> patchSetNumber);

    public abstract Builder changeStatus(Optional<Change.Status> changeStatus);

    public abstract ParentCommitData autoBuild();
  }
}
