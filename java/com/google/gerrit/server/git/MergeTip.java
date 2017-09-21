// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.common.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Class describing a merge tip during merge operation.
 *
 * <p>The current tip of a {@link MergeTip} may be null if the merge operation is against an unborn
 * branch, and has not yet been attempted. This is distinct from a null {@link MergeTip} instance,
 * which may be used to indicate that a merge failed or another error state.
 */
public class MergeTip {
  private CodeReviewCommit initialTip;
  private CodeReviewCommit branchTip;
  private Map<ObjectId, ObjectId> mergeResults;

  /**
   * @param initialTip tip before the merge operation; may be null, indicating an unborn branch.
   * @param toMerge list of commits to be merged in merge operation; may not be null or empty.
   */
  public MergeTip(@Nullable CodeReviewCommit initialTip, Collection<CodeReviewCommit> toMerge) {
    checkNotNull(toMerge, "toMerge may not be null");
    checkArgument(!toMerge.isEmpty(), "toMerge may not be empty");
    this.initialTip = initialTip;
    this.branchTip = initialTip;
    this.mergeResults = new HashMap<>();
    // Assume fast-forward merge until opposite is proven.
    for (CodeReviewCommit commit : toMerge) {
      mergeResults.put(commit.copy(), commit.copy());
    }
  }

  /**
   * @return the initial tip of the branch before the merge operation started; may be null,
   *     indicating a previously unborn branch.
   */
  public CodeReviewCommit getInitialTip() {
    return initialTip;
  }

  /**
   * Moves this MergeTip to newTip and appends mergeResult.
   *
   * @param newTip The new tip; may not be null.
   * @param mergedFrom The result of the merge of {@code newTip}.
   */
  public void moveTipTo(CodeReviewCommit newTip, ObjectId mergedFrom) {
    checkArgument(newTip != null);
    branchTip = newTip;
    mergeResults.put(mergedFrom, newTip.copy());
  }

  /**
   * The merge results of all the merges of this merge operation.
   *
   * @return The merge results of the merge operation as a map of SHA-1 to be merged to SHA-1 of the
   *     merge result.
   */
  public Map<ObjectId, ObjectId> getMergeResults() {
    return mergeResults;
  }

  /**
   * @return The current tip of the current merge operation; may be null, indicating an unborn
   *     branch.
   */
  @Nullable
  public CodeReviewCommit getCurrentTip() {
    return branchTip;
  }
}
