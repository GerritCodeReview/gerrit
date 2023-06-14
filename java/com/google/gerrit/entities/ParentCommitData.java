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

/**
 * Information about the parent of a revision patch-set. The parent can either be a merged commit of
 * the target branch, or a patch-set of another gerrit change.
 */
@AutoValue
public abstract class ParentCommitData {
  @AutoValue
  public abstract static class TargetBranch {
    public abstract String branchName();

    public abstract String objectId();

    public static TargetBranch create(String branchName, String objectId) {
      return new AutoValue_ParentCommitData_TargetBranch(branchName, objectId);
    }
  }

  @AutoValue
  public abstract static class ChangeRevision {
    public abstract String changeId();

    public abstract int changeNumber();

    public abstract int patchSetNumber();

    public abstract String status();

    public static ChangeRevision create(
        int changeNumber, String changeId, int patchSetNumber, String status) {
      return new AutoValue_ParentCommitData_ChangeRevision(
          changeId, changeNumber, patchSetNumber, status);
    }
  }

  /**
   * Set if the parent is a commit merged in the target branch. Includes the name of the target
   * branch and the commit SHA-1 of the parent commit.
   */
  public abstract Optional<TargetBranch> targetBranch();

  /** Set if the parent commit is a patch-set revision of another Gerrit change. */
  public abstract Optional<ChangeRevision> changeRevision();

  public static ParentCommitData create(
      Optional<TargetBranch> targetBranch, Optional<ChangeRevision> changeRevision) {
    return new AutoValue_ParentCommitData(targetBranch, changeRevision);
  }
}
