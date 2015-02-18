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

import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Class describing a merge tip during merge operation.
 * <p>
 * The current tip of a {@link MergeTip} may be null if the merge operation is
 * against an unborn branch, and has not yet been attempted. This is distinct
 * from a null {@link MergeTip} instance, which may be used to indicate that a
 * merge failed or another error state.
 */
public class MergeTip {
  private CodeReviewCommit branchTip;
  private Map<String,String> mergeResults;

  /**
   * @param initial Tip before the merge operation; may be null, indicating an
   *     unborn branch.
   * @param toMerge List of CodeReview commits to be merged in merge operation;
   *     may not be null or empty.
   */
  public MergeTip(@Nullable CodeReviewCommit initial,
      Collection<CodeReviewCommit> toMerge) {
    checkArgument(toMerge != null && !toMerge.isEmpty(),
        "toMerge may not be null or empty: %s", toMerge);
    this.mergeResults = Maps.newHashMap();
    this.branchTip = initial;
    // Assume fast-forward merge until opposite is proven.
    for (CodeReviewCommit commit : toMerge) {
      mergeResults.put(commit.getName(), commit.getName());
    }
  }

  /**
   * Moves this MergeTip to newTip and appends mergeResult.
   *
   * @param newTip The new tip; may not be null.
   * @param mergedFrom The result of the merge of newTip.
   */
  public void moveTipTo(CodeReviewCommit newTip, String mergedFrom) {
    checkArgument(newTip != null);
    branchTip = newTip;
    mergeResults.put(mergedFrom, newTip.getName());
  }

  /**
   * The merge results of all the merges of this merge operation.
   *
   * @return The merge results of the merge operation as a map of SHA-1 to be
   *     merged to SHA-1 of the merge result.
   */
  public Map<String, String> getMergeResults() {
    return mergeResults;
  }

  /**
   * @return The current tip of the current merge operation; may be null,
   *     indicating an unborn branch.
   */
  @Nullable
  public CodeReviewCommit getCurrentTip() {
    return branchTip;
  }
}
